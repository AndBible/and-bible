/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */
package net.bible.service.llm.tools.read

import net.bible.android.AppDialogControllerResetRule
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.database.IdType
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.base.CurrentActivityHolder
import net.bible.service.common.CommonUtils
import net.bible.service.llm.agent.AgentContext
import net.bible.service.llm.agent.AgentSessionChange
import net.bible.service.llm.agent.AgentSessionManager
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.sharedcore.ui.dialog.AppDialogRequest
import net.bible.sharedcore.ui.dialog.AppDialogResult
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Task 25: [GetCommentariesTool]'s `showFilterDialog` is now an
 * [net.bible.sharedcore.ui.dialog.AppDialogRequest.MultiChoice] on the app-wide
 * [AppDialogController], with a `footerFor` live token-total footer, replacing the
 * `android.app.AlertDialog` + `DialogCommentaryFilterBinding` combo and the 500 ms
 * `CurrentActivityHolder` poll that existed only to find a `Context` for it.
 *
 * Follows [net.bible.android.control.backup.BackupControlSelectDatabaseSectionsTest]'s shape: no
 * Activity is built, `dialogs.pending`/`dialogs.respond` are driven directly against this `runTest`'s
 * own `TestScope`, never a real (idled) Looper -- an open `ModalBottomSheet` idled via
 * `shadowOf(Looper.getMainLooper()).idle()` hangs forever (spec-noted trap), so this suite never does
 * that; it exercises [GetCommentariesTool.filterByResponseSizeLimit] (`internal`, exposed the same
 * way `BackupControl.selectDatabaseSections` is) directly with synthetic [GetCommentariesTool.CommentaryResult]s
 * sized to exceed a tiny test threshold, rather than reconstructing a real Sword-commentary pipeline.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class GetCommentariesToolFilterDialogTest {
    private val dialogs: AppDialogController get() = KoinJavaComponent.get(AppDialogController::class.java)
    @get:Rule val dialogReset = AppDialogControllerResetRule()
    private val context = AgentContext(promptId = IdType())

    private val originalThreshold = CommonUtils.aiSettings.commentaryMaxResponseTokens
    private val originalDeselected = CommonUtils.aiSettings.commentaryDeselected

    /**
     * [CurrentActivityHolder] (`app/src/main/java/net/bible/android/view/activity/base/CurrentActivityHolder.kt`)
     * is process-global with no public reset. Controller finding (run 3 fix wave, after 9601082af):
     * in the full `:app` unit suite (one JVM) an earlier test class that builds+`.setup()`s a real
     * Activity and never tears it down (`ActivityBase.onCreate`'s first line is
     * `CurrentActivityHolder.activate(this)`) leaves `currentActivity` non-null for every test class
     * that runs after it -- so `postsWaitingTrueThenFalseAroundTheDialogWhenNoActivityIsCurrent`/
     * `postsWaitingFalseInAFinallyEvenWhenTheDialogIsCancelled`'s "no Activity is built here, so
     * `currentActivity` is null" assumption held only by suite-order luck, not by construction. Both
     * tests need `CurrentActivityHolder.currentActivity == null` to exercise
     * `GetCommentariesTool.filterByResponseSizeLimit`'s "no current Activity" branch at all -- with a
     * stale Activity still registered, `postedWaiting` is false and neither test ever sees an event,
     * which is exactly the `expected 1/2 but was 0` failure the controller's full run hit.
     *
     * Same reflection-based save/clear/restore idiom as `DialogsShimTest.holderActivities()` /
     * `CurrentPageManagerNoActivityTest.holderActivities()`, so this class establishes and restores
     * its own precondition instead of relying on suite ordering.
     */
    @Suppress("UNCHECKED_CAST")
    private fun holderActivities(): ArrayList<ActivityBase> =
        CurrentActivityHolder::class.java.getDeclaredField("activities").apply { isAccessible = true }
            .get(CurrentActivityHolder) as ArrayList<ActivityBase>

    private lateinit var savedActivities: ArrayList<ActivityBase>

    @Before
    fun ensureNoCurrentActivity() {
        savedActivities = ArrayList(holderActivities())
        holderActivities().clear()
        assertNull("precondition: no current Activity", CurrentActivityHolder.currentActivity)
    }

    @After
    fun tearDown() {
        dialogs.cancelAll()
        CommonUtils.aiSettings.commentaryMaxResponseTokens = originalThreshold
        CommonUtils.aiSettings.commentaryDeselected = originalDeselected
        holderActivities().clear()
        holderActivities().addAll(savedActivities)
    }

    /** A commentary whose single entry is big enough that two of these together, at
     *  `commentaryMaxResponseTokens = 10`, clear the size-limit threshold and raise the dialog. */
    private fun bigResult(initials: String, name: String) = GetCommentariesTool.CommentaryResult(
        initials = initials,
        name = name,
        abbreviation = initials,
        entries = listOf(
            GetCommentariesTool.CommentaryEntry(
                verseRange = "Gen.1.1",
                linkUrl = "sword://$initials/Gen.1.1",
                text = "x".repeat(200),
            ),
        ),
    )

    @Test
    fun belowThresholdReturnsEverythingWithoutRaisingADialog() = runTest(timeout = 30.seconds) {
        CommonUtils.aiSettings.commentaryMaxResponseTokens = 0 // "no limit"
        val results = listOf(bigResult("AAA", "Commentary A"))
        val result = GetCommentariesTool.filterByResponseSizeLimit(results, context)
        assertEquals(results, result!!.results)
        assertEquals(emptyList<String>(), result.excludedCommentaries)
    }

    @Test
    fun selectingASubsetKeepsOnlyThoseCommentariesAndPersistsTheDeselection() = runTest(timeout = 30.seconds) {
        CommonUtils.aiSettings.commentaryMaxResponseTokens = 10
        CommonUtils.aiSettings.commentaryDeselected = emptySet()
        val results = listOf(bigResult("AAA", "Commentary A"), bigResult("BBB", "Commentary B"))

        val deferred = async { GetCommentariesTool.filterByResponseSizeLimit(results, context) }
        yield()

        val request = dialogs.pending.value!!.request as AppDialogRequest.MultiChoice
        assertEquals(listOf("AAA", "BBB"), request.options.map { it.value }.sorted())
        // Nothing previously deselected -- everything starts checked, exactly as the old
        // `checkedItems = BooleanArray(items.size) { items[it].initials !in previouslyDeselected }` did.
        assertEquals(listOf("AAA", "BBB"), request.selectedIds.sorted())

        dialogs.respond(dialogs.pending.value!!.id, AppDialogResult.SelectedMany(listOf("AAA")))
        val filterResult = deferred.await()!!
        assertEquals(listOf("AAA"), filterResult.results.map { it.initials })
        assertEquals(listOf("BBB"), filterResult.excludedCommentaries)
        assertEquals(setOf("BBB"), CommonUtils.aiSettings.commentaryDeselected)
    }

    /** Today's cancel value: `null`, aborting the whole tool call -- and, like the old
     *  `setOnCancelListener`/`btnCancel` paths, cancel never touches `commentaryDeselected`. */
    @Test
    fun cancelAbortsWithNullAndDoesNotPersistADeselection() = runTest(timeout = 30.seconds) {
        CommonUtils.aiSettings.commentaryMaxResponseTokens = 10
        CommonUtils.aiSettings.commentaryDeselected = emptySet()
        val results = listOf(bigResult("AAA", "Commentary A"))

        val deferred = async { GetCommentariesTool.filterByResponseSizeLimit(results, context) }
        yield()

        dialogs.respond(dialogs.pending.value!!.id, AppDialogResult.Cancel)
        assertNull(deferred.await())
        assertEquals(emptySet<String>(), CommonUtils.aiSettings.commentaryDeselected)
    }

    /** [AppDialogRequest.MultiChoice.footerFor] is the live token total -- it changes as the
     *  (hypothetical, since this test drives the controller directly rather than a rendered sheet)
     *  checked set changes, and it names the configured threshold. */
    @Test
    fun footerForReflectsTheCurrentSelectionsLiveTokenTotal() = runTest(timeout = 30.seconds) {
        CommonUtils.aiSettings.commentaryMaxResponseTokens = 10
        CommonUtils.aiSettings.commentaryDeselected = emptySet()
        val results = listOf(bigResult("AAA", "Commentary A"), bigResult("BBB", "Commentary B"))

        val deferred = async { GetCommentariesTool.filterByResponseSizeLimit(results, context) }
        yield()

        val request = dialogs.pending.value!!.request as AppDialogRequest.MultiChoice
        val footerFor = requireNotNull(request.footerFor) { "Task 25 must set footerFor" }
        val bothSelected = footerFor(listOf("AAA", "BBB"))
        val oneSelected = footerFor(listOf("AAA"))
        val noneSelected = footerFor(emptyList())
        assertNotEquals(bothSelected, oneSelected)
        assertNotEquals(oneSelected, noneSelected)
        assertTrue("footer must name the configured threshold (10)", bothSelected.contains("10"))

        dialogs.respond(dialogs.pending.value!!.id, AppDialogResult.SelectedMany(listOf("AAA", "BBB")))
        deferred.await()
    }

    /** I1 (run 3 final review): restores today's pre-port behaviour, deleted by Task 25 along with
     *  the 500 ms `CurrentActivityHolder` poll -- [net.bible.service.llm.agent.AgentForegroundService]
     *  consumes [AgentSessionChange.PermissionWaiting] to release its wakelock and show the "permission
     *  needed" notification while an agent waits with no Activity current. `AppDialogController` is
     *  the wait now, but the waiting/not-waiting posts around it must still happen, bracketing the
     *  `await` exactly like the deleted poll did (`true` before, `false` after) -- here, no
     *  Robolectric Activity is built, so [net.bible.android.view.activity.base.CurrentActivityHolder]
     *  has none current, matching the deleted code's `activity == null` branch. */
    @Test
    fun postsWaitingTrueThenFalseAroundTheDialogWhenNoActivityIsCurrent() = runTest(timeout = 30.seconds) {
        CommonUtils.aiSettings.commentaryMaxResponseTokens = 10
        CommonUtils.aiSettings.commentaryDeselected = emptySet()
        val workspaceId = IdType()
        val results = listOf(bigResult("AAA", "Commentary A"))
        val seen = mutableListOf<AgentSessionChange.PermissionWaiting>()
        val subscription = AgentSessionManager.changes.subscribe { if (it is AgentSessionChange.PermissionWaiting) seen.add(it) }
        try {
            val deferred = async {
                GetCommentariesTool.filterByResponseSizeLimit(results, AgentContext(promptId = IdType(), workspaceId = workspaceId))
            }
            yield()

            // The `waiting = true` post already fired, before the answer -- exactly where the
            // deleted poll posted it, before its wait loop.
            assertEquals(1, seen.size)
            assertEquals(workspaceId, seen[0].workspaceId)
            assertTrue("first post must be waiting = true", seen[0].waiting)

            dialogs.respond(dialogs.pending.value!!.id, AppDialogResult.SelectedMany(listOf("AAA")))
            deferred.await()
        } finally {
            subscription.cancel()
        }

        assertEquals(2, seen.size)
        assertTrue("second post must be waiting = false", !seen[1].waiting)
    }

    /** Same [AgentSessionChange.PermissionWaiting] bracket, but the user cancels: the `finally` must still post `waiting = false` -- the
     *  deleted poll had no cancellation path to lose this on, but the new `AppDialogController.await`
     *  can be cancelled (e.g. the whole agent run stops), so this pins the `finally` semantics. */
    @Test
    fun postsWaitingFalseInAFinallyEvenWhenTheDialogIsCancelled() = runTest(timeout = 30.seconds) {
        CommonUtils.aiSettings.commentaryMaxResponseTokens = 10
        CommonUtils.aiSettings.commentaryDeselected = emptySet()
        val workspaceId = IdType()
        val results = listOf(bigResult("AAA", "Commentary A"))
        val seen = mutableListOf<AgentSessionChange.PermissionWaiting>()
        val subscription = AgentSessionManager.changes.subscribe { if (it is AgentSessionChange.PermissionWaiting) seen.add(it) }
        try {
            val deferred = async {
                GetCommentariesTool.filterByResponseSizeLimit(results, AgentContext(promptId = IdType(), workspaceId = workspaceId))
            }
            yield()

            dialogs.respond(dialogs.pending.value!!.id, AppDialogResult.Cancel)
            assertNull(deferred.await())
        } finally {
            subscription.cancel()
        }

        assertEquals(2, seen.size)
        assertTrue("first post must be waiting = true", seen[0].waiting)
        assertTrue("finally must post waiting = false even on cancel", !seen[1].waiting)
    }
}

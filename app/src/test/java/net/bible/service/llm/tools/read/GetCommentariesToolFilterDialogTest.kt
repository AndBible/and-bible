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
import net.bible.service.common.CommonUtils
import net.bible.service.llm.agent.AgentContext
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.sharedcore.ui.dialog.AppDialogRequest
import net.bible.sharedcore.ui.dialog.AppDialogResult
import org.junit.After
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

    @After
    fun tearDown() {
        dialogs.cancelAll()
        CommonUtils.aiSettings.commentaryMaxResponseTokens = originalThreshold
        CommonUtils.aiSettings.commentaryDeselected = originalDeselected
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
}

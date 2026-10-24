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
package net.bible.android.view.compose

import android.os.Looper
import androidx.navigation.NavHostController
import androidx.test.core.app.ApplicationProvider
import java.time.Duration
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.database.IdType
import net.bible.android.view.activity.base.firstTime
import net.bible.android.view.activity.bookmark.ManageLabelsContract
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.sharedcore.nav.ManageLabelsResult
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedui.nav.NavResultChannel
import net.bible.test.DatabaseResetter
import net.bible.test.resetComposeUiDispatcher
import org.junit.After
import org.koin.core.context.GlobalContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import net.bible.sharedui.app.bookmark.BookmarkHost

/**
 * CR-5 fix round 1. The bookmark list and the text-display-settings arm share `manageLabelsResults`,
 * and navigation-compose disposes whichever arm is NOT the resumed destination. An arm-local consume
 * gate therefore misses an answer the bookmark list asked for when the settings arm happens to be the
 * resumed destination when it arrives: the answer stays pending, and the next time the bookmark list
 * composes its effect applies that stale payload through `applyBookmarksManageLabelsResult`, whose
 * else branch writes `autoAssignLabels` into the workspace settings -- the write CR-5 exists to stop.
 *
 * The claim therefore has to be made by whoever asked, from a composition that outlives every
 * destination. This drives the REAL host's collector the way `InstallZipReturnHostTest` drives
 * `InstallZipHost.Overlays`: a focused test of the collector itself rather than two real arms in one
 * host, because putting the settings arm on the stack needs a workspace, a window and a settings
 * bundle that have nothing to do with who consumes the channel.
 *
 * The host starts on `backup/restore`, a destination with no label-manager consumer, and the answer is
 * delivered the way a popped label manager delivers it (`deliver` with a parent entry). Starting on
 * `bookmarks` would not do: the bookmark arm is composed there, which would put a second composition
 * next to the host-level collector under test. The only thing that can consume the answer here is the
 * host-level collector.
 *
 * The last two tests start from the REAL request paths (`requestManageLabels`, `requestAssignLabels`,
 * reached through the real `BookmarksController` that `bookmarksControllerFor` builds) rather than
 * writing the session flag by hand, so deleting `session.awaitingManageLabels = true` from either
 * path fails them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class BookmarksManageLabelsCollectorTest {

    private val controllers = mutableListOf<ActivityController<NavHostComposeActivity>>()

    @After
    fun tearDown() {
        controllers.forEach { it.close() }
        controllers.clear()
        DatabaseResetter.resetDatabase()
        // WindowControl is a Koin singleton, so the workspace settings one test wrote survive into
        // the next. Reset them, or "wrote nothing" reads the previous test's write.
        (autoAssignLabels() as MutableSet<*>).clear()
    }

    private fun host(): NavHostComposeActivity {
        // The collector is a LaunchedEffect, so the destination must be COMPOSED, not only routed:
        // see resetComposeUiDispatcher for what an earlier host test leaves behind in the same JVM.
        resetComposeUiDispatcher()
        firstTime = false
        return Robolectric.buildActivity(
            NavHostComposeActivity::class.java,
            NavHostComposeActivity.intentFor(ApplicationProvider.getApplicationContext(), NavRoutes.BACKUP),
        ).also { controllers += it }.create().start().resume().visible().get()
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))

    private fun navController(activity: NavHostComposeActivity): NavHostController {
        val field = NavHostComposeActivity::class.java.getDeclaredField("navController")
        field.isAccessible = true
        return field.get(activity) as NavHostController
    }

    private fun channel(activity: NavHostComposeActivity): NavResultChannel<ManageLabelsResult> {
        val field = NavHostComposeActivity::class.java.getDeclaredField("manageLabelsResults")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return field.get(activity) as NavResultChannel<ManageLabelsResult>
    }

    /** The real `bookmarksControllerFor`, which is what opening the list does to seed the session. */
    private fun seedBookmarksSession(activity: NavHostComposeActivity) {
        val method = BookmarkHost::class.java.declaredMethods
            .single { it.name == "bookmarksControllerFor" }
        method.isAccessible = true
        method.invoke(activity.appHosts.bookmark, 0, { _: Any? -> }, { _: String -> })
    }

    private fun bookmarksSession(activity: NavHostComposeActivity): Any {
        // `by mutableStateOf`, so the value is reached through the property's getter, not a field.
        val pair = BookmarkHost::class.java.declaredMethods
            .single { it.name == "getBookmarksSession" }
            .apply { isAccessible = true }
            .invoke(activity.appHosts.bookmark) as Pair<*, *>?
        return requireNotNull(pair?.second) { "bookmarksControllerFor did not seed bookmarksSession" }
    }

    /**
     * `awaitingManageLabels` is `by mutableStateOf`. Its accessor methods are unreachable: the session
     * class is private, so the JVM refuses a reflective call on them even after `setAccessible`. The
     * generated delegate field is a `MutableState`, and writing it is exactly what the setter does.
     */
    private fun awaitingState(session: Any): androidx.compose.runtime.MutableState<Boolean> {
        val field = session.javaClass.declaredFields
            .single { it.name.startsWith("awaitingManageLabels") }
            .apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        return field.get(session) as androidx.compose.runtime.MutableState<Boolean>
    }

    private fun setAwaiting(session: Any, awaiting: Boolean) {
        awaitingState(session).value = awaiting
    }

    private fun isAwaiting(session: Any): Boolean = awaitingState(session).value

    /**
     * What a popped label manager does: publish into the channel and pop. Navigating to the label
     * manager first gives `deliver` a parent entry, so it takes the in-graph branch instead of
     * exiting the host. `publishForTest` is `internal` to `:sharedUi` and unreachable from `:app`.
     */
    private fun deliverAsTheLabelManagerWould(activity: NavHostComposeActivity, result: ManageLabelsResult) {
        val nav = navController(activity)
        nav.navigate(NavRoutes.manageLabels("""{"mode":"WORKSPACE"}"""))
        idle()
        channel(activity).deliver(nav, result)
        idle()
    }

    /**
     * The workspace settings `applyBookmarksManageLabelsResult` writes. The activity is final and the
     * method is private, and `windowControl` is a `by inject()` delegate whose backing field a
     * plain-name lookup cannot see, so the write is observed through the Koin singleton the activity
     * itself injects -- the same object, since `singleOf(::WindowControl)` is the only provider.
     */
    private fun autoAssignLabels(): Set<IdType> =
        GlobalContext.get().get<net.bible.android.control.page.window.WindowControl>()
            .windowRepository.workspaceSettings.autoAssignLabels

    /** The real controller the list's own screen would drive, with a recording navigate lambda. */
    private fun realController(
        activity: NavHostComposeActivity,
        navigated: MutableList<String>,
    ): net.bible.sharedcore.bookmark.BookmarksController {
        val method = BookmarkHost::class.java.declaredMethods
            .single { it.name == "bookmarksControllerFor" }
        method.isAccessible = true
        return method.invoke(activity.appHosts.bookmark, 0, { _: Any? -> }, { payload: String -> navigated += payload })
            as net.bible.sharedcore.bookmark.BookmarksController
    }

    /** Idles the main looper until [condition] holds; `requestAssignLabels` hops through Dispatchers.IO. */
    private fun idleUntil(what: String, condition: () -> Boolean) {
        val deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos()
        while (!condition()) {
            check(System.nanoTime() < deadline) { "timed out waiting for $what" }
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50))
            Thread.sleep(20)
        }
    }

    /**
     * WORKSPACE mode through the real `requestManageLabels`: the controller's own `manageLabels()`
     * must arm the gate, so the collector applies the answer without any test-side flag.
     */
    @Test
    fun theRealManageLabelsRequestArmsTheCollector() {
        val activity = host()
        idle()
        val navigated = mutableListOf<String>()
        val controller = realController(activity, navigated)

        controller.manageLabels()
        idleUntil("requestManageLabels to navigate") { navigated.isNotEmpty() }
        assertTrue("requestManageLabels must arm the gate", isAwaiting(bookmarksSession(activity)))

        val marker = IdType()
        val payload = ManageLabelsContract.ManageLabelsData(
            mode = ManageLabelsContract.Mode.WORKSPACE,
            autoAssignLabels = mutableSetOf(marker),
        ).toJSON()
        deliverAsTheLabelManagerWould(activity, ManageLabelsResult(payload))

        assertEquals(null, channel(activity).pending.value)
        assertEquals(setOf(marker), autoAssignLabels())
    }

    /**
     * ASSIGN mode through the real `requestAssignLabels` (Dispatchers.IO, then Main): the labels the
     * label manager returns must end up on the selected bookmark.
     */
    @Test
    fun theRealAssignLabelsRequestArmsTheCollector() {
        val activity = host()
        idle()
        val control: net.bible.android.control.bookmark.BookmarkControl = GlobalContext.get().get()
        val bookmark = kotlinx.coroutines.runBlocking { control.addOrUpdateBibleBookmark(
            net.bible.android.database.bookmarks.BookmarkEntities.BibleBookmarkWithNotes(
                org.crosswire.jsword.passage.VerseRangeFactory.fromString(
                    org.crosswire.jsword.versification.system.Versifications.instance().getVersification("KJV"),
                    "Psalms 119:1",
                ),
                null, true, null,
            ),
            null,
        ) }
        val label = kotlinx.coroutines.runBlocking { control.insertOrUpdateLabel(
            net.bible.android.database.bookmarks.BookmarkEntities.Label(new = true).apply { name = "X" },
        ) }
        // `bookmarksByIds` resolves against what the list's `loadRows` loaded, as in production.
        val service: net.bible.android.view.activity.bookmark.BookmarksServiceImpl = GlobalContext.get().get()
        kotlinx.coroutines.runBlocking {
            service.loadRows(
                filterIndex = 0,
                sort = net.bible.sharedcore.bookmark.BookmarkSortMode.BIBLE_ORDER,
                search = null,
                showNotes = false,
            )
        }
        val navigated = mutableListOf<String>()
        val controller = realController(activity, navigated)

        controller.enterSelection(bookmark.id.toString())
        controller.assignSelected()
        idleUntil("requestAssignLabels to navigate") { navigated.isNotEmpty() }
        assertTrue("requestAssignLabels must arm the gate", isAwaiting(bookmarksSession(activity)))

        val payload = ManageLabelsContract.ManageLabelsData(
            mode = ManageLabelsContract.Mode.ASSIGN,
            selectedLabels = mutableSetOf(label.id),
        ).toJSON()
        deliverAsTheLabelManagerWould(activity, ManageLabelsResult(payload))

        assertEquals(null, channel(activity).pending.value)
        // applyBookmarksManageLabelsResult writes on Dispatchers.IO.
        idleUntil("the assigned label to reach the bookmark") {
            kotlinx.coroutines.runBlocking { control.labelsForBookmark(bookmark) }.map { it.id } == listOf(label.id)
        }
    }

    /**
     * The answer the list asked for must be claimed and applied exactly once even though no bookmark
     * arm is composed to see it.
     */
    @Test
    fun anAnswerTheBookmarkListAskedForIsAppliedExactlyOnceWhileNoBookmarkArmIsComposed() {
        val activity = host()
        idle()
        seedBookmarksSession(activity)
        val session = bookmarksSession(activity)
        setAwaiting(session, true)
        assertTrue("fixture: nothing is auto-assigned yet", autoAssignLabels().isEmpty())

        val marker = IdType()
        val payload = ManageLabelsContract.ManageLabelsData(
            mode = ManageLabelsContract.Mode.WORKSPACE,
            autoAssignLabels = mutableSetOf(marker),
        ).toJSON()
        deliverAsTheLabelManagerWould(activity, ManageLabelsResult(payload))

        assertEquals("the claimed answer must leave the channel", null, channel(activity).pending.value)
        assertTrue("applying the answer clears the request", !isAwaiting(session))
        assertEquals("the answer is applied exactly once", setOf(marker), autoAssignLabels())

        // A second tick is a recomposition's worth of opportunity to apply again.
        idle()
        assertEquals("a recomposition must not re-apply a consumed answer", setOf(marker), autoAssignLabels())
        assertEquals(null, channel(activity).pending.value)
    }

    /**
     * A WORKSPACE-mode label manager has no business with the active window. It read it anyway (for a
     * STUDYPAD-only highlight), so composing it while the window repository could not answer
     * `activeWindow` (here: mid-load, the state behind this class's order-dependent full-suite failures)
     * threw in composition: the collector never ran and the answer stayed pending.
     */
    @Test
    fun aWorkspaceAnswerIsAppliedWhileTheWindowRepositoryCannotNameAnActiveWindow() {
        val activity = host()
        idle()
        val windowControl: net.bible.android.control.page.window.WindowControl = GlobalContext.get().get()
        val previous = windowControl.windowRepository
        val midLoad = net.bible.android.control.page.window.WindowRepository(
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main),
        )
        net.bible.android.control.page.window.WindowRepository::class.java.getDeclaredField("loadingFromDb")
            .apply { isAccessible = true }.setBoolean(midLoad, true)
        windowControl.windowRepository = midLoad
        try {
            seedBookmarksSession(activity)
            setAwaiting(bookmarksSession(activity), true)
            val marker = IdType()
            val payload = ManageLabelsContract.ManageLabelsData(
                mode = ManageLabelsContract.Mode.WORKSPACE,
                autoAssignLabels = mutableSetOf(marker),
            ).toJSON()
            deliverAsTheLabelManagerWould(activity, ManageLabelsResult(payload))

            assertEquals("the claimed answer must leave the channel", null, channel(activity).pending.value)
            assertEquals(setOf(marker), midLoad.workspaceSettings.autoAssignLabels)
        } finally {
            windowControl.windowRepository = previous
        }
    }

    /**
     * The other half of the ruling: an answer nobody asked for stays in the channel, for whoever did,
     * and writes nothing. This is what keeps
     * `aLabelAnswerTheBookmarkListDidNotAskForIsLeftInTheChannel` true once the graph arm no longer
     * consumes.
     */
    @Test
    fun anAnswerTheBookmarkListDidNotAskForIsLeftPending() {
        val activity = host()
        idle()
        seedBookmarksSession(activity)
        setAwaiting(bookmarksSession(activity), false)

        val marker = IdType()
        val payload = ManageLabelsContract.ManageLabelsData(
            mode = ManageLabelsContract.Mode.WORKSPACE,
            autoAssignLabels = mutableSetOf(marker),
        ).toJSON()
        deliverAsTheLabelManagerWould(activity, ManageLabelsResult(payload))

        assertEquals("an unrequested answer stays for whoever asked", payload, channel(activity).pending.value?.data)
        assertTrue("an unrequested answer writes nothing", autoAssignLabels().isEmpty())
    }
}

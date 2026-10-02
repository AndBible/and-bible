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

import android.app.Activity
import android.content.Intent
import android.os.Looper
import androidx.activity.result.ActivityResult
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavHostController
import androidx.test.core.app.ApplicationProvider
import java.time.Duration
import kotlinx.coroutines.launch
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.database.WorkspaceEntities
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.base.ErrorActivity
import net.bible.android.view.activity.base.firstTime
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.android.view.activity.nav.ReadingResultKind
import net.bible.android.view.activity.nav.ReadingResultRequests
import net.bible.android.view.activity.page.WORKSPACE_CHANGED
import net.bible.service.common.CommonUtils
import net.bible.service.db.DatabaseContainer
import net.bible.service.history.HistoryManager
import net.bible.sharedcore.nav.DocumentResult
import net.bible.sharedcore.nav.KeyChooserResult
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedcore.nav.PassageResult
import net.bible.sharedcore.nav.WorkspaceResult
import net.bible.sharedcore.reading.ReadingHostPresence
import net.bible.sharedcore.reading.ReadingViewVisibility
import net.bible.sharedui.nav.NavResultChannel
import net.bible.test.DatabaseResetter
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.versification.BibleBook
import org.crosswire.jsword.versification.system.Versifications
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/**
 * Slice 8 B1 (spec §3.5, plan Correction 1): the reading destination now collects the four channels
 * slice 7's chooser/workspace destinations publish into. Before B1 nothing did -- each answer was left
 * in its channel's pending slot and the user's choice vanished.
 *
 * Driven on the REAL host with a composed reading destination. For the STD/`WORKSPACE_CHANGED` tests the
 * launch itself now puts the chooser on top of `reading` (fix batch 6 F118: a synchronous self-launch
 * navigates the live graph), so `deliver(...)` has its parent entry. The remaining tests (async codes,
 * abandonment) push a stand-in (`AI_TOOL_INFO`, [standInAbove]): what is under test is that the reading
 * destination collects and applies, not what the chooser draws (`ChooserInGraphResultTest` owns the arms).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class ReadingChooserInGraphResultTest {

    private val controllers = mutableListOf<ActivityController<NavHostComposeActivity>>()

    @After
    fun tearDown() {
        controllers.forEach { it.close() }
        controllers.clear()
        ReadingHostPresence.setForeground(null)
        ReadingViewVisibility.setVisible(false)
        DatabaseResetter.resetDatabase()
    }

    private fun composedReadingHost(): NavHostComposeActivity {
        firstTime = false
        // Create the special labels up front, on this thread. `BibleView.loadDocument` creates them
        // lazily with a check-then-insert (`BookmarkControl.getOrCreateSpecialLabel`) from
        // `Window.loadText`'s Dispatchers.IO coroutine, and a test that changes the document twice
        // (`aDocumentChosenInGraphIsAppliedToTheActiveWindow`) runs two such loads at once against a
        // freshly reset database: both see no label, one insert loses with `UNIQUE constraint failed:
        // Label.id`. Which one wins is thread timing, so it failed only in some full-suite orders.
        // The race itself is production code's (reported, not changed here); this test is about the
        // chooser answer, not about first-run label creation.
        GlobalContext.get().get<BookmarkControl>().apply { labelUnlabelled; speakLabel; paragraphBreakLabel }
        return Robolectric.buildActivity(
            NavHostComposeActivity::class.java,
            NavHostComposeActivity.intentFor(ApplicationProvider.getApplicationContext(), NavRoutes.READING),
        ).also { controllers += it }.create().start().resume().visible().get()
    }

    /**
     * Not used for content: its presence installs the Compose test environment (a recomposer and
     * frame clock driven by the test) for every window created while it is active, which is what
     * makes the host's own `setContent` recompose in a Robolectric JVM that has already run another
     * composed test. Without it each test here passed alone and all but the first failed as a
     * class: the reading destination never recomposed after a pop, so its collectors never ran.
     * `ReadingDestinationInGraphTest` holds a compose rule beside its `buildActivity` hosts for the
     * same reason.
     */
    @get:Rule val compose = createEmptyComposeRule()

    private fun idle() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        compose.waitForIdle()
    }

    private fun nav(activity: NavHostComposeActivity): NavHostController =
        NavHostComposeActivity::class.java.getDeclaredField("navController")
            .apply { isAccessible = true }.get(activity) as NavHostController

    @Suppress("UNCHECKED_CAST")
    private fun <T> channel(activity: NavHostComposeActivity, field: String): NavResultChannel<T> =
        NavHostComposeActivity::class.java.getDeclaredField(field)
            .apply { isAccessible = true }.get(activity) as NavResultChannel<T>

    private fun requests(activity: NavHostComposeActivity): ReadingResultRequests =
        NavHostComposeActivity::class.java.getDeclaredField("readingResultRequests")
            .apply { isAccessible = true }.get(activity) as ReadingResultRequests

    /** Push the stand-in above `reading`, so a `deliver` publishes and pops back to it. */
    private fun standInAbove(activity: NavHostComposeActivity): NavHostController {
        val nav = nav(activity)
        nav.navigate(NavRoutes.AI_TOOL_INFO)
        idle()
        return nav
    }

    @Test
    fun aDocumentChosenInGraphIsAppliedToTheActiveWindow() {
        val activity = composedReadingHost()
        val kjv = Books.installed().getBook("KJV")
        CommonUtils.windowControl.activeWindowPageManager.setCurrentDocument(kjv)
        idle()
        activity.startActivityForResult(
            NavHostComposeActivity.intentFor(activity, NavRoutes.chooseDocument()), ActivityBase.STD_REQUEST_CODE,
        )
        idle(); val nav = nav(activity)
        assertEquals(NavRoutes.chooseDocument().substringBefore('?'), activity.currentRouteForTest()?.substringBefore('?'))

        channel<DocumentResult>(activity, "documentResults").deliver(nav, DocumentResult("ESV2011"))
        idle()

        assertEquals(
            "the STD answer must reach applyChosenDocument through the in-graph STD arm",
            "ESV2011",
            CommonUtils.windowControl.activeWindowPageManager.currentPage.currentDocument?.initials,
        )
    }

    @Test
    fun aPassageChosenInGraphMovesTheActiveWindow() {
        val activity = composedReadingHost()
        activity.startActivityForResult(
            NavHostComposeActivity.intentFor(activity, NavRoutes.gridChoosePassage(isScripture = true)),
            ActivityBase.STD_REQUEST_CODE,
        )
        idle(); val nav = nav(activity)
        assertEquals(NavRoutes.gridChoosePassage(isScripture = true).substringBefore('?'), activity.currentRouteForTest()?.substringBefore('?'))

        channel<PassageResult>(activity, "passageResults").deliver(nav, PassageResult("Ps.23.1"))
        idle()

        assertEquals(
            "Ps.23.1",
            (CommonUtils.windowControl.activeWindowPageManager.currentPage.singleKey as Verse).osisID,
        )
    }

    @Test
    fun aWorkspacePickedInGraphIsSwitchedToWithoutWaitingForOnResume() {
        val activity = composedReadingHost()
        val target = WorkspaceEntities.Workspace(name = "slice 8 target").also {
            DatabaseContainer.instance.workspaceDb.workspaceDao().insertWorkspace(it)
        }
        activity.startActivityForResult(
            NavHostComposeActivity.intentFor(activity, NavRoutes.WORKSPACE_SELECTOR),
            WORKSPACE_CHANGED,
        )
        idle(); val nav = nav(activity)
        assertEquals(NavRoutes.WORKSPACE_SELECTOR, activity.currentRouteForTest())

        channel<WorkspaceResult>(activity, "workspaceResults")
            .deliver(nav, WorkspaceResult(workspaceId = target.id.toString(), changed = false))
        idle()

        assertEquals(
            "an in-graph pop produces no onResume, so the WORKSPACE_CHANGED answer must be applied at once",
            target.id,
            activity.hostWindowRepository.id,
        )
    }

    @Test
    fun anAwaitedKeyChooserSelfLaunchNavigatesInsteadOfReachingThePlatform() {
        val activity = composedReadingHost()
        // `lifecycleScope` is `Main.immediate`: the launch runs up to awaitIntent's suspension, i.e.
        // through startActivityForResult, before it returns. Asserted BEFORE any idle: composing the
        // real general-book chooser needs a general book in the active window (classic's `doc!!`
        // fallback in `generalBookKeyResult`), which the test modules do not provide.
        var result: ActivityResult? = null
        val job = activity.lifecycleScope.launch {
            result = activity.awaitIntent(NavHostComposeActivity.intentFor(activity, NavRoutes.CHOOSE_GENERAL_BOOK_KEY))
        }
        assertEquals(
            "F53's interception must now cover the key chooser -- it has a collector",
            null,
            shadowOf(activity).nextStartedActivityForResult,
        )
        assertEquals(NavRoutes.CHOOSE_GENERAL_BOOK_KEY, nav(activity).currentDestination?.route)
        // Back out before the chooser ever composes; the abandonment answer resumes the await, so
        // no coroutine is left parked for tearDown.
        nav(activity).popBackStack()
        idle()
        assertTrue("the awaiting coroutine must have been resumed, not left parked", job.isCompleted)
        assertEquals(Activity.RESULT_CANCELED, result?.resultCode)
    }

    @Test
    fun anAnsweredAsyncKeyChooserResumesItsCallerWithTheChosenKeyNotACancel() {
        val activity = composedReadingHost()
        var result: ActivityResult? = null
        // The ErrorActivity shape of anAsyncChooserLeftWithoutAnAnswerResumesItsCallerWithACancel:
        // a parked deferred at an async code, recorded under the key-chooser kind.
        activity.lifecycleScope.launch { result = activity.awaitIntent(Intent(activity, ErrorActivity::class.java)) }
        idle()
        val code = shadowOf(activity).nextStartedActivityForResult.requestCode
        requests(activity).record(ReadingResultKind.KeyChooser, code)
        val nav = standInAbove(activity)

        channel<KeyChooserResult>(activity, "keyChooserResults")
            .deliver(nav, KeyChooserResult(key = "Gen.1.1", book = "KJV"))
        idle()

        assertEquals(
            "an ANSWERED chooser must never be classified as abandoned on the way back to reading",
            Activity.RESULT_OK,
            result?.resultCode,
        )
        assertEquals("Gen.1.1", result?.data?.getStringExtra("key"))
        assertTrue("…and the request is spent", !requests(activity).isAwaiting(ReadingResultKind.KeyChooser))
    }

    @Test
    fun anAsyncChooserLeftWithoutAnAnswerResumesItsCallerWithACancel() {
        val activity = composedReadingHost()
        var result: ActivityResult? = null
        // A non-self intent, so awaitIntent parks a deferred without being intercepted; the request is
        // then recorded under the kind a real key-chooser launch records.
        activity.lifecycleScope.launch { result = activity.awaitIntent(Intent(activity, ErrorActivity::class.java)) }
        idle()
        val code = shadowOf(activity).nextStartedActivityForResult.requestCode
        requests(activity).record(ReadingResultKind.KeyChooser, code)
        val nav = standInAbove(activity)

        nav.popBackStack()   // the user backs out without choosing
        idle()

        assertEquals(
            "classic's chooser Activity returned RESULT_CANCELED; in-graph, the abandoned request must " +
                "still resume awaitChosenKey, whose cancel arm goes back in history",
            Activity.RESULT_CANCELED,
            result?.resultCode,
        )
        assertTrue("…and the request is spent", !requests(activity).isAwaiting(ReadingResultKind.KeyChooser))
    }

    // ——— B1 addition beyond the brief: the cancel arm's history step must stay on `reading` ————————
    //
    // The abandonment answer runs from the destination listener, i.e. BEFORE the reading
    // destination has recomposed -- so `ReadingViewVisibility.isVisible` is still false (the stand-in
    // was on top and the reading entry's composition, with its `enter`, is gone). `HistoryManager
    // .goBack()` reads that as "a non-reading screen is on top" and calls `leaveCurrentScreen()`, which
    // since slice 8 A3 pops the back stack -- or, with `reading` as the start destination, FINISHES the
    // one host: backing out of a chooser would close the app. The async caller's cancel arm
    // (`CurrentGeneralBookPage.awaitChosenKey`) and the STD arm share that `goBackInHistory()`.
    //
    // And the step must actually HAPPEN (Review Focus #3): `HistoryTraversal.goBack()` refuses on this
    // host's `reading` (`isIntegrateWithHistoryManager` is off there), so the host overrides
    // `goBackInHistory()` to replay the reading history directly.

    private fun historyManager(): HistoryManager = GlobalContext.get().get()

    private fun historyDepth(): Int =
        historyManager().getEntities(CommonUtils.windowControl.activeWindow.id).size

    private val kjvVersification get() = Versifications.instance().getVersification("KJV")

    private fun kjv() = requireNotNull(Books.installed().getBook("KJV")) { "KJV test module must be installed" }

    /** Ps 139:2 current and recorded as a `KeyHistoryItem` -- the item a history step must bring back. */
    private fun recordPs139() {
        val window = CommonUtils.windowControl.activeWindow
        window.pageManager.currentBible.setCurrentDocumentAndKey(kjv(), Verse(kjvVersification, BibleBook.PS, 139, 2))
        historyManager().addHistoryItem(window)
    }

    /** Move the window on to Ps 23:1, so a restored Ps 139:2 is observable. */
    private fun moveToPs23() {
        CommonUtils.windowControl.activeWindow.pageManager.currentBible
            .setCurrentDocumentAndKey(kjv(), Verse(kjvVersification, BibleBook.PS, 23, 1))
    }

    private fun activeOsisRef(): String? =
        (CommonUtils.windowControl.activeWindowPageManager.currentPage.singleKey as? Verse)?.osisID

    @Test
    fun anAbandonedAwaitedChooserGoesBackToThePriorHistoryItemAndStaysOnReading() {
        val activity = composedReadingHost()
        idle()
        recordPs139()
        // awaitChosenKey's shape (`CurrentGeneralBookPage.kt:210-217`), minus its `key == null`
        // precondition, which a Bible page cannot meet. The launch records the position being left
        // (slice 8 A2) -- Ps 139:2 again, a duplicate -- so the window moves on only AFTER it.
        activity.lifecycleScope.launch {
            val result = activity.awaitIntent(Intent(activity, ErrorActivity::class.java))
            if (result.resultCode == Activity.RESULT_CANCELED) activity.goBackInHistory()
        }
        idle()
        moveToPs23()
        idle()
        val depthBefore = historyDepth()
        assertTrue("fixture: history must be non-empty for goBack to do anything", depthBefore > 0)
        val code = shadowOf(activity).nextStartedActivityForResult.requestCode
        requests(activity).record(ReadingResultKind.KeyChooser, code)
        val nav = standInAbove(activity)

        nav.popBackStack()   // the user backs out without choosing
        idle()

        assertFalse("backing out of an abandoned chooser must not close the app", activity.isFinishing)
        assertEquals(NavRoutes.READING, nav.currentDestination?.route)
        assertEquals("classic went back in history; the prior item must be restored", "Ps.139.2", activeOsisRef())
        assertTrue("…and it was spent from the stack", historyDepth() < depthBefore)
    }

    @Test
    fun anAbandonedStdChooserOnAPageWithNoKeyGoesBackToThePriorHistoryItem() {
        val activity = composedReadingHost()
        idle()
        recordPs139()
        moveToPs23()
        // A dictionary switched to with no key -- classic's "page left with no key" case. The switch
        // itself asks for the key chooser at STD_REQUEST_CODE (`CurrentDictionaryPage.startKeyChooser`);
        // the request is (re)recorded under the kind a self-launch of that chooser records.
        val strongs = requireNotNull(Books.installed().getBook("StrongsGreek")) { "StrongsGreek test module must be installed" }
        CommonUtils.windowControl.activeWindowPageManager.setCurrentDocument(strongs)
        idle()
        assertEquals(
            "fixture: the page must have no key", null,
            CommonUtils.windowControl.activeWindowPageManager.currentPage.key,
        )
        assertTrue("fixture: history must be non-empty for goBack to do anything", historyDepth() > 0)
        requests(activity).record(ReadingResultKind.KeyChooser, ActivityBase.STD_REQUEST_CODE)
        // Since fix batch 6 F118 the switch's own STD self-launch navigates to the key chooser (it no longer
        // reaches the platform), so the chooser is already on top of `reading`; no stand-in is needed.
        idle()
        val nav = nav(activity)
        assertEquals(
            "fixture: the switch's STD launch must have put the dictionary key chooser on top",
            NavRoutes.CHOOSE_DICTIONARY_WORD, activity.currentRouteForTest(),
        )

        nav.popBackStack()   // the user backs out without choosing
        idle()

        assertFalse("backing out of an abandoned chooser must not close the app", activity.isFinishing)
        assertEquals(NavRoutes.READING, nav.currentDestination?.route)
        assertEquals(
            "classic's STD cancel arm went back in history; the Bible page must be restored",
            "KJV",
            CommonUtils.windowControl.activeWindowPageManager.currentPage.currentDocument?.initials,
        )
    }

    /**
     * The override must not change BACK. The reading branch of `onBackPressed` asks its own
     * "history" step (gated on `ReadingViewVisibility.isVisible`) and, on the confirmed second
     * press, exits through `ActivityBase.onBackPressed` -- which calls `goBackInHistory()` first.
     * Before B1 that always declined on `reading`; with a declined history step and a non-empty
     * stack, the exit must still be an exit, not a history step.
     */
    @Test
    fun theConfirmedBackExitOnReadingIsNotTurnedIntoAHistoryStep() {
        val activity = composedReadingHost()
        idle()
        recordPs139()
        moveToPs23()
        ReadingViewVisibility.setVisible(false)   // the chain's history step declines
        val depthBefore = historyDepth()
        assertTrue("fixture: history must be non-empty", depthBefore > 0)

        activity.onBackPressed()   // warns
        activity.onBackPressed()   // confirmed: exits

        assertTrue("the second BACK on reading exits, as before slice 8 B1", activity.isFinishing)
        assertEquals("…without spending history", depthBefore, historyDepth())
    }

    @Test
    fun aHistoryStepTakenWhileTheGraphIsOnReadingLeavesNoScreen() {
        val activity = composedReadingHost()
        idle()
        val nav = nav(activity)
        assertEquals(NavRoutes.READING, nav.currentDestination?.route)

        activity.leaveCurrentScreen()
        idle()

        assertFalse(
            "the reading view is the current screen; there is no other screen to leave, and finishing " +
                "the start destination is closing the app",
            activity.isFinishing,
        )
        assertEquals(NavRoutes.READING, nav.currentDestination?.route)
    }
}

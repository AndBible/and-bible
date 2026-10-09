package net.bible.android.view.compose

import android.os.Looper
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavHostController
import androidx.test.core.app.ApplicationProvider
import java.time.Duration
import kotlinx.coroutines.launch
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.view.activity.base.firstTime
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.service.common.CommonUtils
import net.bible.service.history.HistoryManager
import net.bible.service.history.KeyHistoryItem
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedcore.reading.ReadingHostPresence
import net.bible.sharedcore.reading.ReadingViewVisibility
import net.bible.test.DatabaseResetter
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.versification.BibleBook
import org.crosswire.jsword.versification.system.Versifications
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/**
 * Slice 8 finding M3. Classic `MainBibleActivity` recorded "where I left the reading view from"
 * through `ActivityBase.startActivity`'s `integrateWithHistoryManager` gate; the flipped host's flag
 * is false, so nothing recorded it and the History list lost every exit point.
 *
 * Fixture shape copied from `ReadingHostBackChainTest` (history pushes) and
 * `ReadingDestinationInGraphTest.theHostComposesTheRealReadingViewOnTheReadingRoute` (a composed
 * reading destination: `create().start().resume().visible()`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class ReadingHostHistoryOnExitTest {

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
        return Robolectric.buildActivity(
            NavHostComposeActivity::class.java,
            NavHostComposeActivity.intentFor(ApplicationProvider.getApplicationContext(), NavRoutes.READING),
        ).also { controllers += it }.create().start().resume().visible().get()
    }

    private fun historyManager(): HistoryManager = GlobalContext.get().get()

    private fun navControllerOf(activity: NavHostComposeActivity): NavHostController =
        NavHostComposeActivity::class.java.getDeclaredField("navController")
            .apply { isAccessible = true }.get(activity) as NavHostController

    private fun idle() = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))

    /** Puts the active window on Ps 23:1 and returns the stack size AFTER that move settled. */
    private fun readPsalm23(): Int {
        val kjv = requireNotNull(Books.installed().getBook("KJV")) { "KJV test module must be installed" }
        val window = CommonUtils.windowControl.activeWindow
        window.pageManager.currentBible.setCurrentDocumentAndKey(
            kjv, Verse(Versifications.instance().getVersification("KJV"), BibleBook.PS, 23, 1),
        )
        idle()
        return historyManager().getHistory(window.id).size
    }

    @Test
    fun leavingTheReadingViewRecordsWhereTheUserWas() {
        val activity = composedReadingHost()
        val before = readPsalm23()

        activity.startActivity(NavHostComposeActivity.intentFor(activity, NavRoutes.searchForm()))

        val history = historyManager().getHistory(CommonUtils.windowControl.activeWindow.id)
        assertEquals("exactly one item for the exit (M3)", before + 1, history.size)
        val top = history.first() as KeyHistoryItem
        assertEquals("the item names the verse the user left from", "Ps.23.1", top.key.getOsisID())
    }

    /**
     * The async self-launch path passes BOTH chokepoints -- `startActivityForResult` and then, through
     * `navigateInsteadOfSelfLaunch`, `navigateToRoute` -- and must still add exactly ONE item
     * (`HistoryManager.add` drops an item equal to the stack top). The payload is
     * `SelfLaunchAwaitIntentTest`'s.
     *
     * Not driven through `controller.newIntent(...)`: a real `onNewIntent` arrives PAUSED, when
     * `ReadingViewVisibility.isVisible` is false and `createHistoryItem` builds nothing -- which is also
     * classic's behaviour for an inbound route (classic never recorded one).
     */
    @Test
    fun anAsyncSelfLaunchRecordsExactlyOnce() {
        val activity = composedReadingHost()
        val before = readPsalm23()

        activity.lifecycleScope.launch {
            activity.awaitIntent(NavHostComposeActivity.intentFor(activity, NavRoutes.manageLabels("""{"mode":"ASSIGN"}""")))
        }
        idle()

        assertEquals(
            "two chokepoints, one exit, one item",
            before + 1,
            historyManager().getHistory(CommonUtils.windowControl.activeWindow.id).size,
        )
    }

    @Test
    fun aLaunchThatStaysOnReadingRecordsNothing() {
        val activity = composedReadingHost()
        val before = readPsalm23()

        activity.startActivity(NavHostComposeActivity.intentFor(activity, NavRoutes.READING))

        assertEquals(before, historyManager().getHistory(CommonUtils.windowControl.activeWindow.id).size)
    }

    @Test
    fun aLaunchFromAnotherDestinationRecordsNothing() {
        val activity = composedReadingHost()
        val before = readPsalm23()
        val nav = navControllerOf(activity)
        // An in-graph hop off the reading view driven on the controller DIRECTLY: it bypasses both
        // chokepoints, so it records NOTHING (asserted just below).
        //
        // Deliberately NOT idled before the launch. Once the hop settles the reading entry is
        // disposed, `ReadingViewVisibility.isVisible` goes false and `HistoryManager.createHistoryItem`
        // builds nothing anyway -- a launch there could not tell whether `recordHistoryOnLeavingReading`
        // is gated on the destination at all (measured: dropping the gate stayed green). Until the
        // transition settles the old reading entry is still composed, so the flag is still true while
        // `currentDestination` already names the new route; that window is the one only the
        // `readingDestinationIsCurrent()` gate covers.
        nav.navigate(NavRoutes.AI_TOOL_INFO)
        val afterHop = historyManager().getHistory(CommonUtils.windowControl.activeWindow.id).size
        assertTrue("sanity: the hop itself went through navigate, not the chokepoint", afterHop == before)
        assertTrue(
            "sanity: the reading entry is still composed, so only the destination gate can refuse",
            ReadingViewVisibility.isVisible,
        )

        activity.startActivity(NavHostComposeActivity.intentFor(activity, NavRoutes.searchForm()))
        idle()

        assertEquals(
            "only the READING destination records -- no blanket destination listener (parent spec ban)",
            afterHop,
            historyManager().getHistory(CommonUtils.windowControl.activeWindow.id).size,
        )
    }
}

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

import androidx.activity.BackEventCompat
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ApplicationProvider
import net.bible.android.TestBibleApplication
import net.bible.android.view.activity.base.firstTime
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedcore.reading.ReadingViewVisibility
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/**
 * Issue #4073 follow-up: system BACK presses that arrive before the graph has recomposed after a destination change.
 *
 * Nothing is idled between a destination change and the next press, so the destination's Compose back handlers and
 * the NavHost's own handler still have the enablement of the previous frame. A press in that state must still go
 * where the new destination sends it: the reading back chain on `reading` (which warns before exiting), a pop
 * elsewhere. It must never reach the platform fallback that finishes the Activity. The one exception to "and is not
 * swallowed" is navigation-compose's own: see [twoQuickBacksFromSettingsStayInTheApp].
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class)
class SystemBackBurstTest {

    @get:org.junit.Rule
    val compose = createEmptyComposeRule()

    private val hostControllers = mutableListOf<ActivityController<NavHostComposeActivity>>()

    private fun host(): NavHostComposeActivity {
        firstTime = false
        return Robolectric.buildActivity(
            NavHostComposeActivity::class.java,
            NavHostComposeActivity.intentFor(ApplicationProvider.getApplicationContext(), NavRoutes.READING),
        ).also { hostControllers += it }.setup().get().also {
            net.bible.test.awaitReadingGraph(it) { compose.waitForIdle() }
        }
    }

    private fun settledOnSettings(): NavHostComposeActivity = host().also { activity ->
        activity.navigateInGraph(NavRoutes.SETTINGS)
        net.bible.test.awaitHostRoute(activity, NavRoutes.SETTINGS) { compose.waitForIdle() }
        compose.waitForIdle()
    }

    @Before fun readingViewIsWhatTheUserSees() = ReadingViewVisibility.setVisible(true)

    @After
    fun resetReadingSeams() {
        net.bible.test.closeReadingHostsBeforeReset(hostControllers)
    }

    private val exitWarning: String
        get() = ApplicationProvider.getApplicationContext<android.content.Context>()
            .getString(net.bible.android.activity.R.string.one_more_back_press)

    private fun historyDepth(): Int =
        org.koin.core.context.GlobalContext.get().get<net.bible.service.history.HistoryManager>()
            .getEntities(net.bible.service.common.CommonUtils.windowControl.activeWindow.id).size

    /**
     * Runs [press] and asserts the reading back chain handled it: it either stepped the reading history back (leaving
     * Settings recorded one item) or, with nothing left to consume the press, showed the exit warning. A swallowed
     * press does neither.
     */
    private fun assertTheReadingChainRan(message: String, press: () -> Unit) {
        org.robolectric.shadows.ShadowToast.reset()
        val depth = historyDepth()
        press()
        val warned = org.robolectric.shadows.ShadowToast.getTextOfLatestToast() == exitWarning
        val steppedHistory = historyDepth() < depth
        assertTrue("$message (warned=$warned, history $depth -> ${historyDepth()})", warned || steppedHistory)
    }

    /**
     * Settings settled, then two presses. Between them only [betweenPresses] runs: enough of the compose clock to
     * execute the NavHost's pop (a coroutine), not necessarily the frames that recompose `reading`. On the device the
     * second of two presses ~40 ms apart arrives there.
     */
    private fun twoQuickBacksFromSettings(chainMustRun: Boolean, betweenPresses: () -> Unit) {
        val activity = settledOnSettings()
        compose.mainClock.autoAdvance = false
        try {
            activity.onBackPressedDispatcher.onBackPressed()
            betweenPresses()
            assertEquals("the first press pops Settings", NavRoutes.READING, activity.currentRouteForTest()?.substringBefore('?'))

            if (chainMustRun) {
                assertTheReadingChainRan("the second press is the reading view's, handled by its chain") {
                    activity.onBackPressedDispatcher.onBackPressed()
                }
            } else {
                activity.onBackPressedDispatcher.onBackPressed()
            }
            assertFalse("a second BACK before reading recomposed must not close the app", activity.isFinishing)
        } finally {
            compose.mainClock.autoAdvance = true
        }
        compose.waitForIdle()
        assertFalse("nor once the graph has settled", activity.isFinishing)
        assertEquals(NavRoutes.READING, activity.currentRouteForTest()?.substringBefore('?'))
    }

    /**
     * Right after the pop, before any frame: the NavHost's own handler still has its last frame's enablement (two
     * entries) and takes the press with nothing left to pop. That is navigation-compose's, above every host callback;
     * it keeps the app open, which is what #4073 needs.
     */
    @Test
    fun twoQuickBacksFromSettingsStayInTheApp() = twoQuickBacksFromSettings(chainMustRun = false) {
        compose.mainClock.advanceTimeBy(0)
    }

    /**
     * One frame later the NavHost's handler is off (one entry) and the reading arm has not registered its handler
     * yet: before the host callback no callback was enabled here and the platform closed the app.
     */
    @Test
    fun twoBacksOneFrameApartFromSettingsRunTheReadingChain() = twoQuickBacksFromSettings(chainMustRun = true) {
        compose.mainClock.advanceTimeBy(1)
    }

    @Test
    fun aBackRightAfterOpeningSettingsReturnsToReading() {
        val activity = host()

        activity.navigateInGraph(NavRoutes.SETTINGS)
        activity.onBackPressedDispatcher.onBackPressed()
        compose.waitForIdle()

        assertFalse(activity.isFinishing)
        assertEquals(
            "a BACK before Settings composed must pop it, not be swallowed",
            NavRoutes.READING, activity.currentRouteForTest()?.substringBefore('?'),
        )
    }

    @Test
    fun aPredictiveBackStartedRightAfterOpeningSettingsReturnsToReading() {
        val activity = host()

        activity.navigateInGraph(NavRoutes.SETTINGS)
        // A slow swipe: the gesture starts before Settings composed and is released after the graph settled.
        // AndroidX keeps the callback that was enabled at the start for the whole gesture.
        val dispatcher = activity.onBackPressedDispatcher
        dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 0f, 0f, BackEventCompat.EDGE_LEFT))
        compose.waitForIdle()
        dispatcher.dispatchOnBackProgressed(BackEventCompat(100f, 0f, 0.5f, BackEventCompat.EDGE_LEFT))
        dispatcher.onBackPressed()
        compose.waitForIdle()

        assertFalse(activity.isFinishing)
        assertEquals(NavRoutes.READING, activity.currentRouteForTest()?.substringBefore('?'))
    }

    @Test
    fun aPredictiveBackStartedRightAfterReturningToReadingRunsTheReadingChain() {
        val activity = settledOnSettings()
        val dispatcher = activity.onBackPressedDispatcher
        compose.mainClock.autoAdvance = false
        try {
            dispatcher.onBackPressed()
            compose.mainClock.advanceTimeBy(1)
            dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 0f, 0f, BackEventCompat.EDGE_LEFT))
        } finally {
            compose.mainClock.autoAdvance = true
        }
        compose.waitForIdle()
        assertTheReadingChainRan("a gesture started in the frame after the pop is the reading view's, not swallowed") {
            dispatcher.onBackPressed()
            compose.waitForIdle()
        }

        assertFalse("the reading chain's first unconsumed press only warns", activity.isFinishing)
        assertEquals(NavRoutes.READING, activity.currentRouteForTest()?.substringBefore('?'))
    }
}

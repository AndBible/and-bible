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

import android.content.Intent
import android.os.Bundle
import android.os.Looper
import android.view.WindowManager
import androidx.navigation.NavHostController
import androidx.test.core.app.ApplicationProvider
import java.time.Duration
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.view.activity.base.firstTime
import net.bible.android.view.activity.comingFromStartupActivity
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.service.common.CommonUtils
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedcore.reading.ReadingHostPresence
import net.bible.sharedcore.reading.ReadingViewVisibility
import net.bible.test.DatabaseResetter
import net.bible.test.resetComposeUiDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/** Slice 8 §4 gate (b) on a real host started on WELCOME (uninitialised, spec §3.1 rule 2). */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class UsableBibleGateTest {

    private val controllers = mutableListOf<ActivityController<NavHostComposeActivity>>()

    @After
    fun tearDown() {
        controllers.forEach { it.close() }
        controllers.clear()
        CommonUtils.realSharedPreferences.edit().remove("show_calculator").commit()
        comingFromStartupActivity = false
        ReadingHostPresence.setForeground(null)
        ReadingViewVisibility.setVisible(false)
        DatabaseResetter.resetDatabase()
    }

    private fun welcomeIntent() =
        NavHostComposeActivity.intentFor(ApplicationProvider.getApplicationContext(), NavRoutes.WELCOME)

    private fun welcomeHost(usable: Boolean = true, intent: Intent = welcomeIntent()): NavHostComposeActivity {
        // These tests assert after the first frame (a destination change, the listener): see
        // resetComposeUiDispatcher for what an earlier host test leaves behind in the same JVM.
        resetComposeUiDispatcher()
        firstTime = false
        val controller = Robolectric.buildActivity(NavHostComposeActivity::class.java, intent).also { controllers += it }
        controller.get().usableBibleGate = { usable }
        return controller.create().start().resume().visible().get()
    }

    private fun flagSecure(a: NavHostComposeActivity): Boolean =
        a.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0

    private fun route(a: NavHostComposeActivity): String? = nav(a).currentDestination?.route

    private fun idle() = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))

    private fun nav(a: NavHostComposeActivity): NavHostController =
        NavHostComposeActivity::class.java.getDeclaredField("navController").apply { isAccessible = true }.get(a) as NavHostController

    private fun bootstrapped(a: NavHostComposeActivity): Boolean =
        NavHostComposeActivity::class.java.getDeclaredField("readingAppBootstrapped").apply { isAccessible = true }.getBoolean(a)

    @Test
    fun withAUsableBibleTheWelcomeIsReplacedByTheReadingView() {
        val activity = welcomeHost()
        assertEquals(NavRoutes.WELCOME, nav(activity).currentDestination?.route)

        activity.welcomeAfterFlow()
        idle()

        assertEquals(NavRoutes.READING, nav(activity).currentDestination?.route)
        assertNull("WELCOME is popped inclusive: back from reading exits", nav(activity).previousBackStackEntry)
        assertTrue("the reading view is bootstrapped before it composes", bootstrapped(activity))
    }

    @Test
    fun withoutAUsableBibleTheWelcomeStays() {
        val activity = welcomeHost(usable = false)
        activity.welcomeAfterFlow()
        idle()
        assertEquals(NavRoutes.WELCOME, nav(activity).currentDestination?.route)
        assertFalse(bootstrapped(activity))
    }

    /** Fix round 1 minor 3: through the real flow (Import), which initialises + secures the window on the way out. */
    @Test
    fun returningToWelcomeFromAFlowReChecks() {
        CommonUtils.realSharedPreferences.edit().putBoolean("show_calculator", true).commit()
        val activity = welcomeHost()
        assertFalse("sanity: an uninitialised WELCOME start applies no FLAG_SECURE", flagSecure(activity))

        activity.welcomeFlow.navDeps().onImport()
        idle()
        assertEquals(NavRoutes.INSTALL_ZIP_PATTERN, route(activity))
        assertTrue("leaving Welcome initialises the app and its window (Correction 6)", flagSecure(activity))

        nav(activity).popBackStack()
        idle()
        assertEquals("the listener's re-check ran gate (b)", NavRoutes.READING, route(activity))
    }

    /**
     * The real `WelcomeFlow.leaveFor` (initialise, then navigate in-graph) onto a light stand-in destination:
     * the real Download destination starts a repository refresh that has no network to finish in a unit test.
     */
    private fun leaveWelcomeLikeAFlow(activity: NavHostComposeActivity) {
        activity.welcomeFlow.leaveFor(NavRoutes.AI_TOOL_INFO)
        idle()
        assertEquals(NavRoutes.AI_TOOL_INFO, route(activity))
        assertTrue("sanity: the listener armed the re-check", activity.welcomeFlow.awaitingReturn)
    }

    /**
     * Fix round 1 (review Important): a recreate while a flow is over Welcome (a uiMode / font-scale change --
     * this host does not handle them) keeps the initialised window state and the pending re-check.
     */
    @Test
    fun aRecreateDuringAFlowOverWelcomeKeepsTheWindowStateAndTheReCheck() {
        CommonUtils.realSharedPreferences.edit().putBoolean("show_calculator", true).commit()
        val activity = welcomeHost()
        leaveWelcomeLikeAFlow(activity)
        assertTrue(flagSecure(activity))

        resetComposeUiDispatcher()
        val recreated = controllers.last().recreate().get()
        idle()
        assertTrue("sanity: the flow is still on top", NavRoutes.AI_TOOL_INFO == route(recreated))
        assertTrue("the recreated host is still an initialised one: FLAG_SECURE (Correction 6)", flagSecure(recreated))

        nav(recreated).popBackStack()
        idle()
        assertEquals("the restored back stack still re-checks on the way back", NavRoutes.READING, route(recreated))
    }

    /** Fix round 1: the same, shaped like a process-death restore -- the saved Bundle into a fresh instance. */
    @Test
    fun aProcessDeathRestoreDuringAFlowOverWelcomeKeepsTheWindowStateAndTheReCheck() {
        CommonUtils.realSharedPreferences.edit().putBoolean("show_calculator", true).commit()
        val activity = welcomeHost()
        leaveWelcomeLikeAFlow(activity)

        val saved = Bundle()
        val first = controllers.removeAt(controllers.lastIndex)
        first.pause().saveInstanceState(saved).stop().destroy()
        idle()

        resetComposeUiDispatcher()
        val restored = Robolectric.buildActivity(NavHostComposeActivity::class.java, welcomeIntent())
            .also { controllers += it }
            .create(saved).start().restoreInstanceState(saved).resume().visible().get()
        idle()
        assertTrue("sanity: the flow is restored on top", NavRoutes.AI_TOOL_INFO == route(restored))
        assertTrue("the restored host initialised itself: FLAG_SECURE (Correction 6)", flagSecure(restored))

        nav(restored).popBackStack()
        idle()
        assertEquals("the restored back stack still re-checks on the way back", NavRoutes.READING, route(restored))
    }

    /** Fix round 1 minor 4: the unlock prompt suspends, so two re-checks can overlap; only one transitions. */
    @Test
    fun overlappingReChecksTransitionOnce() {
        val activity = welcomeHost()
        activity.welcomeAfterFlow()
        activity.welcomeAfterFlow()
        idle()
        assertEquals(NavRoutes.READING, route(activity))
        assertNull("exactly one READING entry, nothing beneath it", nav(activity).previousBackStackEntry)
    }

    /**
     * Fix round 1 minor 2 / Review Focus #2: a deep link opened on a fresh install reaches the WELCOME-started
     * host as `openLink`, and gate (b)'s bootstrap dispatches it once a Bible is usable.
     */
    @Test
    fun aDeepLinkCarriedToWelcomeIsOpenedByGateB() {
        val activity = welcomeHost(
            intent = welcomeIntent().putExtra("openLink", "https://read.andbible.org/Rev.22.21"),
        )
        activity.welcomeAfterFlow()
        idle()
        assertEquals(NavRoutes.READING, route(activity))
        // showLink opens it in the links window (WindowControl.showLink); that window's page is the chapter.
        val linksKeys = CommonUtils.windowControl.windowRepository.windowList
            .filter { it.isLinksWindow }.map { it.pageManager.currentPage.key?.getOsisRef() }
        assertTrue("the deep link's passage is open in the links window: $linksKeys", linksKeys.any { it == "Rev.22" })
    }

    @Test
    fun theFirstArrivalAtWelcomeDoesNotReCheck() {
        // A host StartupActivity sent to WELCOME has already failed the check (and the unlock attempt);
        // re-checking on arrival would prompt again immediately.
        val activity = welcomeHost()
        idle()
        assertEquals(NavRoutes.WELCOME, nav(activity).currentDestination?.route)
    }

    /** Plan Correction 6 / Review Focus 4. */
    @Test
    fun aDiscreteUserKeepsFlagSecureAcrossTheTransition() {
        CommonUtils.realSharedPreferences.edit().putBoolean("show_calculator", true).commit()
        val activity = welcomeHost()
        assertEquals(
            "sanity: an uninitialised WELCOME start applies no FLAG_SECURE",
            0, activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE,
        )
        activity.welcomeAfterFlow()
        idle()
        assertTrue(
            "the reading view must not show in Recents in discrete mode",
            activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0,
        )
    }

    /** Plan Correction 5: the in-graph transition produces no onResume, so writing the flag would suppress the NEXT real calculator prompt. */
    @Test
    fun theTransitionDoesNotArmTheStartupFlag() {
        comingFromStartupActivity = false
        val activity = welcomeHost()
        activity.welcomeAfterFlow()
        idle()
        assertFalse(comingFromStartupActivity)
    }

    @Test
    fun aReadingStartWithoutAUsableBibleOpensWelcomeInstead() {
        firstTime = false
        val controller = Robolectric.buildActivity(
            NavHostComposeActivity::class.java,
            NavHostComposeActivity.intentFor(ApplicationProvider.getApplicationContext(), NavRoutes.READING),
        ).also { controllers += it }
        controller.get().usableBibleGate = { false }
        val activity = controller.create().get()
        val startRoute = NavHostComposeActivity::class.java.getDeclaredField("startRoute")
            .apply { isAccessible = true }.get(activity)
        assertEquals("gate (c): M7's PendingIntents must not reach a Bible-less reading view", NavRoutes.WELCOME, startRoute)
        assertFalse(bootstrapped(activity))
    }

    @Test
    fun aReadingRouteDeliveredLaterWithoutAUsableBibleDoesNotBootstrap() {
        firstTime = false
        val controller = Robolectric.buildActivity(
            NavHostComposeActivity::class.java,
            NavHostComposeActivity.intentFor(ApplicationProvider.getApplicationContext(), NavRoutes.WELCOME),
        ).also { controllers += it }
        controller.get().usableBibleGate = { false }
        val activity = controller.create().start().resume().visible().get()
        controller.newIntent(NavHostComposeActivity.intentFor(activity, NavRoutes.READING))
        idle()
        assertFalse("onNewIntent is the second READING entry point", bootstrapped(activity))
        assertEquals(NavRoutes.WELCOME, nav(activity).currentDestination?.route)
    }

    /**
     * Slice 8 final review, finding 4: gate (c) through [NavHostComposeActivity.onNewIntent] on a LIVE reading
     * host pushes WELCOME over READING; gate (b) must then return to that READING entry, not stack a second one
     * (`navigate(READING) { popUpTo(WELCOME) { inclusive } }` leaves READING, READING).
     */
    @Test
    fun gateBOverALiveReadingEntryReturnsToItInsteadOfStackingASecond() {
        resetComposeUiDispatcher()
        firstTime = false
        var usable = true
        val controller = Robolectric.buildActivity(
            NavHostComposeActivity::class.java,
            NavHostComposeActivity.intentFor(ApplicationProvider.getApplicationContext(), NavRoutes.READING),
        ).also { controllers += it }
        controller.get().usableBibleGate = { usable }
        val activity = controller.create().start().resume().visible().get()
        idle()
        assertEquals(NavRoutes.READING, route(activity))

        usable = false
        controller.newIntent(NavHostComposeActivity.intentFor(activity, NavRoutes.READING))
        idle()
        assertEquals("sanity: gate (c) pushed WELCOME over the live reading entry", NavRoutes.WELCOME, route(activity))

        usable = true
        activity.welcomeAfterFlow()
        idle()

        assertEquals(NavRoutes.READING, route(activity))
        val readingEntries = nav(activity).currentBackStack.value.count { it.destination.route == NavRoutes.READING }
        assertEquals("exactly one READING entry on the back stack", 1, readingEntries)
        assertNull("nothing beneath it: back from reading exits", nav(activity).previousBackStackEntry)
    }
}

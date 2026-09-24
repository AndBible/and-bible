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

    private fun welcomeHost(usable: Boolean = true): NavHostComposeActivity {
        // These tests assert after the first frame (a destination change, the listener): see
        // resetComposeUiDispatcher for what an earlier host test leaves behind in the same JVM.
        resetComposeUiDispatcher()
        firstTime = false
        val controller = Robolectric.buildActivity(
            NavHostComposeActivity::class.java,
            NavHostComposeActivity.intentFor(ApplicationProvider.getApplicationContext(), NavRoutes.WELCOME),
        ).also { controllers += it }
        controller.get().usableBibleGate = { usable }
        return controller.create().start().resume().visible().get()
    }

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

    @Test
    fun returningToWelcomeFromAFlowReChecks() {
        val activity = welcomeHost()
        activity.welcomeFlow.awaitingReturn = true
        nav(activity).navigate(NavRoutes.AI_TOOL_INFO)   // stand-in for Download / InstallZip
        idle()
        nav(activity).popBackStack()
        idle()
        assertEquals("the listener's re-check ran gate (b)", NavRoutes.READING, nav(activity).currentDestination?.route)
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
}

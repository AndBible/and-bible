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
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.view.Screen
import net.bible.android.view.ScreenLauncher
import net.bible.android.view.activity.base.IntentHelper
import net.bible.android.view.activity.base.firstTime
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedcore.reading.ReadingHostPresence
import net.bible.sharedcore.reading.ReadingViewVisibility
import net.bible.test.DatabaseResetter
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Fix batch 1 §2.5a (F88 + first-prefs force-stop): Settings' return work is paid on RETURN only.
 * The platform's synthetic RESULT_CANCELED for the singleTop self-launch must not pay it on entry.
 * Payments are counted through `onReadingReturnWorkForTest`, so `changeAppIconAndName` (whose
 * `forceStopApp` is `exitProcess`) is never reached.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class SettingsReturnWorkPaidOnceTest {
    private val controllers = mutableListOf<ActivityController<NavHostComposeActivity>>()

    /** `firstTime` is a file-level var Robolectric does not reset; true makes fixNightMode recreate() and null navController. */
    @Before
    fun firstTimeIsPinnedFalse() {
        firstTime = false
    }

    @After
    fun tearDown() {
        controllers.forEach { it.close() }
        controllers.clear()
        ReadingHostPresence.setForeground(null)
        ReadingViewVisibility.setVisible(false)
        DatabaseResetter.resetDatabase()
    }

    private fun host() = Robolectric.buildActivity(
        NavHostComposeActivity::class.java,
        NavHostComposeActivity.intentFor(ApplicationProvider.getApplicationContext(), NavRoutes.READING),
    ).also { controllers += it }

    private fun idleMain() = shadowOf(Looper.getMainLooper()).idle()

    @Test
    fun theSyntheticCancelOfASettingsSelfLaunchPaysNothingOnEntry() {
        val controller = host().apply { create().start().resume().visible() }
        val activity = controller.get()
        val paid = mutableListOf<Int>()
        activity.onReadingReturnWorkForTest = { paid += it }

        activity.startActivityForResult(ScreenLauncher.intentFor(activity, Screen.Settings), IntentHelper.REFRESH_DISPLAY_ON_FINISH)
        activity.navigateInGraph(NavRoutes.SETTINGS) // production: the self-launch arrives as onNewIntent
        controller.pause()
        activity.onActivityResult(IntentHelper.REFRESH_DISPLAY_ON_FINISH, Activity.RESULT_CANCELED, null)
        controller.resume()
        idleMain()

        assertEquals(emptyList(), paid, "Settings' return work ran on ENTRY -- the force-stop F88 and the first-prefs finding saw")

        activity.navigateInGraph(NavRoutes.READING) // the user comes back
        idleMain()
        assertEquals(listOf(IntentHelper.REFRESH_DISPLAY_ON_FINISH), paid, "...and must be paid exactly once on return")
    }

    /** Review Focus 3: a second round trip pays again, exactly once. */
    @Test
    fun aSecondSettingsRoundTripPaysExactlyOnceAgain() {
        val controller = host().apply { create().start().resume().visible() }
        val activity = controller.get()
        val paid = mutableListOf<Int>()
        activity.onReadingReturnWorkForTest = { paid += it }
        repeat(2) {
            activity.startActivityForResult(ScreenLauncher.intentFor(activity, Screen.Settings), IntentHelper.REFRESH_DISPLAY_ON_FINISH)
            activity.navigateInGraph(NavRoutes.SETTINGS)
            controller.pause()
            activity.onActivityResult(IntentHelper.REFRESH_DISPLAY_ON_FINISH, Activity.RESULT_CANCELED, null)
            controller.resume()
            idleMain()
            activity.navigateInGraph(NavRoutes.READING)
            idleMain()
        }
        assertEquals(listOf(IntentHelper.REFRESH_DISPLAY_ON_FINISH, IntentHelper.REFRESH_DISPLAY_ON_FINISH), paid)
    }

    /** F110: no platform launch -- that is what made a second host instance on API 28. */
    @Test
    fun aSettingsSelfLaunchNavigatesTheLiveGraphInsteadOfLaunching() {
        val controller = host().apply { create().start().resume().visible() }
        val activity = controller.get()
        val paid = mutableListOf<Int>()
        activity.onReadingReturnWorkForTest = { paid += it }

        activity.startActivityForResult(ScreenLauncher.intentFor(activity, Screen.Settings), IntentHelper.REFRESH_DISPLAY_ON_FINISH)
        idleMain()
        assertNull(shadowOf(activity).nextStartedActivity, "the self-launch must not reach the platform")
        assertEquals(NavRoutes.SETTINGS, activity.currentRouteForTest()?.substringBefore('?'))
        assertEquals(emptyList(), paid)

        activity.navigateInGraph(NavRoutes.READING)
        idleMain()
        assertEquals(listOf(IntentHelper.REFRESH_DISPLAY_ON_FINISH), paid)
    }

    @Test
    @Config(sdk = [28])
    fun onApi28TooTheSettingsRoundTripPaysOnReturnWithoutASecondInstance() =
        aSettingsSelfLaunchNavigatesTheLiveGraphInsteadOfLaunching()

    /** Review Focus 3. */
    @Test
    fun withNoGraphYetTheSelfLaunchStillGoesToThePlatform() {
        val activity = host().create().get() // no .visible(): nothing composed, navController null
        assertNull(activity.currentRouteForTest(), "precondition: there must be no graph yet")
        activity.startActivityForResult(ScreenLauncher.intentFor(activity, Screen.Settings), IntentHelper.REFRESH_DISPLAY_ON_FINISH)
        assertNotNull(shadowOf(activity).nextStartedActivity)
    }
}

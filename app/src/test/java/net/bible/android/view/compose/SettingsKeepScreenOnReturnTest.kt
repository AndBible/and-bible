/*
 * Copyright (c) 2026 Sykero Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 * Licensed under the GNU General Public License, version 3 or later.
 */
package net.bible.android.view.compose

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.view.WindowManager
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavHostController
import androidx.test.core.app.ApplicationProvider
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.activity.BuildConfig
import net.bible.android.view.Screen
import net.bible.android.view.ScreenLauncher
import net.bible.android.view.activity.base.IntentHelper
import net.bible.android.view.activity.base.firstTime
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.service.common.CommonUtils
import net.bible.service.db.DatabaseContainer
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedcore.settings.AppSettingsService
import net.bible.test.awaitHostRoute
import net.bible.test.awaitReadingGraph
import net.bible.test.closeReadingHostsBeforeReset
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** The live Settings round trip must refresh the existing Android window, not rely on onRestart. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class SettingsKeepScreenOnReturnTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val controllers = mutableListOf<ActivityController<NavHostComposeActivity>>()
    private val aliasStates = mutableMapOf<ComponentName, Int>()
    private var originalFirstTime = false
    private var originalKeepOn: Boolean? = null

    @Before
    fun preserveSettingsAndMatchLauncherAliases() {
        originalFirstTime = firstTime
        firstTime = false
        val store = DatabaseContainer.instance.settingsStore
        // Different answers for the two defaults mean the key was absent, not explicitly false.
        originalKeepOn = store.getBoolean(KEEP_ON, false).takeIf {
            it == store.getBoolean(KEEP_ON, true)
        }
        val pm = ApplicationProvider.getApplicationContext<Context>().packageManager
        val discrete = CommonUtils.realSharedPreferences.getBoolean("discrete_mode", false)
        listOf(
            "net.bible.android.activity.StartupActivity" to !discrete,
            "net.bible.android.view.activity.Calculator" to discrete,
        ).forEach { (name, enabled) ->
            val component = ComponentName(BuildConfig.APPLICATION_ID, name)
            aliasStates[component] = pm.getComponentEnabledSetting(component)
            // Let the real preference-return privacy step run without changing an alias / exitProcess.
            pm.setComponentEnabledSetting(
                component,
                if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
        }
    }

    @After
    fun closeHostsThenRestoreBeforeDatabaseReset() {
        closeReadingHostsBeforeReset(controllers, restoreBeforeReset = {
            CommonUtils.settings.setBoolean(KEEP_ON, originalKeepOn)
            GlobalContext.get().get<AppSettingsService>().refresh()
            firstTime = originalFirstTime
            val pm = ApplicationProvider.getApplicationContext<Context>().packageManager
            aliasStates.forEach { (component, state) ->
                pm.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP)
            }
        })
    }

    @Test
    fun enablingKeepScreenOnInSettingsSetsTheSameReaderWindowFlagOnReturn() {
        settingsRoundTrip(initial = false, changed = true)
    }

    @Test
    fun disablingKeepScreenOnInSettingsClearsTheSeparatelySeededReaderWindowFlagOnReturn() {
        settingsRoundTrip(initial = true, changed = false)
    }

    private fun settingsRoundTrip(initial: Boolean, changed: Boolean) {
        CommonUtils.settings.setBoolean(KEEP_ON, initial)
        val controller = Robolectric.buildActivity(
            NavHostComposeActivity::class.java,
            NavHostComposeActivity.intentFor(ApplicationProvider.getApplicationContext(), NavRoutes.READING),
        ).also { controllers += it }.apply { create().start().resume().visible() }
        val activity = controller.get()
        awaitReadingGraph(activity) { compose.waitForIdle() }
        val window = activity.window
        fun keepOnFlag() = window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0
        assertEquals(initial, keepOnFlag(), "Control: host creation must apply the seeded preference")
        val nav = NavHostComposeActivity::class.java.getDeclaredField("navController")
            .apply { isAccessible = true }.get(activity) as NavHostController
        val readerEntry = nav.currentBackStackEntry
        val lifecycleEvents = mutableListOf<Lifecycle.Event>()
        val observer = LifecycleEventObserver { _, event -> lifecycleEvents += event }
        activity.lifecycle.addObserver(observer)
        lifecycleEvents.clear() // Ignore the observer's initial synchronization to RESUMED.
        try {
            assertNull(activity.onReadingReturnWorkForTest, "Real preference-return work must not be stubbed")
            compose.runOnIdle {
                activity.startActivityForResult(
                    ScreenLauncher.intentFor(activity, Screen.Settings),
                    IntentHelper.REFRESH_DISPLAY_ON_FINISH,
                )
            }
            awaitHostRoute(activity, NavRoutes.SETTINGS) { compose.waitForIdle() }
            assertNull(shadowOf(activity).nextStartedActivity, "Settings must stay in the existing host graph")

            val service = GlobalContext.get().get<AppSettingsService>()
            compose.runOnIdle { service.setBool(KEEP_ON, changed) }
            assertEquals(changed, service.snapshot.value.screenKeepOn)
            assertEquals(changed, CommonUtils.settings.getBoolean(KEEP_ON, initial))
            compose.runOnIdle { assertTrue(nav.popBackStack(), "Return through the real Settings back stack") }
            awaitReadingGraph(activity) { compose.waitForIdle() }
            compose.waitForIdle()

            assertEquals(NavRoutes.READING, activity.currentRouteForTest()?.substringBefore('?'))
            assertSame(readerEntry, nav.currentBackStackEntry, "Return must reveal the original reader entry")
            assertSame(activity, controller.get())
            assertSame(window, activity.window, "No replacement Activity/window may supply the refresh")
            assertEquals(Lifecycle.State.RESUMED, activity.lifecycle.currentState)
            assertEquals(emptyList(), lifecycleEvents, "No pause/resume, restart, or recreation may mask the defect")
            assertNull(shadowOf(activity).nextStartedActivity, "The return must not launch another Activity")
            assertEquals(
                changed, keepOnFlag(),
                "Settings return must refresh FLAG_KEEP_SCREEN_ON on the same reader window ($initial -> $changed)",
            )
        } finally {
            activity.lifecycle.removeObserver(observer)
        }
    }

    private companion object {
        const val KEEP_ON = "screen_keep_on_pref"
    }
}

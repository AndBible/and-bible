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
import net.bible.android.BibleApplication
import net.bible.android.TEST_SDK
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.service.common.CommonUtils
import net.bible.service.db.DatabaseContainer
import net.bible.sharedcore.nav.NavRoutes
import net.bible.test.resetComposeUiDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Slice 8 D1 (spec §3.1 rule 2: INSTALL_ZIP initialises "as today"). A host started UNINITIALISED on BACKUP (the crash dialog's "Backup &
 * restore", before `initializeDatabase()`) can reach InstallZip in-graph (Backup's "restore documents",
 * wired in D2). The classic `InstallZipComposeActivity` initialised the app in its own `ActivityBase.onCreate`,
 * so the in-graph destination must too, before the install touches the database. Same test-side trick as
 * [BackupBeforeDbInitTest]: `isRunningTests` is off inside the test body, so `DataBaseNotReady` is live.
 *
 * The database is opened once BEFORE the switch (while `isRunningTests` still picks the Robolectric
 * SQLite factory), so the initialisation under test finds an existing container instead of building one
 * over the device-only Requery factory; `ready = false` still makes every read throw until it runs.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = InstallZipBeforeDbInitTest.NotRunningTestsApplication::class, sdk = [TEST_SDK])
class InstallZipBeforeDbInitTest {

    class NotRunningTestsApplication : BibleApplication() {
        override val isRunningTests: Boolean get() = runningTests
    }

    /** The [openInstallZip][NavHostComposeActivity.openInstallZip] path: the in-app opener initialises first. */
    @Test
    fun aBackupStartedHostInitialisesTheAppWhenItOpensInstallZip() = onAnUninitialisedBackupHost { activity ->
        activity.openInstallZip()
    }

    /**
     * The SESSION path (slice 8 final review, finding 8): an InstallZip entry that never passed through
     * `openInstallZip` -- a destination restored onto an uninitialised host after process death on Backup ->
     * InstallZip -- is initialised by `installZipSessionFor` when the destination composes. Navigating the
     * graph directly reaches only that init, so this is the one test that sees it.
     */
    @Test
    fun anInstallZipEntryThatBypassedTheOpenerInitialisesTheAppFromItsSession() = onAnUninitialisedBackupHost { activity ->
        nav(activity).navigate(NavRoutes.installZip(null, emptyList()))
    }

    private fun nav(activity: NavHostComposeActivity): NavHostController =
        NavHostComposeActivity::class.java.getDeclaredField("navController")
            .apply { isAccessible = true }.get(activity) as NavHostController

    private fun onAnUninitialisedBackupHost(enterInstallZip: (NavHostComposeActivity) -> Unit) {
        DatabaseContainer.instance
        val wasReady = DatabaseContainer.ready
        val wasInitialized = CommonUtils.initialized
        DatabaseContainer.ready = false
        CommonUtils.initialized = false
        val prefs = CommonUtils.realSharedPreferences
        val hadCalculator = prefs.getBoolean("show_calculator", false)
        prefs.edit().putBoolean("show_calculator", true).commit()
        runningTests = false
        // Without it, after the first NavHost host in this JVM the InstallZip destination never composes, so
        // `installZipSessionFor`'s initialisation had no effective test at all.
        resetComposeUiDispatcher()
        val controller = Robolectric.buildActivity(
            NavHostComposeActivity::class.java,
            NavHostComposeActivity.intentFor(ApplicationProvider.getApplicationContext(), NavRoutes.BACKUP),
        )
        try {
            controller.create().start().resume().visible()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
            val activity = controller.get()
            assertTrue("precondition: a BACKUP start does not initialise the app", activity.doNotInitializeApp)
            assertFalse("precondition: nothing initialised the app yet", CommonUtils.initialized)
            assertTrue(
                "precondition: an uninitialised start sets no FLAG_SECURE",
                (activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE) == 0,
            )

            enterInstallZip(activity)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))

            assertEquals(NavRoutes.INSTALL_ZIP_PATTERN, nav(activity).currentDestination?.route)
            assertTrue("InstallZip initialises the app, as the classic Activity's onCreate did", CommonUtils.initialized)
            assertTrue("…so the database is ready for the install", DatabaseContainer.ready)
            assertFalse("the host now answers as an initialised one", activity.doNotInitializeApp)
            assertTrue(
                "the calculator disguise's FLAG_SECURE is applied, as ActivityBase.onCreate does for an initialised start",
                (activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE) != 0,
            )
        } finally {
            controller.close()
            // The initialisation under test built the TTS managers against THIS test's Application; left in
            // CommonUtils, a later test's TestBibleApplication would reuse them and fail to unregister their
            // receivers in onTerminate ("Receiver not registered"). Tear them down with the app they belong to.
            if (CommonUtils.initialized) CommonUtils.destroy()
            runningTests = true
            DatabaseContainer.ready = wasReady
            CommonUtils.initialized = wasInitialized
            prefs.edit().putBoolean("show_calculator", hadCalculator).commit()
        }
    }

    companion object {
        /** Read by [NotRunningTestsApplication]; true except inside the test body. */
        @Volatile private var runningTests = true
    }
}

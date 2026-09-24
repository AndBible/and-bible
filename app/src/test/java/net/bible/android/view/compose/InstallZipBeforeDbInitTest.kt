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
import net.bible.android.BibleApplication
import net.bible.android.TEST_SDK
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.service.common.CommonUtils
import net.bible.service.db.DatabaseContainer
import net.bible.sharedcore.nav.NavRoutes
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
 * Slice 8 D1, controller ruling R4. A host started UNINITIALISED on BACKUP (the crash dialog's "Backup &
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

    @Test
    fun aBackupStartedHostInitialisesTheAppWhenItOpensInstallZip() {
        DatabaseContainer.instance
        val wasReady = DatabaseContainer.ready
        val wasInitialized = CommonUtils.initialized
        DatabaseContainer.ready = false
        CommonUtils.initialized = false
        runningTests = false
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

            activity.openInstallZip()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))

            val nav = NavHostComposeActivity::class.java.getDeclaredField("navController")
                .apply { isAccessible = true }.get(activity) as NavHostController
            assertEquals(NavRoutes.INSTALL_ZIP_PATTERN, nav.currentDestination?.route)
            assertTrue("InstallZip initialises the app, as the classic Activity's onCreate did", CommonUtils.initialized)
            assertTrue("…so the database is ready for the install", DatabaseContainer.ready)
            assertFalse("the host now answers as an initialised one", activity.doNotInitializeApp)
        } finally {
            controller.close()
            runningTests = true
            DatabaseContainer.ready = wasReady
            CommonUtils.initialized = wasInitialized
        }
    }

    companion object {
        /** Read by [NotRunningTestsApplication]; true except inside the test body. */
        @Volatile private var runningTests = true
    }
}

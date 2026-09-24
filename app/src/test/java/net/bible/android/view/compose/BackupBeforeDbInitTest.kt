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
import net.bible.test.resetComposeUiDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Slice 8 C3, plan Review Focus #1: "Backup & restore" from `ErrorReportControl.checkCrash`, which
 * `StartupActivity` runs BEFORE `initializeDatabase()` sets [DatabaseContainer.ready]. On a device,
 * [DatabaseContainer.instance] then throws `DataBaseNotReady`. Every other unit test hides that, because
 * `TestBibleApplication.isRunningTests` is true. This test's own [NotRunningTestsApplication] turns
 * `isRunningTests` off for the duration of the launch, so the real throw is live. It is test-side only;
 * production code gains no hook.
 *
 * Before C3's fix, the host crashed in `AbAppTheme` → `AndBibleSettings.getDisplayColorMode` →
 * the unguarded `getString`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = BackupBeforeDbInitTest.NotRunningTestsApplication::class, sdk = [TEST_SDK])
class BackupBeforeDbInitTest {

    class NotRunningTestsApplication : BibleApplication() {
        override val isRunningTests: Boolean get() = runningTests
    }

    @Test
    fun theHostStartsOnBackupWithoutTouchingTheDatabase() {
        val wasReady = DatabaseContainer.ready
        val wasInitialized = CommonUtils.initialized
        DatabaseContainer.ready = false
        CommonUtils.initialized = false
        runningTests = false
        // Without it, after the first NavHost host in this JVM the destination never recomposes, so
        // Backup's ON_RESUME load() -- the part of the screen that could touch the database -- never ran.
        resetComposeUiDispatcher()
        val controller = Robolectric.buildActivity(
            NavHostComposeActivity::class.java,
            NavHostComposeActivity.intentFor(ApplicationProvider.getApplicationContext(), NavRoutes.BACKUP),
        )
        try {
            controller.create().start().resume().visible()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
            val activity = controller.get()
            val nav = NavHostComposeActivity::class.java.getDeclaredField("navController")
                .apply { isAccessible = true }.get(activity) as NavHostController
            assertTrue("a BACKUP start must not initialise the app", activity.doNotInitializeApp)
            assertEquals(NavRoutes.BACKUP, nav.currentDestination?.route)
        } finally {
            // Tear down whatever state the launch reached (a failed assertion must not leak a live host),
            // still with the DB "not ready", which is what an on-device teardown would see.
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

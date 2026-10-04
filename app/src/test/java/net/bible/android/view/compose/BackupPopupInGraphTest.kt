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
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavHostController
import androidx.test.core.app.ApplicationProvider
import java.time.Duration
import kotlinx.coroutines.launch
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.backup.BackupControl
import net.bible.android.view.activity.base.firstTime
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedcore.reading.ReadingHostPresence
import net.bible.sharedcore.reading.ReadingViewVisibility
import net.bible.test.DatabaseResetter
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

/**
 * Slice 8 C3 (plan Correction 4): `BackupControl.backupPopup` on the nav host navigates IN-GRAPH and
 * suspends until the Backup entry leaves the back stack. `ErrorReportControl.showErrorDialog`'s loop
 * re-shows its dialog after `backupPopup` returns; an F53-shaped self-launch would return at once
 * (synthetic cancel) and put the dialog back over the Backup screen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class BackupPopupInGraphTest {

    private val controllers = mutableListOf<ActivityController<NavHostComposeActivity>>()

    @After
    fun tearDown() {
        controllers.forEach { it.close() }
        controllers.clear()
        ReadingHostPresence.setForeground(null)
        ReadingViewVisibility.setVisible(false)
        DatabaseResetter.resetDatabase()
    }

    @Test
    fun backupPopupOnTheHostNavigatesAndReturnsOnlyWhenBackupIsLeft() {
        firstTime = false
        val activity = Robolectric.buildActivity(
            NavHostComposeActivity::class.java,
            NavHostComposeActivity.intentFor(ApplicationProvider.getApplicationContext(), NavRoutes.READING),
        ).also { controllers += it }.create().start().resume().visible().get()
        val nav = NavHostComposeActivity::class.java.getDeclaredField("navController")
            .apply { isAccessible = true }.get(activity) as NavHostController
        var returned = false

        activity.lifecycleScope.launch { BackupControl.backupPopup(activity); returned = true }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))

        assertNull("no platform launch: Backup is a destination of this host", shadowOf(activity).nextStartedActivityForResult)
        assertEquals(NavRoutes.BACKUP, nav.currentDestination?.route)
        assertFalse("backupPopup must still be suspended while Backup is on screen", returned)

        nav.popBackStack()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        assertTrue("…and return once the user has left it", returned)
    }

    /**
     * Review minor 1: "Backup & restore" chosen from an error dialog raised INSIDE Backup. Classic pushed a
     * second Backup screen and re-showed the dialog over it; in-graph, a `launchSingleTop` navigate is a
     * no-op, so waiting for Backup to leave would put the dialog back only once the user reached reading.
     * `backupPopup` must return at once, leaving Backup where it is.
     */
    @Test
    fun backupPopupWhileBackupIsAlreadyCurrentReturnsAtOnce() {
        firstTime = false
        val activity = Robolectric.buildActivity(
            NavHostComposeActivity::class.java,
            NavHostComposeActivity.intentFor(ApplicationProvider.getApplicationContext(), NavRoutes.READING),
        ).also { controllers += it }.create().start().resume().visible().get()
        val nav = NavHostComposeActivity::class.java.getDeclaredField("navController")
            .apply { isAccessible = true }.get(activity) as NavHostController
        nav.navigate(NavRoutes.BACKUP)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        assertEquals(NavRoutes.BACKUP, nav.currentDestination?.route)
        var returned = false

        activity.lifecycleScope.launch { BackupControl.backupPopup(activity); returned = true }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))

        assertTrue("backupPopup must return at once when Backup is already on screen", returned)
        assertEquals(NavRoutes.BACKUP, nav.currentDestination?.route)
        assertEquals(
            "exactly one Backup entry",
            1, nav.currentBackStack.value.count { it.destination.route == NavRoutes.BACKUP },
        )
    }
}

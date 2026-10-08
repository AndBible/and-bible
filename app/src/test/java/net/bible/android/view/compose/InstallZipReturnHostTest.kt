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
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.view.activity.base.firstTime
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.sharedcore.nav.InstallZipResult
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedcore.reading.ReadingHostPresence
import net.bible.sharedcore.reading.ReadingViewVisibility
import net.bible.test.DatabaseResetter
import net.bible.test.resetComposeUiDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/**
 * Slice 8 D1 fix round 2 (review minor 2): `openInstallZip`'s `onResult` belongs to the InstallZip entry it
 * opened. If that entry leaves without answering (popped from outside), the callback must not survive to
 * answer for a LATER InstallZip entry that nobody opened through `openInstallZip` (a restored one, or a
 * redirect's start).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class InstallZipReturnHostTest {

    private val controllers = mutableListOf<ActivityController<NavHostComposeActivity>>()

    @After
    fun tearDown() {
        controllers.forEach { it.close() }
        controllers.clear()
        ReadingHostPresence.setForeground(null)
        ReadingViewVisibility.setVisible(false)
        DatabaseResetter.resetDatabase()
    }

    private fun host(): Pair<NavHostComposeActivity, NavHostController> {
        // This test needs the destination COMPOSED (its BackHandler), not only its route: see
        // resetComposeUiDispatcher for what an earlier host test leaves behind in the same JVM.
        resetComposeUiDispatcher()
        firstTime = false
        val activity = Robolectric.buildActivity(
            NavHostComposeActivity::class.java,
            NavHostComposeActivity.intentFor(ApplicationProvider.getApplicationContext(), NavRoutes.BACKUP),
        ).also { controllers += it }.create().start().resume().visible().get()
        val nav = NavHostComposeActivity::class.java.getDeclaredField("navController")
            .apply { isAccessible = true }.get(activity) as NavHostController
        return activity to nav
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))

    @Test
    fun anOpenersCallbackAnswersForItsOwnEntryOnly() {
        val (activity, nav) = host()

        val answers = mutableListOf<InstallZipResult>()
        activity.openInstallZip { answers += it }
        idle()
        assertEquals(NavRoutes.INSTALL_ZIP_PATTERN, nav.currentDestination?.route)
        activity.onBackPressedDispatcher.onBackPressed()
        idle()
        assertEquals(NavRoutes.BACKUP, nav.currentDestination?.route)
        assertEquals("the opener's callback gets its entry's answer", listOf(InstallZipResult.CANCELED), answers)

        val orphaned = mutableListOf<InstallZipResult>()
        activity.openInstallZip { orphaned += it }
        idle()
        nav.popBackStack() // from outside: the entry leaves without answering
        idle()
        assertEquals(NavRoutes.BACKUP, nav.currentDestination?.route)

        nav.navigate(NavRoutes.installZip()) // an entry nobody opened through openInstallZip
        idle()
        activity.onBackPressedDispatcher.onBackPressed()
        idle()
        assertEquals(NavRoutes.BACKUP, nav.currentDestination?.route)
        assertEquals("a callback whose entry left unanswered answers nothing later", emptyList<InstallZipResult>(), orphaned)
        assertEquals(listOf(InstallZipResult.CANCELED), answers)
    }
}

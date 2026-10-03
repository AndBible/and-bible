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
import androidx.test.core.app.ApplicationProvider
import net.bible.android.TestBibleApplication
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Fix batch 5 F107: on `reading` the bands beside a side nav bar show the pane colour, so the system's
 * translucent nav-bar contrast scrim must be off there; every other screen keeps it.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestBibleApplication::class, sdk = [35])
class NavBarContrastOnReadingTest {
    private val controllers = mutableListOf<ActivityController<NavHostComposeActivity>>()

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

    private fun idleMain() = shadowOf(Looper.getMainLooper()).idle()

    @Test
    fun contrastIsOffOnReadingAndBackOnElsewhere() {
        val controller = Robolectric.buildActivity(
            NavHostComposeActivity::class.java,
            NavHostComposeActivity.intentFor(ApplicationProvider.getApplicationContext(), NavRoutes.READING),
        ).also { controllers += it }.create().start().resume().visible()
        val activity = controller.get()
        idleMain()
        assertFalse(activity.window.isNavigationBarContrastEnforced, "on reading the scrim must be off")

        activity.navigateInGraph(NavRoutes.SETTINGS)
        idleMain()
        assertTrue(activity.window.isNavigationBarContrastEnforced, "off reading the scrim is back")

        activity.navigateInGraph(NavRoutes.READING)
        idleMain()
        assertFalse(activity.window.isNavigationBarContrastEnforced, "and off again on return")
    }
}

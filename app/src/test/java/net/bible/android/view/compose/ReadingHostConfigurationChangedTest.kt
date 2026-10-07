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

import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import net.bible.android.TestBibleApplication
import net.bible.android.control.page.window.WindowChange
import net.bible.android.control.page.window.WindowStateServiceImpl
import net.bible.android.view.activity.base.firstTime
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.sharedcore.nav.NavRoutes
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/**
 * Fix batch 2 §2.3 (secondary). The nav host declares `orientation` in `configChanges` (F65), so it is
 * not recreated on rotation, and BibleView only re-derives `isSplitVertically` / re-sends its pane
 * offsets on a `WindowChange.LayoutConfigurationChanged`. Classic never posted one on rotation because it was
 * recreated instead.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class)
class ReadingHostConfigurationChangedTest {
    private val hostControllers = mutableListOf<ActivityController<NavHostComposeActivity>>()

    private fun hostController(route: String): ActivityController<NavHostComposeActivity> {
        firstTime = false
        return Robolectric.buildActivity(
            NavHostComposeActivity::class.java,
            NavHostComposeActivity.intentFor(ApplicationProvider.getApplicationContext(), route),
        ).also { hostControllers += it }.setup()
    }

    @After
    fun tearDown() {
        hostControllers.forEach { it.close() }
        hostControllers.clear()
    }

    @Test fun aConfigurationChangeIsBroadcastToTheReadingPanes() {
        val controller = hostController(NavRoutes.READING)
        var seen = 0
        val subscription = GlobalContext.get().get<WindowStateServiceImpl>().windowChanges.subscribe {
            if (it == WindowChange.LayoutConfigurationChanged) seen++
        }
        try {
            val landscape = Configuration(controller.get().resources.configuration).apply {
                orientation = Configuration.ORIENTATION_LANDSCAPE
            }
            controller.configurationChange(landscape)
            assertEquals("a rotation must reach BibleView (it re-sends its pane offsets on it)", 1, seen)
        } finally {
            subscription.cancel()
        }
    }
}

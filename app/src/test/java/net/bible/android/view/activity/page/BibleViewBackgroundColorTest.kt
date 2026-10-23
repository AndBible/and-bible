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
package net.bible.android.view.activity.page

import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.page.window.WindowRepository
import net.bible.android.database.WorkspaceEntities.TextDisplaySettings
import net.bible.android.view.util.UiUtils
import net.bible.service.common.CommonUtils
import net.bible.test.DatabaseResetter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [bibleViewBackgroundColorFor] delegates to the shared `readingBackgroundArgb`; the values Android paints
 * (and the Roborazzi goldens depend on) must not move: the window's colours, black/white in monochrome mode,
 * and the day/night defaults of [UiUtils.bibleViewDefaultBackgroundColor].
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class BibleViewBackgroundColorTest {
    private lateinit var repository: WindowRepository

    @Before
    fun setUp() {
        repository = net.bible.test.testWindowRepository()
        repository.initializeAtEdge()
    }

    @After
    fun tearDown() {
        CommonUtils.settings.removeString("display_color_mode")
        CommonUtils.realSharedPreferences.edit().remove("night_mode_pref").remove("night_mode_pref3").apply()
        DatabaseResetter.resetDatabase(repository.scope)
    }

    private fun setNight(night: Boolean) {
        CommonUtils.realSharedPreferences.edit().putString("night_mode_pref3", "manual").putBoolean("night_mode_pref", night).apply()
    }

    private fun withColours(day: Int, night: Int) = repository.activeWindow.also {
        it.pageManager.textDisplaySettings = TextDisplaySettings(
            colors = TextDisplaySettings.default.colors!!.copy(dayBackground = day, nightBackground = night),
        )
    }

    @Test
    fun theWindowColoursDecideByDayAndNight() {
        val window = withColours(day = 0xFF112233.toInt(), night = 0xFF445566.toInt())
        setNight(false)
        assertEquals(0xFF112233.toInt(), bibleViewBackgroundColorFor(window))
        setNight(true)
        assertEquals(0xFF445566.toInt(), bibleViewBackgroundColorFor(window))
    }

    @Test
    fun monochromeForcesWhiteByDayAndBlackAtNight() {
        val window = withColours(day = 0xFF112233.toInt(), night = 0xFF445566.toInt())
        CommonUtils.settings.setString("display_color_mode", "bw")
        setNight(false)
        assertEquals(-1, bibleViewBackgroundColorFor(window))
        setNight(true)
        assertEquals(-16777216, bibleViewBackgroundColorFor(window))
    }

    @Test
    fun theDefaultsAreTheOldBibleBackgroundResources() {
        setNight(false)
        assertEquals(CommonUtils.getResourceColor(net.bible.android.activity.R.color.bible_background_day), UiUtils.bibleViewDefaultBackgroundColor)
        setNight(true)
        assertEquals(CommonUtils.getResourceColor(net.bible.android.activity.R.color.bible_background_night), UiUtils.bibleViewDefaultBackgroundColor)
        // A window with the stock colours paints exactly the defaults.
        val window = repository.activeWindow
        assertEquals(UiUtils.bibleViewDefaultBackgroundColor, bibleViewBackgroundColorFor(window))
    }
}

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
package net.bible.sharedui.desktop.reading

import net.bible.android.view.activity.page.SwipeAction
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedui.host.reading.HostReadingGestures
import net.bible.sharedui.reading.host.ReadingFullScreen
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class DesktopReadingGesturesTest {
    private class MemorySettings : AppSettings {
        private val values = mutableMapOf<String, Any?>()
        override fun getString(key: String, default: String?) = values["String:$key"] as? String ?: default
        override fun getLong(key: String, default: Long) = values["Long:$key"] as? Long ?: default
        override fun getInt(key: String, default: Int) = values["Int:$key"] as? Int ?: default
        override fun getBoolean(key: String, default: Boolean) = values["Boolean:$key"] as? Boolean ?: default
        override fun getDouble(key: String, default: Double) = values["Double:$key"] as? Double ?: default
        override fun getFloat(key: String, default: Float) = values["Float:$key"] as? Float ?: default
        override fun setString(key: String, value: String?) { values["String:$key"] = value }
        override fun setLong(key: String, value: Long?) { values["Long:$key"] = value }
        override fun setInt(key: String, value: Int?) { values["Int:$key"] = value }
        override fun setBoolean(key: String, value: Boolean?) { values["Boolean:$key"] = value }
        override fun setDouble(key: String, value: Double?) { values["Double:$key"] = value }
        override fun setFloat(key: String, value: Float?) { values["Float:$key"] = value }
    }

    private val fullScreen = ReadingFullScreen()
    private val settings = MemorySettings()
    private val swipes = mutableListOf<Pair<String, SwipeAction>>()
    private var touched = 0
    private val real = HostReadingGestures(fullScreen, settings, { false }, { id, a -> swipes += id to a }, onPaneTouched = { touched++ })
    private val gestures = DesktopReadingGestures({ real })

    @Test fun fractionalWheelDeltasAccumulate() {
        settings.setBoolean("auto_fullscreen_pref", true)
        // 0.4 px per frame would round to nothing; 200 frames in under a second (the gesture rules cap accumulation to a rolling second) are 80 px, past the 56 point threshold.
        for (i in 0 until 200) gestures.onWheel("w1", 0.0, 0.4, 1000L + i * 4L)
        assertTrue(fullScreen.state.value)
    }

    @Test fun smallWheelDeltasBelowTheThresholdDoNothing() {
        settings.setBoolean("auto_fullscreen_pref", true)
        for (i in 0 until 20) gestures.onWheel("w1", 0.0, 0.4, 1000L + i * 4L)
        assertFalse(fullScreen.state.value)
    }

    @Test fun horizontalDeltaDoesNotSwitchWindow() {
        // Only the swipe path is used for a horizontal gesture; it fires at most once per gesture and never focuses.
        for (i in 0 until 20) gestures.onWheel("w1", 20.0, 0.0, 1000L + i * 16L)
        assertEquals(1, swipes.size)
        assertEquals("w1", swipes[0].first)
        // A new gesture after a pause may swipe again.
        for (i in 0 until 20) gestures.onWheel("w1", 20.0, 0.0, 5000L + i * 16L)
        assertEquals(2, swipes.size)
    }

    @Test fun smallOrMostlyVerticalHorizontalDriftDoesNotSwipe() {
        for (i in 0 until 5) gestures.onWheel("w1", 20.0, 0.0, 1000L + i * 16L) // 100 px < 150
        for (i in 0 until 20) gestures.onWheel("w2", 20.0, 60.0, 2000L + i * 16L) // mostly vertical
        assertEquals(emptyList(), swipes)
    }

    @Test fun swipeDirectionFollowsTheDeltaSign() {
        for (i in 0 until 20) gestures.onWheel("w1", 20.0, 0.0, 1000L + i * 16L)
        for (i in 0 until 20) gestures.onWheel("w1", -20.0, 0.0, 5000L + i * 16L)
        assertEquals(2, swipes.size)
        assertNotEquals(swipes[0].second, swipes[1].second)
    }

    @Test fun clickOnVerseSelectsAsTapDoesOnIos() {
        // The click is only reported as a touch; nothing is consumed, and the script never cancels a click.
        gestures.onClick("w1")
        assertEquals(1, touched)
        assertFalse(DesktopInputScript.CLICK_PART.contains("preventDefault"))
        assertFalse(DesktopInputScript.WHEEL_PART.contains("preventDefault"))
        assertTrue(DesktopInputScript.WHEEL_PART.contains("passive"))
    }

    @Test fun noGesturesBoundIsHarmless() {
        val none = DesktopReadingGestures({ null })
        none.onWheel("w1", 1.0, 1.0, 1L)
        none.onClick("w1")
    }
}

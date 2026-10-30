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
package net.bible.sharedui.ios.reading

import net.bible.android.view.activity.page.SwipeAction
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedui.reading.host.ReadingFullScreen
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.bible.sharedui.host.reading.HostReadingGestures

/** [HostReadingGestures]: the same decisions as Android's `BibleGestureListener`, without UIKit. */
class IosReadingGesturesTest {
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
    private var rtl = false
    private val gestures = HostReadingGestures(fullScreen, settings, { rtl }, { id, action -> swipes += id to action })

    /** Scrolls forward (finger up) in steps; the first step of a fresh tracker is spent by the direction rule. */
    private fun scrollForward(steps: Int, stepPoints: Float = 30f, startMillis: Long = 100L) {
        for (i in 0 until steps) gestures.onScroll(-stepPoints, startMillis + i * 10L)
    }

    @Test fun touchesAndScrollStartsReportPaneTouched() {
        var touched = 0
        val g = HostReadingGestures(fullScreen, settings, { false }, { _, _ -> }, onPaneTouched = { touched++ })
        g.onTouched()
        assertEquals(1, touched)
        g.onScrollGestureBegan(10L)
        assertEquals(2, touched)
        g.onScroll(-30f, 20L)
        assertEquals(2, touched)
    }

    @Test fun onlyActualScrollMovementReportsPaneScrolled() {
        var scrolled = 0
        val g = HostReadingGestures(fullScreen, settings, { false }, { _, _ -> }, onPaneScrolled = { scrolled++ })
        g.onTouched()
        g.onScrollGestureBegan(10L)
        g.onScroll(0f, 20L)
        assertEquals(0, scrolled)
        g.onScroll(-30f, 30L)
        g.onScroll(15f, 40L)
        assertEquals(2, scrolled)
    }

    @Test fun doubleTapEntersAndLocks() {
        gestures.onDoubleTap()
        assertTrue(fullScreen.state.value)
        assertTrue(fullScreen.lockedByDoubleTap)
    }

    @Test fun doubleTapLeavesFullScreen() {
        fullScreen.set(true)
        gestures.onDoubleTap()
        assertFalse(fullScreen.state.value)
        assertFalse(fullScreen.lockedByDoubleTap)
    }

    @Test fun doubleTapLeavesEvenWhenDisabled() {
        // Android: the setting only gates entering.
        settings.setBoolean("double_tap_to_fullscreen", false)
        fullScreen.set(true)
        gestures.onDoubleTap()
        assertFalse(fullScreen.state.value)
    }

    @Test fun doubleTapIgnoredWhenDisabled() {
        settings.setBoolean("double_tap_to_fullscreen", false)
        gestures.onDoubleTap()
        assertFalse(fullScreen.state.value)
    }

    @Test fun upwardScrollPastThresholdEntersWhenAutoEnabled() {
        settings.setBoolean("auto_fullscreen_pref", true)
        scrollForward(2) // spent + -30: below 56
        assertFalse(fullScreen.state.value)
        scrollForward(1, startMillis = 130L) // -60: past the threshold
        assertTrue(fullScreen.state.value)
        assertFalse(fullScreen.lockedByDoubleTap)
    }

    @Test fun upwardScrollDoesNothingWhenAutoDisabled() {
        scrollForward(6)
        assertFalse(fullScreen.state.value)
    }

    @Test fun downwardScrollLeavesAnAutoFullScreen() {
        settings.setBoolean("auto_fullscreen_pref", true)
        scrollForward(3)
        assertTrue(fullScreen.state.value)
        for (i in 0 until 4) gestures.onScroll(30f, 200L + i * 10L)
        assertFalse(fullScreen.state.value)
    }

    @Test fun autoScrollDoesNotExitADoubleTapFullScreen() {
        settings.setBoolean("auto_fullscreen_pref", true)
        gestures.onDoubleTap()
        for (i in 0 until 8) gestures.onScroll(30f, 200L + i * 10L)
        assertTrue(fullScreen.state.value)
    }

    @Test fun fullScreenChangesGoThroughTheHostFunction() {
        val applied = mutableListOf<Pair<Boolean, Boolean>>()
        val g = HostReadingGestures(fullScreen, settings, { false }, { _, _ -> }) { v, d -> applied += v to d; fullScreen.set(v, d) }
        g.onDoubleTap()
        assertEquals(listOf(true to true), applied)
    }

    @Test fun swipeRoutesToTheSwipedWindow() {
        gestures.onSwipe("w2", towardsNext = true)
        gestures.onSwipe("w3", towardsNext = false)
        assertEquals(listOf("w2" to SwipeAction.NextChapter, "w3" to SwipeAction.PreviousChapter), swipes)
    }

    @Test fun swipeFollowsTheModeAndRtl() {
        settings.setString("bible_view_swipe_mode", "PAGE")
        rtl = true
        gestures.onSwipe("w1", towardsNext = true)
        settings.setString("bible_view_swipe_mode", "NONE")
        gestures.onSwipe("w1", towardsNext = true)
        settings.setString("bible_view_swipe_mode", "garbage")
        gestures.onSwipe("w1", towardsNext = false)
        assertEquals(listOf("w1" to SwipeAction.PageUp, "w1" to SwipeAction.NextChapter), swipes)
    }
}

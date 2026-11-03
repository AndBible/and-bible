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

import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedui.host.reading.HostReadingGestures
import net.bible.sharedui.reading.host.ReadingFullScreen
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A press on a pane's web view activates that pane's window (iOS on a Mac: a click is not always seen by the Compose
 * tap detector behind the interop view, so the native side reports it too).
 */
class HostReadingGesturesActivationTest {
    private object NoSettings : AppSettings {
        override fun getString(key: String, default: String?) = default
        override fun getLong(key: String, default: Long) = default
        override fun getInt(key: String, default: Int) = default
        override fun getBoolean(key: String, default: Boolean) = default
        override fun getDouble(key: String, default: Double) = default
        override fun getFloat(key: String, default: Float) = default
        override fun setString(key: String, value: String?) {}
        override fun setLong(key: String, value: Long?) {}
        override fun setInt(key: String, value: Int?) {}
        override fun setBoolean(key: String, value: Boolean?) {}
        override fun setDouble(key: String, value: Double?) {}
        override fun setFloat(key: String, value: Float?) {}
    }

    private val activated = mutableListOf<String>()
    private var touched = 0
    private val gestures = HostReadingGestures(
        ReadingFullScreen(), NoSettings, { false }, { _, _ -> },
        onPaneTouched = { touched++ }, onPaneActivated = { activated += it },
    )

    @Test fun aPressActivatesTheWindowItWasOn() {
        gestures.onPanePressed("win-2", "tap")
        assertEquals(listOf("win-2"), activated)
    }

    @Test fun eachPaneReportsItsOwnId() {
        gestures.onPanePressed("a", "touch-down")
        gestures.onPanePressed("b", "touch-down")
        assertEquals(listOf("a", "b"), activated)
    }

    @Test fun aPressAlsoCountsAsATouchForTheWindowButtons() {
        gestures.onPanePressed("a", "tap")
        assertEquals(1, touched)
    }
}

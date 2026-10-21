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
package net.bible.sharedui.reading.host

import net.bible.sharedcore.platform.AppSettings
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The window-button rail gate: hidden by `hide_window_buttons` or a maximised window, shown otherwise. */
class ReadingPaneButtonsTest {
    private class MapSettings : AppSettings {
        val values = mutableMapOf<String, Any>()
        override fun getString(key: String, default: String?): String? = values[key] as? String ?: default
        override fun getLong(key: String, default: Long): Long = values[key] as? Long ?: default
        override fun getInt(key: String, default: Int): Int = values[key] as? Int ?: default
        override fun getBoolean(key: String, default: Boolean): Boolean = values[key] as? Boolean ?: default
        override fun getDouble(key: String, default: Double): Double = values[key] as? Double ?: default
        override fun getFloat(key: String, default: Float): Float = values[key] as? Float ?: default
        override fun setString(key: String, value: String?) = put(key, value)
        override fun setLong(key: String, value: Long?) = put(key, value)
        override fun setInt(key: String, value: Int?) = put(key, value)
        override fun setBoolean(key: String, value: Boolean?) = put(key, value)
        override fun setDouble(key: String, value: Double?) = put(key, value)
        override fun setFloat(key: String, value: Float?) = put(key, value)
        private fun put(key: String, value: Any?) {
            if (value == null) values.remove(key) else values[key] = value
        }
    }

    @Test
    fun unsetPrefShowsTheRailWhenNotMaximised() {
        assertTrue(paneButtonsShown(MapSettings(), maximised = false))
    }

    @Test
    fun hidePrefHidesTheRail() {
        val settings = MapSettings().apply { setBoolean("hide_window_buttons", true) }
        assertFalse(paneButtonsShown(settings, maximised = false))
    }

    @Test
    fun explicitFalsePrefShowsTheRail() {
        val settings = MapSettings().apply { setBoolean("hide_window_buttons", false) }
        assertTrue(paneButtonsShown(settings, maximised = false))
    }

    @Test
    fun maximisedWindowHidesTheRailWhateverThePref() {
        assertFalse(paneButtonsShown(MapSettings(), maximised = true))
        assertFalse(paneButtonsShown(MapSettings().apply { setBoolean("hide_window_buttons", false) }, maximised = true))
    }
}

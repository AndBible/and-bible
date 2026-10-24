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
package net.bible.sharedlogic.platform

/**
 * The one decision both platforms make from the `night_mode_pref3` mode: `"system"` follows [systemDark], `"automatic"`
 * follows the light-sensor decision [autoReading] when there is one (else the system), and every other value, `"manual"`
 * included, reads the manual preference [manualNight] (Android's `else` branch).
 *
 * Whether `"automatic"` is available at all is the caller's business: Android passes `"manual"` for a stored
 * `"automatic"` on a device without a light sensor, exactly as before.
 */
fun resolveNightMode(mode: String, manualNight: Boolean, systemDark: Boolean, autoReading: Boolean?): Boolean =
    when (mode) {
        "system" -> systemDark
        "automatic" -> autoReading ?: systemDark
        else -> manualNight
    }

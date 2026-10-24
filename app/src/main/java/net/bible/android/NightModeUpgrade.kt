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
package net.bible.android

import android.content.SharedPreferences

/** Marker: the one-time night mode default check of [keepNightModeOnUpgrade] has run. */
internal const val NIGHT_MODE_DEFAULT_CHECKED_KEY = "night_mode_default_checked"

/**
 * The default night mode changed from Manual to System. Once, on the first run of a version that has this check,
 * an upgrading install with no stored `night_mode_pref3` gets `"manual"` written so it keeps what it had;
 * a fresh install is left without a value and so follows the system. Committed synchronously because it runs
 * before the stored version is written.
 */
internal fun keepNightModeOnUpgrade(prefs: SharedPreferences, newInstall: Boolean) {
    if(prefs.getBoolean(NIGHT_MODE_DEFAULT_CHECKED_KEY, false)) return
    prefs.edit().apply {
        if(!newInstall && !prefs.contains("night_mode_pref3")) putString("night_mode_pref3", "manual")
        putBoolean(NIGHT_MODE_DEFAULT_CHECKED_KEY, true)
    }.commit()
}

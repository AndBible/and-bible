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
package net.bible.sharedcore.platform

import platform.Foundation.NSNumber
import platform.Foundation.NSUserDefaults

/**
 * Device-local preferences (Android's `SharedPreferences`, spec C3): the standard `NSUserDefaults` domain under
 * Android's key names (`discrete_mode`, `show_calculator`, `calculator_pin`, `locale_pref`, ...). They are read
 * before the database exists, so they cannot live in the settings store.
 */
class IosDeviceSettings(private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults) {
    fun getString(key: String): String? = defaults.stringForKey(key)

    /** A null [value] removes the key. */
    fun putString(key: String, value: String?) {
        if (value == null) defaults.removeObjectForKey(key) else defaults.setObject(value, forKey = key)
    }

    fun getBoolean(key: String, default: Boolean): Boolean =
        if (defaults.objectForKey(key) == null) default else defaults.boolForKey(key)

    fun putBoolean(key: String, value: Boolean) = defaults.setBool(value, forKey = key)

    fun getInt(key: String, default: Int): Int = (defaults.objectForKey(key) as? NSNumber)?.intValue ?: default

    fun putInt(key: String, value: Int) = defaults.setInteger(value.toLong(), forKey = key)
}

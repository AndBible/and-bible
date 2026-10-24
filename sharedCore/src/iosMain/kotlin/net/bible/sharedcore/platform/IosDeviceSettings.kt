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
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSUserDefaultsDidChangeNotification

/**
 * Device-local preferences (Android's `SharedPreferences`, spec C3): the standard `NSUserDefaults` domain under
 * Android's key names (`discrete_mode`, `show_calculator`, `calculator_pin`, `locale_pref`, ...). They are read
 * before the database exists, so they cannot live in the settings store.
 */
class IosDeviceSettings(private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults) : net.bible.sharedcore.settings.DeviceLocalPrefs {
    override fun getString(key: String, default: String?): String? = getString(key) ?: default
    override fun setString(key: String, value: String) = putString(key, value)
    override fun setBoolean(key: String, value: Boolean) = putBoolean(key, value)
    override fun remove(key: String) = defaults.removeObjectForKey(key)
    fun getString(key: String): String? = defaults.stringForKey(key)

    /** A null [value] removes the key. */
    fun putString(key: String, value: String?) {
        if (value == null) defaults.removeObjectForKey(key) else defaults.setObject(value, forKey = key)
    }

    override fun getBoolean(key: String, default: Boolean): Boolean =
        if (defaults.objectForKey(key) == null) default else defaults.boolForKey(key)

    fun putBoolean(key: String, value: Boolean) {
        defaults.setBool(value, forKey = key)
        NSNotificationCenter.defaultCenter.postNotificationName(NSUserDefaultsDidChangeNotification, defaults)
    }

    /** Observes this preference domain across IosDeviceSettings writers; cancel when the host is disposed. */
    fun observeBoolean(key: String, default: Boolean, onChange: (Boolean) -> Unit): () -> Unit {
        var previous = getBoolean(key, default)
        val observer = {
            val value = getBoolean(key, default)
            if (value != previous) {
                previous = value
                onChange(value)
            }
        }
        val center = NSNotificationCenter.defaultCenter
        val token = center.addObserverForName(NSUserDefaultsDidChangeNotification, null, null) { observer() }
        onChange(previous)
        return { center.removeObserver(token) }
    }

    fun getLong(key: String, default: Long): Long = (defaults.objectForKey(key) as? NSNumber)?.longLongValue ?: default

    fun putLong(key: String, value: Long) = defaults.setObject(NSNumber(longLong = value), forKey = key)

    fun getInt(key: String, default: Int): Int = (defaults.objectForKey(key) as? NSNumber)?.intValue ?: default

    fun putInt(key: String, value: Int) = defaults.setInteger(value.toLong(), forKey = key)
}

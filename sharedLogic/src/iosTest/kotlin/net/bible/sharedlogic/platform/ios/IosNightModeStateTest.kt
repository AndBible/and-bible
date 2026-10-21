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
package net.bible.sharedlogic.platform.ios

import net.bible.sharedcore.platform.IosDeviceSettings
import platform.Foundation.NSUUID
import platform.Foundation.NSUserDefaults
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IosNightModeStateTest {
    private val suites = mutableListOf<String>()

    private fun suite(): NSUserDefaults {
        val name = "night-mode-" + NSUUID().UUIDString
        suites += name
        return NSUserDefaults(suiteName = name)
    }

    @AfterTest fun cleanUp() {
        suites.forEach { NSUserDefaults.standardUserDefaults.removePersistentDomainForName(it) }
        suites.clear()
    }

    @Test fun absentPrefFollowsSystem() {
        val defaults = suite()
        var system = false
        val state = IosNightModeState(IosDeviceSettings(defaults)) { system }
        assertNull(defaults.stringForKey("night_mode_pref3"), "nothing wrote a default")
        assertEquals("system", state.mode)
        assertFalse(state.nightMode)
        system = true
        assertTrue(state.nightMode, "live: the system appearance is read on every access")
        defaults.setBool(false, forKey = "night_mode_pref") // the manual Boolean is ignored in system mode
        assertTrue(state.nightMode)
        assertNull(defaults.stringForKey("night_mode_pref3"), "reading does not write a default either")
    }

    @Test fun explicitSystemFollowsTheSystem() {
        val defaults = suite()
        defaults.setObject("system", forKey = "night_mode_pref3")
        defaults.setBool(true, forKey = "night_mode_pref")
        assertFalse(IosNightModeState(IosDeviceSettings(defaults)) { false }.nightMode)
    }

    @Test fun manualReadsTheBoolean() {
        val defaults = suite()
        defaults.setObject("manual", forKey = "night_mode_pref3")
        val state = IosNightModeState(IosDeviceSettings(defaults)) { true }
        assertFalse(state.nightMode, "manual with the Boolean unset is light, whatever the system says")
        defaults.setBool(true, forKey = "night_mode_pref")
        assertTrue(state.nightMode)
        defaults.setBool(false, forKey = "night_mode_pref")
        assertFalse(state.nightMode)
    }

    @Test fun automaticFallsBackToSystem() {
        val defaults = suite()
        defaults.setObject("automatic", forKey = "night_mode_pref3")
        defaults.setBool(true, forKey = "night_mode_pref")
        assertTrue(IosNightModeState(IosDeviceSettings(defaults)) { true }.nightMode)
        assertFalse(IosNightModeState(IosDeviceSettings(defaults)) { false }.nightMode)
    }
}

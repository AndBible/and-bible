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

import platform.Foundation.NSUUID
import platform.Foundation.NSUserDefaults
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IosDeviceSettingsTest {
    private val suite = "org.andbible.test.${NSUUID().UUIDString}"
    private val defaults = NSUserDefaults(suiteName = suite)
    private val settings = IosDeviceSettings(defaults)

    @AfterTest
    fun tearDown() { defaults.removePersistentDomainForName(suite) }

    @Test
    fun stringRoundTripsAndNullRemoves() {
        assertNull(settings.getString("locale_pref"))
        settings.putString("locale_pref", "fi")
        assertEquals("fi", settings.getString("locale_pref"))
        settings.putString("locale_pref", null)
        assertNull(settings.getString("locale_pref"))
    }

    @Test
    fun booleanAnswersTheDefaultUntilSetAndKeepsAnExplicitFalse() {
        assertEquals(true, settings.getBoolean("discrete_mode", true))
        assertEquals(false, settings.getBoolean("discrete_mode", false))
        settings.putBoolean("discrete_mode", false)
        assertEquals(false, settings.getBoolean("discrete_mode", true))
        settings.putBoolean("discrete_mode", true)
        assertEquals(true, settings.getBoolean("discrete_mode", false))
    }

    @Test
    fun intRoundTripsIncludingNegativesAndZero() {
        assertEquals(7, settings.getInt("calculator_pin_length", 7))
        settings.putInt("calculator_pin_length", 4)
        assertEquals(4, settings.getInt("calculator_pin_length", 7))
        settings.putInt("calculator_pin_length", 0)
        assertEquals(0, settings.getInt("calculator_pin_length", 7))
        settings.putInt("calculator_pin_length", -3)
        assertEquals(-3, settings.getInt("calculator_pin_length", 7))
    }

    @Test
    fun keysAreIndependentAcrossTypes() {
        settings.putString("a", "x")
        assertEquals(5, settings.getInt("b", 5))
        assertNull(settings.getString("b"))
    }
}

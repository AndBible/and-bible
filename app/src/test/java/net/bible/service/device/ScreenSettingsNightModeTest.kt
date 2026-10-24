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
package net.bible.service.device

import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.service.common.CommonUtils
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** `ScreenSettings.nightMode` over every stored mode and system appearance (it delegates to the common resolver). */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class ScreenSettingsNightModeTest {
    private val prefs get() = CommonUtils.realSharedPreferences

    @After
    fun tearDown() {
        prefs.edit().remove("night_mode_pref").remove("night_mode_pref3").commit()
        RuntimeEnvironment.setQualifiers("+notnight")
    }

    private fun set(mode: String?, manual: Boolean?, systemDark: Boolean) {
        prefs.edit().apply {
            if(mode == null) remove("night_mode_pref3") else putString("night_mode_pref3", mode)
            if(manual == null) remove("night_mode_pref") else putBoolean("night_mode_pref", manual)
        }.commit()
        RuntimeEnvironment.setQualifiers(if(systemDark) "+night" else "+notnight")
    }

    @Test
    fun absentModeFollowsTheSystem() {
        set(null, true, systemDark = false)
        assertEquals(false, ScreenSettings.nightMode)
        set(null, false, systemDark = true)
        assertEquals(true, ScreenSettings.nightMode)
    }

    @Test
    fun manualReadsTheBooleanOnly() {
        for(manual in listOf(true, false)) for(system in listOf(true, false)) {
            set("manual", manual, system)
            assertEquals("manual=$manual system=$system", manual, ScreenSettings.nightMode)
        }
    }

    @Test
    fun systemFollowsTheConfigurationOnly() {
        for(manual in listOf(true, false)) for(system in listOf(true, false)) {
            set("system", manual, system)
            assertEquals("manual=$manual system=$system", system, ScreenSettings.nightMode)
        }
    }

    @Test
    fun automaticWithoutALightSensorFallsThroughToManual() {
        // The test device has no light sensor, so "automatic" is not available and Android's else branch applies.
        assertEquals(false, ScreenSettings.autoModeAvailable)
        for(manual in listOf(true, false)) for(system in listOf(true, false)) {
            set("automatic", manual, system)
            assertEquals("manual=$manual system=$system", manual, ScreenSettings.nightMode)
        }
    }

    @Test
    fun anUnknownStoredModeIsManual() {
        set("dark", true, systemDark = false)
        assertEquals(true, ScreenSettings.nightMode)
    }
}

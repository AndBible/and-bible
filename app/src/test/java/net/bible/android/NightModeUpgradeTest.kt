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

import net.bible.service.common.CommonUtils
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** [keepNightModeOnUpgrade]: an upgrade keeps the old Manual default, a fresh install follows the system. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class NightModeUpgradeTest {
    private val prefs get() = CommonUtils.realSharedPreferences

    @After
    fun tearDown() {
        prefs.edit().remove("night_mode_pref3").remove(NIGHT_MODE_DEFAULT_CHECKED_KEY).commit()
    }

    private fun setup(mode: String?, marker: Boolean) {
        prefs.edit().apply {
            if(mode == null) remove("night_mode_pref3") else putString("night_mode_pref3", mode)
            if(marker) putBoolean(NIGHT_MODE_DEFAULT_CHECKED_KEY, true) else remove(NIGHT_MODE_DEFAULT_CHECKED_KEY)
        }.commit()
    }

    @Test
    fun upgradeWithAbsentModeKeepsManual() {
        setup(null, marker = false)
        keepNightModeOnUpgrade(prefs, newInstall = false)
        assertEquals("manual", prefs.getString("night_mode_pref3", null))
        assertTrue(prefs.getBoolean(NIGHT_MODE_DEFAULT_CHECKED_KEY, false))
    }

    @Test
    fun upgradeKeepsAStoredMode() {
        setup("automatic", marker = false)
        keepNightModeOnUpgrade(prefs, newInstall = false)
        assertEquals("automatic", prefs.getString("night_mode_pref3", null))
        assertTrue(prefs.getBoolean(NIGHT_MODE_DEFAULT_CHECKED_KEY, false))
    }

    @Test
    fun freshInstallStaysAbsent() {
        setup(null, marker = false)
        keepNightModeOnUpgrade(prefs, newInstall = true)
        assertFalse(prefs.contains("night_mode_pref3"))
        assertTrue(prefs.getBoolean(NIGHT_MODE_DEFAULT_CHECKED_KEY, false))
    }

    @Test
    fun secondUpgradeAfterFreshInstallStaysAbsent() {
        setup(null, marker = true)
        keepNightModeOnUpgrade(prefs, newInstall = false)
        assertFalse(prefs.contains("night_mode_pref3"))
    }
}

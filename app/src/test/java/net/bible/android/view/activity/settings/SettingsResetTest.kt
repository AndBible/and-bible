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
package net.bible.android.view.activity.settings

import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.service.common.CommonUtils
import net.bible.service.device.ScreenSettings
import net.bible.test.DatabaseResetter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The reset key list is a hardcoded contract shared by the classic and Compose settings screens.
 * These assertions exist so moving it out of `SettingsActivity`'s companion cannot quietly drop or
 * duplicate an entry — the failure mode would be a "Reset" that silently stops clearing a setting.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class SettingsResetTest {
    @After fun tearDown() {
        CommonUtils.realSharedPreferences.edit().remove("night_mode_pref").remove("night_mode_pref3").commit()
        RuntimeEnvironment.setQualifiers("+notnight")
        DatabaseResetter.resetDatabase()
    }

    @Test fun theKeyListHasNoDuplicates() {
        assertEquals(SettingsReset.RESET_KEYS.size, SettingsReset.RESET_KEYS.toSet().size)
    }

    @Test fun theKeyListStillCarriesItsKnownTailEntries() {
        for (key in listOf("bible_view_swipe_mode", "experimental_features", "notes_content_type")) {
            assertTrue("$key is missing from RESET_KEYS", SettingsReset.RESET_KEYS.contains(key))
        }
    }

    @Test fun theKeyListIsNotEmpty() {
        assertTrue(SettingsReset.RESET_KEYS.size > 10)
    }

    @Test fun resetReturnsTheReaderToSystemNightMode() {
        val prefs = CommonUtils.realSharedPreferences
        prefs.edit().putString("night_mode_pref3", "manual").putBoolean("night_mode_pref", true).commit()
        RuntimeEnvironment.setQualifiers("+notnight")
        assertTrue(ScreenSettings.nightMode)

        // The same call the Compose settings screen makes (AndroidSettingsPlatform.resetAllSettings).
        SettingsReset.performReset()

        assertFalse(prefs.contains("night_mode_pref3"))
        assertFalse(ScreenSettings.nightMode)
        RuntimeEnvironment.setQualifiers("+night")
        assertTrue(ScreenSettings.nightMode)
    }
}

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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The reset key list is a hardcoded contract shared by the classic and Compose settings screens.
 * These assertions exist so moving it out of `SettingsActivity`'s companion cannot quietly drop or
 * duplicate an entry — the failure mode would be a "Reset" that silently stops clearing a setting.
 */
class SettingsResetTest {
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
}

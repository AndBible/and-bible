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

package net.bible.android.control.backup

import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.service.db.ALL_DB_FILENAMES
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class BackupControlTest {
    /**
     * Regression guard for the "Unknown database file: ..." crash (e.g. progress.sqlite3).
     * Every database that can appear in a backup/restore must have a title mapping,
     * otherwise [selectDatabaseSections]/the reset section throw IllegalStateException.
     */
    @Test
    fun everyBackedUpDatabaseHasATitle() {
        val missing = ALL_DB_FILENAMES.filterNot { databaseTitleResIds.containsKey(it) }
        assertTrue(
            "Database filenames missing from databaseTitleResIds: $missing",
            missing.isEmpty()
        )
    }

    @Test
    fun allTitleResourcesResolveToNonBlankStrings() {
        val context = RuntimeEnvironment.getApplication()
        for ((filename, resId) in databaseTitleResIds) {
            assertNotEquals("Title resId for $filename must not be 0", 0, resId)
            val title = context.getString(resId)
            assertFalse("Title for $filename must not be blank", title.isBlank())
        }
    }

    @Test
    fun databaseTitlesHaveNoExtraEntries() {
        // Keeps the map in sync with the canonical list — no orphaned/typo'd filenames.
        val unexpected = databaseTitleResIds.keys.filterNot { it in ALL_DB_FILENAMES }
        assertTrue(
            "databaseTitleResIds has entries not in ALL_DB_FILENAMES: $unexpected",
            unexpected.isEmpty()
        )
    }
}

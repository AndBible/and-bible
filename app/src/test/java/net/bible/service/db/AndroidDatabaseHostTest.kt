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

package net.bible.service.db

import net.bible.android.BibleApplication.Companion.application
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.service.common.CommonUtils
import net.bible.service.db.oldmigrations.oldMigrations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class AndroidDatabaseHostTest {
    private val host = AndroidDatabaseHost(application)
    private val epochMs = 1_700_000_000_000L
    private val pattern = "yyyyMMdd-HHmmss"

    @Test fun databasePathIsTheContextDatabasePath() {
        assertEquals(application.getDatabasePath("a.sqlite3").path, host.databasePath("a.sqlite3"))
    }

    @Test fun dbBackupPathExistsAfterRead() {
        CommonUtils.dbBackupPath.deleteRecursively()
        assertTrue(java.io.File(host.dbBackupPath).isDirectory)
    }

    @Test fun timestampMatchesSimpleDateFormatInDeviceLocale() {
        assertEquals(SimpleDateFormat(pattern, Locale.getDefault()).format(Date(epochMs)), host.formatTimestamp(epochMs, pattern, true))
        assertEquals(SimpleDateFormat(pattern, Locale.US).format(Date(epochMs)), host.formatTimestamp(epochMs, pattern, false))
    }

    @Test fun timestampMatchesSimpleDateFormatInPersianLocale() {
        val old = Locale.getDefault()
        Locale.setDefault(Locale("fa"))
        try {
            assertEquals(SimpleDateFormat(pattern, Locale("fa")).format(Date(epochMs)), host.formatTimestamp(epochMs, pattern, true))
            assertEquals(SimpleDateFormat(pattern, Locale.US).format(Date(epochMs)), host.formatTimestamp(epochMs, pattern, false))
        } finally {
            Locale.setDefault(old)
        }
    }

    @Test fun legacyMigrationsAreTheOldMigrations() {
        assertEquals(oldMigrations.toList(), host.legacyMonolithicMigrations.toList())
    }
}

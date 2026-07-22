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

package net.bible.service.installzip

import kotlinx.coroutines.runBlocking
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.backup.BackupControl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class InstallInspectorTest {
    private val zipType: suspend (File) -> BackupControl.AbDbFileType = { BackupControl.AbDbFileType.ZIP }
    private val sqliteType: suspend (File) -> BackupControl.AbDbFileType = { BackupControl.AbDbFileType.SQLITE3 }
    private fun scan(existing: List<String> = emptyList(), total: Int = 3, epub: Boolean = false, invalid: Boolean = false) =
        InstallInspector.SwordZipScan(existing, total, epub, invalid)

    @Test fun `epub mime classified as Epub`() = runBlocking {
        val plan = InstallInspector(zipType).inspect(
            File("/x/book.epub"), "book.epub", "application/epub+zip", false,
            studyPadStats = { error("unused") }, swordZipScan = { error("unused") },
            epubUpgradeCheck = { false })
        assertTrue(plan is InstallPlan.Epub)
    }

    @Test fun `ttf by extension classified as Ttf`() = runBlocking {
        val plan = InstallInspector(zipType).inspect(
            File("/x/Font.ttf"), "Font.ttf", null, false,
            { error("") }, { error("") }, { false })
        assertTrue(plan is InstallPlan.Ttf)
    }

    @Test fun `sqlite mybible extension`() = runBlocking {
        val plan = InstallInspector(sqliteType).inspect(
            File("/x/b.sqlite3"), "b.sqlite3", null, false, { error("") }, { error("") }, { false })
        assertEquals(SqliteBookType.MYBIBLE, (plan as InstallPlan.Sqlite).type)
    }

    @Test fun `sword zip with existing files needs overwrite`() = runBlocking {
        val plan = InstallInspector(zipType).inspect(
            File("/x/m.zip"), "m.zip", null, false, { error("") },
            swordZipScan = { scan(existing = listOf("mods.d/x.conf")) }, epubUpgradeCheck = { false })
        assertEquals(listOf("mods.d/x.conf"), (plan as InstallPlan.SwordZip).existingFiles)
    }

    @Test fun `zip that is actually epub`() = runBlocking {
        val plan = InstallInspector(zipType).inspect(
            File("/x/e.zip"), "e.zip", null, false, { error("") }, { scan(epub = true) }, { false })
        assertTrue(plan is InstallPlan.EpubFromZip)
    }

    @Test fun `invalid zip`() = runBlocking {
        val plan = InstallInspector(zipType).inspect(
            File("/x/j.zip"), "j.zip", null, false, { error("") }, { scan(invalid = true) }, { false })
        assertTrue(plan is InstallPlan.Invalid)
    }

    @Test fun `studypad export uses stats`() = runBlocking {
        val plan = InstallInspector(zipType).inspect(
            File("/x/sp.abpb"), "sp.abpb", null, true,
            studyPadStats = { "5 bookmarks" to File("/tmp/unzip") }, swordZipScan = { error("") }, { false })
        assertEquals("5 bookmarks", (plan as InstallPlan.StudyPad).statsText)
    }

    @Test fun `unknown type is invalid`() = runBlocking {
        val plan = InstallInspector({ BackupControl.AbDbFileType.UNKNOWN }).inspect(
            File("/x/weird.dat"), "weird.dat", null, false, { error("") }, { error("") }, { false })
        assertTrue(plan is InstallPlan.Invalid)
    }
}

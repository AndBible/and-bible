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

import org.junit.Assert.assertEquals
import org.junit.Test

class ModuleArchiveTargetNameTest {
    @Test fun rootSqliteModulesMoveIntoMyBibleDirIgnoringCase() {
        assertEquals("mybible/KJV.SQLite3", moduleArchiveTargetName("KJV.SQLite3"))
        assertEquals("mybible/KJV.sqlite3", moduleArchiveTargetName("KJV.sqlite3"))
        assertEquals("mybible/KJV.SQLITE3", moduleArchiveTargetName("KJV.SQLITE3"))
    }

    @Test fun alreadyPlacedEntryIsUnchanged() {
        assertEquals("mybible/KJV.SQLite3", moduleArchiveTargetName("mybible/KJV.SQLite3"))
    }

    @Test fun traversalAndAbsoluteNamesAreNeverRemapped() {
        for (name in listOf("../x.SQLite3", "..\\x.SQLite3", "a/../b.sqlite3", "/abs.SQLite3")) {
            // Both callers normalise backslashes first (moduleArchiveTargetName documents that precondition).
            val normalised = name.replace('\\', '/')
            val target = moduleArchiveTargetName(normalised)
            assertEquals(normalised, target)
            assertEquals(false, target.startsWith("mybible/"))
        }
    }
}

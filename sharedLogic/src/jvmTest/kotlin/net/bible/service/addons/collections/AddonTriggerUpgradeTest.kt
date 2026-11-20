/*
 * Copyright (c) 2026 Martin Denham, Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.service.addons.collections

import kotlinx.coroutines.test.runTest
import net.bible.service.cloudsync.createTriggers
import net.bible.service.cloudsync.dropTriggers
import net.bible.service.db.exec
import net.bible.service.db.openSqlite
import net.bible.shareddata.fixtures.createFromExport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Sync review Minor 5: a device upgrading from bookmark schema 13 has sync triggers without the add-on condition.
 * The container's open (`DatabaseContainer.create`: drop + create every category's triggers after the Room
 * migration) replaces them, so from then on an add-on row is never logged and a user row still is.
 */
class AddonTriggerUpgradeTest {
    private val addonTagCondition = Regex(""" AND NOT \(substr\([^)]*\) = X'ADD08C01A011'\)""")

    @Test
    fun upgradedSchema13DatabaseWithOldTriggersLogsNoAddonRowAfterTheContainerOpen() = runTest {
        val dir = newDir()
        val path = "$dir/old.sqlite3"
        createFromExport("net.bible.android.database.BookmarkDatabase", 13, path)
        // Schema 13's triggers: today's, without the add-on condition.
        val probe = newLocalDb(dir, "probe.sqlite3")
        val oldTriggers = try {
            probe.rows("SELECT sql FROM sqlite_master WHERE type = 'trigger' AND name LIKE 'Label%'").map { addonTagCondition.replace(it[0]!!, "") }
        } finally { probe.close() }
        assertEquals(3, oldTriggers.size)
        assertTrue(oldTriggers.none { it.contains("ADD08C01A011") })
        openSqlite(path).let { c -> try { oldTriggers.forEach { c.exec(it) } } finally { c.close() } }

        val db = openBookmarkDb(path)
        try {
            val addonLabel = addonId("Manna", Src.comfort)
            // Before the container's trigger refresh the old trigger logs an add-on row...
            db.sql("INSERT INTO Label (id, name) VALUES (${hex(addonLabel)}, 'Comfort')")
            assertEquals(1, db.count("SELECT COUNT(*) FROM LogEntry WHERE entityId1 = ${hex(addonLabel)}"))
            db.sql("DELETE FROM Label", "DELETE FROM LogEntry")

            // ...after it (what DatabaseContainer.create runs for every category), never.
            val acc = accessor(db, path)
            dropTriggers(acc)
            createTriggers(acc)
            db.sql(
                "INSERT INTO Label (id, name) VALUES (${hex(addonLabel)}, 'Comfort')",
                "UPDATE Label SET name = 'Renamed' WHERE id = ${hex(addonLabel)}",
                "INSERT INTO Label (id, name) VALUES (${hex(Src.comfort)}, 'Mine')",
            )
            assertEquals(listOf(listOf(Src.comfort.toHex())), db.rows("SELECT lower(hex(entityId1)) FROM LogEntry"))
            db.sql("DELETE FROM Label WHERE id = ${hex(addonLabel)}")
            assertEquals(1, db.count("SELECT COUNT(*) FROM LogEntry"))
        } finally {
            db.close()
        }
    }
}

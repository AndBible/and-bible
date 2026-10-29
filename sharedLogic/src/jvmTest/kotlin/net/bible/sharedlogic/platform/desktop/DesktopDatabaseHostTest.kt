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
package net.bible.sharedlogic.platform.desktop

import net.bible.sharedcore.platform.desktop.DesktopAppEnvironment
import net.bible.sharedcore.platform.desktop.DesktopDeviceSettings
import net.bible.sharedcore.platform.desktop.DesktopPaths
import net.bible.sharedcore.platform.secrets.SecretStore
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopDatabaseHostTest {
    private val root: Path = Files.createTempDirectory("desktop-dbhost")
    private val secrets = object : SecretStore {
        private val values = mutableMapOf<String, String>()
        override fun get(key: String) = values[key]
        override fun put(key: String, value: String) { values[key] = value }
        override fun remove(key: String) { values.remove(key) }
    }
    private val env = DesktopAppEnvironment(
        DesktopPaths({ null }, "Linux", root), secrets, DesktopDeviceSettings(root.resolve("prefs.properties")),
        properties = mapOf("andbible.versionCode" to "42")::get,
    )

    @Test fun databasesLiveUnderDataDatabasesAndAreListed() {
        val host = DesktopDatabaseHost(env)
        val path = Path.of(host.databasePath("bookmarks.db"))
        assertEquals(root.resolve(".local/share/andbible/databases/bookmarks.db"), path)
        Files.writeString(path, "x")
        assertEquals(listOf("bookmarks.db"), host.databaseList())
    }

    @Test fun rootOverrideIsUsedAndCreated() {
        val override = root.resolve("elsewhere/dbs")
        val host = DesktopDatabaseHost(env, override)
        assertEquals(override.resolve("a.db").toString(), host.databasePath("a.db"))
        assertTrue(Files.isDirectory(override))
        assertEquals(emptyList(), host.databaseList())
    }

    @Test fun deleteRemovesDatabaseAndSidecarsOnly() {
        val host = DesktopDatabaseHost(env)
        val dir = Path.of(host.databasePath("a.db")).parent
        listOf("a.db", "a.db-wal", "a.db-shm", "other.db").forEach { Files.writeString(dir.resolve(it), "x") }
        assertTrue(host.deleteDatabase("a.db"))
        assertEquals(listOf("other.db"), host.databaseList())
        assertFalse(host.deleteDatabase("a.db"))
    }

    @Test fun hostFactsFollowTheEnvironmentAndThereAreNoLegacyMigrations() {
        val host = DesktopDatabaseHost(env)
        assertFalse(host.isRunningTests)
        assertEquals(42, host.applicationVersionNumber)
        assertEquals(env.deviceIdentifier, host.deviceIdentifier)
        assertTrue(Files.isDirectory(Path.of(host.dbBackupPath)))
        assertEquals(0, host.legacyMonolithicMigrations.size)
    }

    @Test fun formatTimestampUsesUsLocaleUnlessDeviceLocaleIsRequested() {
        val host = DesktopDatabaseHost(env)
        val old = Locale.getDefault()
        val old2 = java.util.TimeZone.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("fi-FI")); java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"))
            assertEquals("Sun", host.formatTimestamp(1_772_928_000_000, "EEE", false))
            assertEquals("su", host.formatTimestamp(1_772_928_000_000, "EEE", true))
        } finally { Locale.setDefault(old); java.util.TimeZone.setDefault(old2) }
    }
}

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
package net.bible.sharedcore.platform.desktop

import net.bible.sharedcore.platform.CrashPreferences
import net.bible.sharedcore.platform.secrets.SecretStore
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopAppEnvironmentTest {
    private class MemorySecrets : SecretStore {
        val values = mutableMapOf<String, String>()
        override fun get(key: String) = values[key]
        override fun put(key: String, value: String) { values[key] = value }
        override fun remove(key: String) { values.remove(key) }
    }

    private val root: Path = Files.createTempDirectory("desktop-env")
    private val paths = DesktopPaths({ null }, "Linux", root)
    private val settings = DesktopDeviceSettings(root.resolve("prefs.properties"))
    private val secrets = MemorySecrets()

    private fun env(properties: Map<String, String> = emptyMap()) =
        DesktopAppEnvironment(paths, secrets, settings, properties = properties::get)

    @Test fun versionComesFromSystemPropertyAndFallsBackToDev() {
        assertEquals("dev", env().versionName)
        assertEquals("dev", env(mapOf("andbible.version" to "  ")).versionName)
        assertEquals("5.0.1", env(mapOf("andbible.version" to "5.0.1")).versionName)
        assertEquals(0L, env().versionNumber)
        assertEquals(500L, env(mapOf("andbible.versionCode" to "500")).versionNumber)
    }

    @Test fun realSystemPropertyIsTheDefaultSource() {
        val old = System.getProperty("andbible.version")
        try {
            System.setProperty("andbible.version", "9.9.9")
            assertEquals("9.9.9", DesktopAppEnvironment(paths, secrets, settings).versionName)
            System.clearProperty("andbible.version")
            assertEquals("dev", DesktopAppEnvironment(paths, secrets, settings).versionName)
        } finally {
            if (old != null) System.setProperty("andbible.version", old) else System.clearProperty("andbible.version")
        }
    }

    @Test fun flagsAreFixed() {
        val e = env()
        assertFalse(e.isDiscrete)
        assertFalse(e.isMeteredNetwork)
        assertNull(e.apkPath)
        assertTrue(e.isInitialized)
    }

    @Test fun freeSpaceOfTheDataDirIsPositive() {
        assertTrue(env().megabytesFree > 0)
        assertTrue(Files.isDirectory(paths.data))
    }

    @Test fun directoriesAreCreatedAndDatabasesLiveUnderData() {
        val e = env()
        assertEquals(paths.data.resolve("databases").resolve("x.db").toString(), e.databasePath("x.db"))
        assertTrue(Files.isDirectory(Path.of(e.dbBackupPath)))
        assertTrue(Files.isDirectory(Path.of(e.cacheDir)))
        assertTrue(Files.isDirectory(Path.of(e.tmpDir)))
        assertEquals(paths.sword.toString(), e.modulesDir)
    }

    @Test fun deviceIdentifierIsGeneratedOnceAndPersistedInTheSecretStore() {
        val first = env().deviceIdentifier
        assertEquals(first, env().deviceIdentifier)
        assertEquals(first, secrets.values["device_identifier"])
    }

    @Test fun crashTimeComesFromDeviceSettings() {
        assertEquals(0L, env().lastCrashTime)
        settings.putLong(CrashPreferences.TIME, 123L)
        assertEquals(123L, env().lastCrashTime)
    }

    @Test fun deleteDatabaseRemovesDatabaseAndSidecars() {
        val e = env()
        val db = Path.of(e.databasePath("a.db"))
        listOf("a.db", "a.db-wal", "a.db-shm").forEach { Files.writeString(db.resolveSibling(it), "x") }
        assertTrue(e.deleteDatabase("a.db"))
        assertEquals(emptyList(), Files.list(db.parent).use { it.toList() })
        assertFalse(e.deleteDatabase("a.db"))
    }

    @Test fun orphanSidecarsAreRemovedButReportFalse() {
        val e = env()
        val db = Path.of(e.databasePath("b.db"))
        Files.writeString(db.resolveSibling("b.db-wal"), "x")
        assertFalse(e.deleteDatabase("b.db"))
        assertFalse(Files.exists(db.resolveSibling("b.db-wal")))
    }
}

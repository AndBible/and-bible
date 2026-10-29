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

import net.bible.sharedcore.log.Log
import net.bible.sharedcore.platform.AppEnvironment
import net.bible.sharedcore.platform.CrashPreferences
import net.bible.sharedcore.platform.secrets.SecretStore
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

/**
 * Process and file-system facts on the desktop. Paths come from [paths]; the persistent device identifier lives in
 * [secrets] (the caller picks the store, as on iOS); the crash time is read from [deviceSettings].
 *
 * The version name is the `andbible.version` system property (the packaging sets it), `"dev"` when absent;
 * [versionNumber] is the optional `andbible.versionCode` property, 0 when absent. The network is never reported as
 * metered: the desktop has no portable way to ask, and metered-connection warnings are a phone concern.
 */
class DesktopAppEnvironment(
    private val paths: DesktopPaths,
    private val secrets: SecretStore,
    private val deviceSettings: DesktopDeviceSettings,
    private val initialized: () -> Boolean = { true },
    private val properties: (String) -> String? = System::getProperty,
    override val isBeta: Boolean = false,
    override val isDebugMode: Boolean = false,
) : AppEnvironment {
    /** The application version, `"dev"` when the packaging did not set `andbible.version`. */
    val versionName: String get() = properties("andbible.version")?.takeIf { it.isNotBlank() } ?: "dev"

    override val isInitialized: Boolean get() = initialized()
    override val isDiscrete: Boolean get() = false
    override val deviceIdentifier: String
        get() = secrets.get("device_identifier") ?: UUID.randomUUID().toString().also { secrets.put("device_identifier", it) }
    override val applicationNameMedium: String get() = "AndBible"
    override val packageName: String get() = "net.bible.desktop"
    override val versionNumber: Long get() = properties("andbible.versionCode")?.toLongOrNull() ?: 0L
    override val isMeteredNetwork: Boolean get() = false
    override val lastCrashTime: Long get() = deviceSettings.getLong(CrashPreferences.TIME, 0L)
    override val apkPath: String? get() = null

    /** Sword modules directory, created on read. */
    val modulesDir: String get() = ensured(paths.sword)
    override val tmpDir: String get() = ensured(paths.cache.resolve("tmp"))
    override val cacheDir: String get() = ensured(paths.cache)
    override val dbBackupPath: String get() = ensured(paths.data.resolve("backup"))

    override fun databasePath(name: String): String = ensured(paths.data.resolve("databases")) + "/" + name

    override fun deleteDatabase(name: String): Boolean {
        val path = Path.of(databasePath(name))
        var removedDatabase = false
        var succeeded = true
        listOf(path, path.resolveSibling("$name-wal"), path.resolveSibling("$name-shm")).forEach { file ->
            try {
                if (Files.deleteIfExists(file) && file == path) removedDatabase = true
            } catch (e: Exception) {
                succeeded = false
                Log.w(TAG, "Database removal failed: $file", e)
            }
        }
        return removedDatabase && succeeded
    }

    override val megabytesFree: Long
        get() = try {
            Files.getFileStore(Path.of(ensured(paths.data))).usableSpace / (1024L * 1024L)
        } catch (e: Exception) {
            Log.w(TAG, "Free space query failed", e)
            0L
        }

    private fun ensured(path: Path): String {
        try {
            Files.createDirectories(path)
        } catch (e: Exception) {
            Log.w(TAG, "Directory creation failed: $path", e)
        }
        return path.toString()
    }

    private companion object { const val TAG = "DesktopAppEnvironment" }
}

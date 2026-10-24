package net.bible.service.db

import androidx.room3.migration.Migration
import net.bible.sharedcore.settings.store.SettingsStore
import okio.FileSystem
import okio.IOException
import okio.Path.Companion.toPath

/** A [DatabaseHost] over a plain directory: real files, no Android. Containers are opened with `create()`. */
class FakeDatabaseHost(private val dir: String) : DatabaseHost {
    override val isRunningTests = false
    override fun databasePath(name: String) = "$dir/$name"

    override fun deleteDatabase(name: String): Boolean = try {
        val path = databasePath(name).toPath()
        val existed = FileSystem.SYSTEM.exists(path)
        FileSystem.SYSTEM.delete(path, mustExist = false)
        for (suffix in listOf("-journal", "-wal", "-shm")) FileSystem.SYSTEM.delete((databasePath(name) + suffix).toPath(), mustExist = false)
        existed
    } catch (_: IOException) {
        false
    }

    override fun databaseList(): List<String> = FileSystem.SYSTEM.listOrNull(dir.toPath()).orEmpty().map { it.name }
    override val dbBackupPath = "$dir/backups"
    override val applicationVersionNumber = 1
    override val deviceIdentifier = "test-device"
    override fun formatTimestamp(epochMs: Long, pattern: String, deviceLocale: Boolean) = "20260101-000000"
    override fun migrateOldSettingsKeys() {}
    override fun zipDatabaseFilesForUpgradeBackup(): String? = null
    override val databaseBackupSuffix = ".zip"
    override val legacyMonolithicMigrations: Array<out Migration> = emptyArray()
    override suspend fun awaitWorkspacePersisted() {}
    override fun flushSettingsBeforeExit(store: SettingsStore?, timeoutMs: Long?) {}
    override fun openContainer(): DatabaseContainer = error("commonTest opens containers with create()")
}

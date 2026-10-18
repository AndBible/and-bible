package net.bible.shareddata

import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.room3.migration.Migration
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers

/**
 * Same builder as `:sharedData`'s test helper of this name (needed by the shared `ExportFixtures.kt`): a bundled-driver
 * database file at `dir/name` with the given migrations.
 */
inline fun <reified T : RoomDatabase> openTestDatabase(
    dir: String,
    name: String,
    vararg migrations: Migration,
): T = Room.databaseBuilder<T>(name = "$dir/$name")
    .setDriver(BundledSQLiteDriver())
    .setQueryCoroutineContext(Dispatchers.Default)
    .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
    .addMigrations(*migrations)
    .build()

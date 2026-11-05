package net.bible.shareddata

import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.room3.migration.Migration
import net.bible.service.db.sqliteDriverFactory
import kotlinx.coroutines.Dispatchers

/** A fresh writable directory, isolated from every other test database. */
expect fun testDatabaseDir(): String

inline fun <reified T : RoomDatabase> openTestDatabase(
    dir: String,
    name: String,
    vararg migrations: Migration,
): T = Room.databaseBuilder<T>(name = "$dir/$name")
    .setDriver(sqliteDriverFactory())
    .setQueryCoroutineContext(Dispatchers.Default)
    .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
    .addMigrations(*migrations)
    .build()

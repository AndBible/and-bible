package net.bible.android.database.migrations

import kotlinx.coroutines.runBlocking
import net.bible.android.BibleApplication.Companion.application
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.service.db.exec
import net.bible.service.db.openSqlite
import net.bible.service.db.queryLong
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * D1 final review M1: MIGRATION_27_28 falls back to a non-unique index when existing duplicates prevent the unique
 * one. The fallback caught `java.sql.SQLException`, which SQLite never throws, so it was dead and the migration
 * failed instead; it now catches `androidx.sqlite.SQLiteException`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class OldMonolithicMigration27To28Test {
    private val file = File(application.cacheDir, "old-monolithic-27.db")
    private val migration = oldMonolithicAppDatabaseMigrations.single { it.startVersion == 27 }

    @After fun tearDown() { file.delete() }

    /** The v27 shape of the two tables the migration touches, with optional duplicates. */
    private fun migrateV27(duplicates: Boolean): Pair<Long, Long> = openSqlite(file.apply { delete() }.path).use { c ->
        c.exec("CREATE TABLE `readingplan` (`_id` INTEGER PRIMARY KEY, `plan_code` TEXT NOT NULL, `plan_start_date` INTEGER NOT NULL, `plan_current_day` INTEGER NOT NULL DEFAULT 1)")
        c.exec("CREATE INDEX `index_readingplan_plan_code` ON `readingplan` (`plan_code`)")
        c.exec("CREATE TABLE `readingplan_status` (`_id` INTEGER PRIMARY KEY AUTOINCREMENT, `plan_code` TEXT NOT NULL, `plan_day` INTEGER NOT NULL, `reading_status` TEXT NOT NULL)")
        c.exec("CREATE INDEX `code_day` ON `readingplan_status` (`plan_code`, `plan_day`)")
        c.exec("INSERT INTO readingplan VALUES (1, 'y1', 0, 1)")
        c.exec("INSERT INTO readingplan_status (plan_code, plan_day, reading_status) VALUES ('y1', 1, 's')")
        if (duplicates) {
            c.exec("INSERT INTO readingplan VALUES (2, 'y1', 0, 3)")
            c.exec("INSERT INTO readingplan_status (plan_code, plan_day, reading_status) VALUES ('y1', 1, 't')")
        }
        runBlocking { migration.migrate(c) }
        fun unique(table: String, index: String) =
            c.queryLong("SELECT \"unique\" FROM pragma_index_list('$table') WHERE name = '$index'")!!
        assertEquals(if (duplicates) 2L else 1L, c.queryLong("SELECT count(*) FROM readingplan"))
        unique("readingplan", "index_readingplan_plan_code") to unique("readingplan_status", "code_day")
    }

    @Test fun duplicatesFallBackToNonUniqueIndexes() = assertEquals(0L to 0L, migrateV27(duplicates = true))

    @Test fun withoutDuplicatesTheIndexesAreUnique() = assertEquals(1L to 1L, migrateV27(duplicates = false))
}

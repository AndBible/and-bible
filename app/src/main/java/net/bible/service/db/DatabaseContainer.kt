/*
 * Copyright (c) 2018 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
 *
 * This file is part of And Bible (http://github.com/AndBible/and-bible).
 *
 * And Bible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * And Bible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with And Bible.
 * If not, see http://www.gnu.org/licenses/.
 *
 */
package net.bible.service.db

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import net.bible.android.BibleApplication
import net.bible.android.control.page.window.WindowLayout
import net.bible.service.db.bookmark.Bookmark
import net.bible.service.db.bookmark.BookmarkDatabaseDefinition
import net.bible.service.db.bookmark.BookmarkToLabel
import net.bible.service.db.bookmark.Label
import net.bible.service.db.mynote.MyNote
import net.bible.service.db.mynote.MyNoteDatabaseDefinition
import net.bible.service.db.readingplan.ReadingPlan
import net.bible.service.db.readingplan.ReadingPlanDatabaseOperations
import net.bible.service.db.readingplan.ReadingPlanStatus
import net.bible.service.db.workspaces.WorkspaceDao
import net.bible.service.db.workspaces.WorkspaceEntities
import java.util.*


const val DATABASE_NAME = "andBibleDatabase-6.db"
private const val DATABASE_VERSION = 8

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        MyNoteDatabaseDefinition.instance.onCreate(db)
    }
}

private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        BookmarkDatabaseDefinition.instance.upgradeToVersion3(db)
        MyNoteDatabaseDefinition.instance.upgradeToVersion3(db)
    }
}

private val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        BookmarkDatabaseDefinition.instance.upgradeToVersion4(db)
    }
}

private val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        BookmarkDatabaseDefinition.instance.upgradeToVersion5(db)

    }
}

private val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        ReadingPlanDatabaseOperations.instance.onCreate(db)
        ReadingPlanDatabaseOperations.instance.migratePrefsToDatabase(db)
    }
}

private val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.apply {
            execSQL("PRAGMA foreign_keys=ON;")
            execSQL("DROP TRIGGER IF EXISTS bookmark_cleanup;")
            execSQL("DROP TRIGGER IF EXISTS label_cleanup;")
            execSQL("CREATE TABLE `bookmark_label_new` (`bookmark_id` INTEGER NOT NULL, `label_id` INTEGER NOT NULL, PRIMARY KEY(`bookmark_id`, `label_id`), FOREIGN KEY(`bookmark_id`) REFERENCES `bookmark`(`_id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`label_id`) REFERENCES `label`(`_id`) ON UPDATE NO ACTION ON DELETE CASCADE );");
            execSQL("INSERT INTO bookmark_label_new SELECT * from bookmark_label;")
            execSQL("DROP TABLE bookmark_label;")
            execSQL("ALTER TABLE bookmark_label_new RENAME TO bookmark_label;")
            execSQL("CREATE INDEX IF NOT EXISTS `code_day` ON `readingplan_status` (`plan_code`, `plan_day`)")
            execSQL("CREATE INDEX IF NOT EXISTS `index_readingplan_plan_code` ON `readingplan` (`plan_code`)")
        }
    }
}

private val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.apply {
            // TODO: Table creations here
        }
    }
}

class Converters {
    @TypeConverter
    fun fromTimestamp(value: Long): Date = Date(value)

    @TypeConverter
    fun dateToTimestamp(date: Date): Long = date.time

    @TypeConverter
    fun windowStateToString(windowState: WindowLayout.WindowState): String = windowState.toString()

    @TypeConverter
    fun stringToWindowState(str: String): WindowLayout.WindowState = WindowLayout.WindowState.valueOf(str)
}

@Database(
    entities = [
        Bookmark::class,
        Label::class,
        BookmarkToLabel::class,
        MyNote::class,
        ReadingPlan::class,
        ReadingPlanStatus::class,
        WorkspaceEntities.Workspace::class,
        WorkspaceEntities.Window::class,
        WorkspaceEntities.HistoryItem::class,
        WorkspaceEntities.PageManager::class
    ],
    version = DATABASE_VERSION
)
@TypeConverters(Converters::class)
abstract class AppDatabase: RoomDatabase() {
    abstract fun workspaceDao(): WorkspaceDao

    fun sync() { // Sync all data so far into database file
        val cur = openHelper.writableDatabase
            .query("PRAGMA wal_checkpoint(FULL)")
        cur.moveToFirst()
        cur.close()
    }

    fun reset() {
        DatabaseContainer.reset()
    }
}

object DatabaseContainer {
	private var instance: AppDatabase? = null

	val db: AppDatabase
		get () {
			return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    BibleApplication.application, AppDatabase::class.java, DATABASE_NAME
                )
                    .allowMainThreadQueries()
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7,
                        MIGRATION_7_8
                    )
                    .build()
                    .also { instance = it }
            }
		}
	fun reset() {
        synchronized(this) {
            instance = null
        }
	}
}

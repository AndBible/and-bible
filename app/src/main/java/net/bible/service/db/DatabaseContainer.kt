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
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import net.bible.android.BibleApplication
import net.bible.service.db.bookmark.Bookmark
import net.bible.service.db.bookmark.BookmarkDatabaseDefinition
import net.bible.service.db.bookmark.BookmarkToLabel
import net.bible.service.db.bookmark.Label
import net.bible.service.db.mynote.MyNote
import net.bible.service.db.mynote.MyNoteDatabaseDefinition
import net.bible.service.db.readingplan.ReadingPlan
import net.bible.service.db.readingplan.ReadingPlanDatabaseOperations
import net.bible.service.db.readingplan.ReadingPlanStatus


const val DATABASE_NAME = "andBibleDatabase.db"
private const val DATABASE_VERSION = 6

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

@Database(
    entities = [
        Bookmark::class,
        Label::class,
        BookmarkToLabel::class,
        MyNote::class,
        ReadingPlan::class,
        ReadingPlanStatus::class
    ],
    version = DATABASE_VERSION
)
abstract class AppDatabase: RoomDatabase() {
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
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6
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

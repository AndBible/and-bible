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

import androidx.room.ColumnInfo
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import net.bible.android.BibleApplication
import net.bible.service.db.bookmark.BookmarkDatabaseDefinition
import net.bible.service.db.mynote.MyNoteDatabaseDefinition
import net.bible.service.db.readingplan.ReadingPlanDatabaseOperations

/**
 * Oversee database creation and upgrade based on version
 * There is a single And Bible database but creation and upgrade is implemented by the different db modules
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */

const val DATABASE_VERSION = 6
const val DATABASE_NAME = "andBibleDatabase.db"

object DatabaseContainer {
	private var instance: AppDatabase? = null

	val db: AppDatabase
		get () {
			if(instance == null) {
				instance = Room.databaseBuilder(BibleApplication.application, AppDatabase::class.java, DATABASE_NAME)
					.addMigrations(
						AppDatabase.MIGRATION_0_1,
						AppDatabase.MIGRATION_1_2,
						AppDatabase.MIGRATION_2_3,
						AppDatabase.MIGRATION_3_4,
						AppDatabase.MIGRATION_4_5,
						AppDatabase.MIGRATION_5_6
					).build()
			}
			return instance!!
		}
	fun reset() {
		instance = null
	}
}

@Entity(tableName = "bookmark")
data class Bookmarks(
	@PrimaryKey @ColumnInfo(name="_id") val id: Int?,
	@ColumnInfo(name = "created_on") val createdOn: Int?,
	val key: String,
	val versification: String?,
	@ColumnInfo(name = "speak_settings") val speakSettings: String?
)

@Database(entities = [Bookmarks::class], version = DATABASE_VERSION)
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

	companion object {
		val MIGRATION_0_1 = object : Migration(0, 1) {
			override fun migrate(db: SupportSQLiteDatabase) {
				BookmarkDatabaseDefinition.instance.onCreate(db)
			}
		}

		val MIGRATION_1_2 = object : Migration(1, 2) {
			override fun migrate(db: SupportSQLiteDatabase) {
				MyNoteDatabaseDefinition.instance.onCreate(db)
			}
		}

		val MIGRATION_2_3 = object : Migration(2, 3) {
			override fun migrate(db: SupportSQLiteDatabase) {
				BookmarkDatabaseDefinition.instance.upgradeToVersion3(db)
				MyNoteDatabaseDefinition.instance.upgradeToVersion3(db)
			}
		}
		val MIGRATION_3_4 = object : Migration(3, 4) {
			override fun migrate(db: SupportSQLiteDatabase) {
				BookmarkDatabaseDefinition.instance.upgradeToVersion4(db)

			}
		}
		val MIGRATION_4_5 = object : Migration(4, 5) {
			override fun migrate(db: SupportSQLiteDatabase) {
				BookmarkDatabaseDefinition.instance.upgradeToVersion5(db)

			}
		}
		val MIGRATION_5_6 = object : Migration(5, 6) {
			override fun migrate(db: SupportSQLiteDatabase) {
				ReadingPlanDatabaseOperations.instance.onCreate(db)
				ReadingPlanDatabaseOperations.instance.migratePrefsToDatabase(db)
			}
		}
	}
}


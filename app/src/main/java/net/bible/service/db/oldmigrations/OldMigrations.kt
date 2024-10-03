/*
 * Copyright (c) 2023 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.service.db.oldmigrations

import android.content.ContentValues
import io.requery.android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.sqlite.db.SupportSQLiteDatabase
import net.bible.android.BibleApplication
import net.bible.android.activity.R
import net.bible.android.common.toV11n
import net.bible.android.database.bookmarks.KJVA
import net.bible.android.database.migrations.Migration
import net.bible.android.database.migrations.TAG
import net.bible.service.common.CommonUtils
import org.crosswire.jsword.passage.VerseRange
import org.crosswire.jsword.passage.VerseRangeFactory
import org.crosswire.jsword.versification.Versification
import org.crosswire.jsword.versification.system.Versifications

private val MIGRATION_37_38_MyNotes_To_Bookmarks = object : Migration(37, 38) {
    override fun doMigrate(db: SupportSQLiteDatabase) {
        db.apply {
            execSQL("ALTER TABLE `Bookmark` ADD COLUMN `lastUpdatedOn` INTEGER NOT NULL DEFAULT 0")
            execSQL("UPDATE Bookmark SET lastUpdatedOn=createdAt")

            val c = db.query("SELECT * from mynote")
            val idIdx = c.getColumnIndex("_id")
            val keyIdx = c.getColumnIndex("key")
            val v11nIdx = c.getColumnIndex("versification")
            val myNoteIdx = c.getColumnIndex("mynote")
            val lastUpdatedOnIdx = c.getColumnIndex("last_updated_on")
            val createdOnIdx = c.getColumnIndex("created_on")

            c.moveToFirst()

            var labelId = -1L
            if(!c.isAfterLast) {
                val labelValues = ContentValues().apply {
                    put("name", BibleApplication.application.getString(R.string.migrated_my_notes))
                }
                labelId = db.insert("Label", SQLiteDatabase.CONFLICT_FAIL, labelValues)
            }

            while(!c.isAfterLast) {
                val id = c.getLong(idIdx)
                val key = c.getString(keyIdx)
                var v11n: Versification? = null
                var verseRange: VerseRange? = null
                var verseRangeInKjv: VerseRange? = null

                try {
                    v11n = Versifications.instance().getVersification(
                        c.getString(v11nIdx) ?: Versifications.DEFAULT_V11N
                    )
                    verseRange = VerseRangeFactory.fromString(v11n, key)
                    verseRangeInKjv = verseRange.toV11n(KJVA)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to migrate bookmark: v11n:$v11n verseRange:$verseRange verseRangeInKjv:$verseRangeInKjv", e)
                    c.moveToNext()
                    continue
                }

                val createdAt = c.getLong(createdOnIdx)
                val lastUpdatedOn = c.getLong(lastUpdatedOnIdx)
                val myNote = c.getString(myNoteIdx)
                val newValues = ContentValues()
                newValues.apply {
                    put("v11n", v11n.name)
                    put("kjvOrdinalStart", verseRangeInKjv.start.ordinal)
                    put("kjvOrdinalEnd", verseRangeInKjv.end.ordinal)
                    put("ordinalStart", verseRange.start.ordinal)
                    put("ordinalEnd", verseRange.end.ordinal)
                    put("createdAt", createdAt)
                    put("lastUpdatedOn", lastUpdatedOn)
                    put("notes", myNote)
                }
                val bookmarkId = db.insert("Bookmark", SQLiteDatabase.CONFLICT_FAIL, newValues)

                val bookmarkLabelValues = ContentValues().apply {
                    put("bookmarkId", bookmarkId)
                    put("labelId", labelId)
                }
                db.insert("BookmarkToLabel", SQLiteDatabase.CONFLICT_FAIL, bookmarkLabelValues)
                c.moveToNext()
            }
            execSQL("DROP TABLE mynote;")
        }
    }
}

private val MIGRATION_53_54_booleanSettings = object : Migration(53, 54) {
    override fun doMigrate(db: SupportSQLiteDatabase) {
        db.apply {
            execSQL("CREATE INDEX IF NOT EXISTS `index_Bookmark_primaryLabelId` ON `Bookmark` (`primaryLabelId`)")
            execSQL("CREATE TABLE IF NOT EXISTS `BooleanSetting` (`key` TEXT NOT NULL, `value` INTEGER NOT NULL, PRIMARY KEY(`key`))");
            execSQL("CREATE TABLE IF NOT EXISTS `StringSetting` (`key` TEXT NOT NULL, `value` TEXT NOT NULL, PRIMARY KEY(`key`))")
            execSQL("CREATE TABLE IF NOT EXISTS `LongSetting` (`key` TEXT NOT NULL, `value` INTEGER NOT NULL, PRIMARY KEY(`key`))")
            execSQL("CREATE TABLE IF NOT EXISTS `DoubleSetting` (`key` TEXT NOT NULL, `value` REAL NOT NULL, PRIMARY KEY(`key`))")
            val sharedPreferences = CommonUtils.realSharedPreferences
            for((k, v) in sharedPreferences.all) {
                val values = ContentValues()
                values.put("key", k)
                when(v) {
                    is Long -> {
                        values.put("value", v)
                        db.insert("LongSetting", SQLiteDatabase.CONFLICT_IGNORE, values)
                    }
                    is Int -> {
                        values.put("value", v)
                        db.insert("LongSetting", SQLiteDatabase.CONFLICT_IGNORE, values)
                    }
                    is Boolean -> {
                        values.put("value", v)
                        db.insert("BooleanSetting", SQLiteDatabase.CONFLICT_IGNORE, values)
                    }
                    is String -> {
                        values.put("value", v)
                        db.insert("StringSetting", SQLiteDatabase.CONFLICT_IGNORE, values)
                    }
                    is Float -> {
                        values.put("value", v)
                        db.insert("DoubleSetting", SQLiteDatabase.CONFLICT_IGNORE, values)
                    }
                    is Double -> {
                        values.put("value", v)
                        db.insert("DoubleSetting", SQLiteDatabase.CONFLICT_IGNORE, values)
                    }
                    else -> {
                        Log.e(TAG, "Illegal value '$k', $v")
                    }
                }
            }
        }
    }
}

val oldMigrations = arrayOf(
    MIGRATION_37_38_MyNotes_To_Bookmarks,
    MIGRATION_53_54_booleanSettings,
)

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

import android.util.Log
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import net.bible.service.db.columnIndex
import net.bible.service.db.insertOr
import net.bible.service.db.queryRows
import net.bible.service.db.textOrNull
import net.bible.android.database.migrations.Migration
import net.bible.android.BibleApplication
import net.bible.android.activity.R
import net.bible.android.common.toV11n
import net.bible.android.database.bookmarks.KJVA
import net.bible.android.database.migrations.TAG
import net.bible.service.common.CommonUtils
import org.crosswire.jsword.passage.VerseRange
import org.crosswire.jsword.passage.VerseRangeFactory
import org.crosswire.jsword.versification.Versification
import org.crosswire.jsword.versification.system.Versifications

private val MIGRATION_37_38_MyNotes_To_Bookmarks = object : Migration(37, 38) {
    override fun doMigrate(connection: SQLiteConnection) {
        val db = connection
        db.apply {
            execSQL("ALTER TABLE `Bookmark` ADD COLUMN `lastUpdatedOn` INTEGER NOT NULL DEFAULT 0")
            execSQL("UPDATE Bookmark SET lastUpdatedOn=createdAt")

            class MyNote(val id: Long, val key: String?, val v11n: String?, val myNote: String?, val lastUpdatedOn: Long, val createdOn: Long)
            val myNotes = db.queryRows("SELECT * from mynote") { st ->
                val idIdx = st.columnIndex("_id")
                val keyIdx = st.columnIndex("key")
                val v11nIdx = st.columnIndex("versification")
                val myNoteIdx = st.columnIndex("mynote")
                val lastUpdatedOnIdx = st.columnIndex("last_updated_on")
                val createdOnIdx = st.columnIndex("created_on")
                MyNote(st.getLong(idIdx), st.textOrNull(keyIdx), st.textOrNull(v11nIdx), st.textOrNull(myNoteIdx),
                    st.getLong(lastUpdatedOnIdx), st.getLong(createdOnIdx))
            }

            var labelId = -1L
            if(myNotes.isNotEmpty()) {
                labelId = db.insertOr("FAIL", "Label", "name" to BibleApplication.application.getString(R.string.migrated_my_notes))
            }

            for (c in myNotes) {
                val key = c.key
                var v11n: Versification? = null
                var verseRange: VerseRange? = null
                var verseRangeInKjv: VerseRange? = null

                try {
                    v11n = Versifications.instance().getVersification(
                        c.v11n ?: Versifications.DEFAULT_V11N
                    )
                    verseRange = VerseRangeFactory.fromString(v11n, key)
                    verseRangeInKjv = verseRange.toV11n(KJVA)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to migrate bookmark: v11n:$v11n verseRange:$verseRange verseRangeInKjv:$verseRangeInKjv", e)
                    continue
                }

                val bookmarkId = db.insertOr("FAIL", "Bookmark",
                    "v11n" to v11n!!.name,
                    "kjvOrdinalStart" to verseRangeInKjv.start.ordinal,
                    "kjvOrdinalEnd" to verseRangeInKjv.end.ordinal,
                    "ordinalStart" to verseRange.start.ordinal,
                    "ordinalEnd" to verseRange.end.ordinal,
                    "createdAt" to c.createdOn,
                    "lastUpdatedOn" to c.lastUpdatedOn,
                    "notes" to c.myNote,
                )

                db.insertOr("FAIL", "BookmarkToLabel", "bookmarkId" to bookmarkId, "labelId" to labelId)
            }
            execSQL("DROP TABLE mynote;")
        }
    }
}

private val MIGRATION_53_54_booleanSettings = object : Migration(53, 54) {
    override fun doMigrate(connection: SQLiteConnection) {
        val db = connection
        db.apply {
            execSQL("CREATE INDEX IF NOT EXISTS `index_Bookmark_primaryLabelId` ON `Bookmark` (`primaryLabelId`)")
            execSQL("CREATE TABLE IF NOT EXISTS `BooleanSetting` (`key` TEXT NOT NULL, `value` INTEGER NOT NULL, PRIMARY KEY(`key`))");
            execSQL("CREATE TABLE IF NOT EXISTS `StringSetting` (`key` TEXT NOT NULL, `value` TEXT NOT NULL, PRIMARY KEY(`key`))")
            execSQL("CREATE TABLE IF NOT EXISTS `LongSetting` (`key` TEXT NOT NULL, `value` INTEGER NOT NULL, PRIMARY KEY(`key`))")
            execSQL("CREATE TABLE IF NOT EXISTS `DoubleSetting` (`key` TEXT NOT NULL, `value` REAL NOT NULL, PRIMARY KEY(`key`))")
            val sharedPreferences = CommonUtils.realSharedPreferences
            for((k, v) in sharedPreferences.all) {
                when(v) {
                    is Long -> db.insertOr("IGNORE", "LongSetting", "key" to k, "value" to v)
                    is Int -> db.insertOr("IGNORE", "LongSetting", "key" to k, "value" to v)
                    is Boolean -> db.insertOr("IGNORE", "BooleanSetting", "key" to k, "value" to v)
                    is String -> db.insertOr("IGNORE", "StringSetting", "key" to k, "value" to v)
                    is Float -> db.insertOr("IGNORE", "DoubleSetting", "key" to k, "value" to v)
                    is Double -> db.insertOr("IGNORE", "DoubleSetting", "key" to k, "value" to v)
                    else -> {
                        Log.e(TAG, "Illegal value '$k', $v")
                    }
                }
            }
        }
    }
}

private val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun doMigrate(connection: SQLiteConnection) {
        val db = connection
        ReadingPlanDatabaseOperations.instance.onCreate(db)
        ReadingPlanDatabaseOperations.instance.migratePrefsToDatabase(db)
    }
}

val oldMigrations = arrayOf(
    MIGRATION_5_6,
    MIGRATION_37_38_MyNotes_To_Bookmarks,
    MIGRATION_53_54_booleanSettings,
)

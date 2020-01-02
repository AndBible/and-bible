/*
 * Copyright (c) 2020 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
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
/**
 *
 */
package net.bible.service.db.mynote

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase.CONFLICT_FAIL
import android.util.Log
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteQueryBuilder.builder
import net.bible.service.db.DatabaseContainer
import net.bible.service.db.bookmark.BookmarkDatabaseDefinition.BookmarkColumn
import net.bible.service.db.mynote.MyNoteDatabaseDefinition.MyNoteColumn
import org.apache.commons.lang3.StringUtils
import org.crosswire.jsword.passage.Key
import org.crosswire.jsword.passage.NoSuchKeyException
import org.crosswire.jsword.passage.VerseKey
import org.crosswire.jsword.passage.VerseRangeFactory
import org.crosswire.jsword.versification.BibleBook
import org.crosswire.jsword.versification.Versification
import org.crosswire.jsword.versification.system.Versifications
import java.util.*

/**
 * MyNote database update methods
 *
 * @author John D. Lewis [balinjdl at gmail dot com]
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class MyNoteDBAdapter {
    // Variable to hold the database instance
	private val db: SupportSQLiteDatabase = DatabaseContainer.db.openHelper.readableDatabase

    fun insertMyNote(mynote: MyNoteDto): MyNoteDto? { // Create a new row of values to insert.
        Log.d(TAG, "about to insertMyNote: " + mynote.verseRange)
        val verseRange = mynote.verseRange
        val v11nName = getVersification(verseRange)
        // Gets the current system time in milliseconds
        val now = java.lang.Long.valueOf(System.currentTimeMillis())
        val newValues = ContentValues()
        newValues.put(MyNoteColumn.KEY, verseRange.osisRef)
        newValues.put(MyNoteColumn.VERSIFICATION, v11nName)
        newValues.put(MyNoteColumn.MYNOTE, mynote.noteText)
        newValues.put(MyNoteColumn.LAST_UPDATED_ON, now)
        newValues.put(MyNoteColumn.CREATED_ON, now)
        val newId = db.insert(MyNoteDatabaseDefinition.Table.MYNOTE, CONFLICT_FAIL, newValues)
        return getMyNoteDto(newId)
    }

    /**
     * @param key
     * @return
     */
    private fun getVersification(key: Key): String {
        var v11nName = ""
        if (key is VerseKey<*>) { // must save a VerseKey's versification along with the key!
            v11nName = key.versification.name
        }
        return v11nName
    }

    fun updateMyNote(mynote: MyNoteDto): MyNoteDto? { // Create a new row of values to insert.
        Log.d(TAG, "about to updateMyNote: " + mynote.verseRange)
        val verserange = mynote.verseRange
        val v11nName = getVersification(verserange)
        // Gets the current system time in milliseconds
        val now = java.lang.Long.valueOf(System.currentTimeMillis())
        val newValues = ContentValues()
        newValues.put(MyNoteColumn.KEY, verserange.osisRef)
        newValues.put(MyNoteColumn.VERSIFICATION, v11nName)
        newValues.put(MyNoteColumn.MYNOTE, mynote.noteText)
        newValues.put(MyNoteColumn.LAST_UPDATED_ON, now)
        val rowsUpdated = db.update(MyNoteDatabaseDefinition.Table.MYNOTE, CONFLICT_FAIL, newValues, "_id=?", arrayOf(mynote.id.toString())).toLong()
        Log.d(TAG, "Rows updated:$rowsUpdated")
        return getMyNoteDto(mynote.id!!)
    }

    fun removeMyNote(mynote: MyNoteDto): Boolean {
        Log.d(TAG, "Removing my note:" + mynote.verseRange)
        return db.delete(MyNoteDatabaseDefinition.Table.MYNOTE, MyNoteColumn._ID + "=" + mynote.id, null) > 0
    }

    val allMyNotes: List<MyNoteDto>
        get() {
            Log.d(TAG, "about to getAllMyNotes")
            val allMyNotes: MutableList<MyNoteDto> = ArrayList()
            val c = db.query(builder(MyNoteQuery.TABLE).columns(MyNoteQuery.COLUMNS).create())
			c.use { c ->
				if (c.moveToFirst()) {
					while (!c.isAfterLast) {
						val mynote = getMyNoteDto(c)
						allMyNotes.add(mynote)
						c.moveToNext()
					}
				}
			}
            Log.d(TAG, "allMyNotes set to " + allMyNotes.size + " item long list")
            return allMyNotes
        }

    fun getMyNotesInBook(book: BibleBook): List<MyNoteDto> {
        Log.d(TAG, "about to getMyNotesInPassage:" + book.osis)
        val notesList: MutableList<MyNoteDto> = ArrayList()
        val c = db.query(builder(MyNoteQuery.TABLE).columns(MyNoteQuery.COLUMNS).selection(MyNoteColumn.KEY + " LIKE ?", arrayOf(book.osis + ".%")).create())
		c.use { c ->
			if (c.moveToFirst()) {
				while (!c.isAfterLast) {
					val mynote = getMyNoteDto(c)
					notesList.add(mynote)
					c.moveToNext()
				}
			}
		}
        Log.d(TAG, "myNotesInPassage set to " + notesList.size + " item long list")
        return notesList
    }

    private fun getMyNoteDto(id: Long): MyNoteDto? {
        var mynote: MyNoteDto? = null
        val c = db.query(builder(MyNoteQuery.TABLE).columns(MyNoteQuery.COLUMNS).selection(MyNoteColumn._ID + "=?", arrayOf(id.toString())).create())
		c.use { c ->
			if (c.moveToFirst()) {
				mynote = getMyNoteDto(c)
			}
		}
        return mynote
    }

    /**
     * Find bookmark starting at the location specified by key.
     * If it starts there then the initial part of the key should match.
     */
    fun getMyNoteByStartVerse(startVerse: String): MyNoteDto? {
        var mynote: MyNoteDto? = null
        var c: Cursor? = null
        try { // exact match
            c = db.query(builder(MyNoteQuery.TABLE).columns(MyNoteQuery.COLUMNS).selection(MyNoteColumn.KEY + "=?", arrayOf(startVerse)).create())
            if (!c.moveToFirst()) { // start of verse range
                c = db.query(builder(MyNoteQuery.TABLE).columns(MyNoteQuery.COLUMNS).selection(MyNoteColumn.KEY + " LIKE ?", arrayOf("$startVerse-%")).create())
                if (!c.moveToFirst()) {
                    return null
                }
            }
            mynote = getMyNoteDto(c)
        } finally {
            c!!.close()
        }
        return mynote
    }

    /** return Dto from current cursor position or null
     * @param c
     * @return
     * @throws NoSuchKeyException
     */
    private fun getMyNoteDto(c: Cursor?): MyNoteDto {
        val dto = MyNoteDto()
        try { //Id
            val id = c!!.getLong(MyNoteQuery.ID)
            dto.id = id
            //Verse
            val key = c.getString(MyNoteQuery.KEY)
            var v11n: Versification? = null
            if (!c.isNull(MyNoteQuery.VERSIFICATION)) {
                val v11nString = c.getString(MyNoteQuery.VERSIFICATION)
                if (!StringUtils.isEmpty(v11nString)) {
                    v11n = Versifications.instance().getVersification(v11nString)
                }
            }
            if (v11n == null) {
                Log.d(TAG, "Using default Versification")
                // use default v11n
                v11n = Versifications.instance().getVersification(Versifications.DEFAULT_V11N)
            }
            Log.d(TAG, "Versification found:$v11n")
            try {
                dto.verseRange = VerseRangeFactory.fromString(v11n, key)
            } catch (e: Exception) {
                Log.e(TAG, "Note saved with incorrect versification", e)
                // fix problem where KJV was always the v11n even for dc books
// NRSVA should contain most dc books and allow verse to be fetched
                val v11nWithDC = Versifications.instance().getVersification("NRSVA")
                dto.verseRange = VerseRangeFactory.fromString(v11nWithDC, key)
            }
            //Note
            val mynote = c.getString(MyNoteQuery.MYNOTE)
            dto.noteText = mynote
            //Update date
            val updated = c.getLong(MyNoteQuery.LAST_UPDATED_ON)
            dto.lastUpdatedOn = Date(updated)
            //Create date
            val created = c.getLong(MyNoteQuery.CREATED_ON)
            dto.createdOn = Date(created)
        } catch (nke: NoSuchKeyException) {
            Log.e(TAG, "Key error", nke)
        }
        return dto
    }

    private interface MyNoteQuery {
        companion object {
            const val TABLE = MyNoteDatabaseDefinition.Table.MYNOTE
            val COLUMNS = arrayOf(MyNoteColumn._ID, MyNoteColumn.KEY, BookmarkColumn.VERSIFICATION, MyNoteColumn.MYNOTE, MyNoteColumn.LAST_UPDATED_ON, MyNoteColumn.CREATED_ON)
            const val ID = 0
            const val KEY = 1
            const val VERSIFICATION = 2
            const val MYNOTE = 3
            const val LAST_UPDATED_ON = 4
            const val CREATED_ON = 5
        }
    }

    companion object {
        private const val TAG = "MyNoteDBAdapter"
    }
}

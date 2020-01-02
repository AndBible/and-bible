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
package net.bible.service.db.bookmark

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase.CONFLICT_FAIL
import android.util.Log
import androidx.sqlite.db.SupportSQLiteQueryBuilder.builder
import net.bible.android.control.bookmark.BookmarkStyle
import net.bible.android.control.speak.PlaybackSettings.Companion.fromJson
import net.bible.service.db.SQLHelper
import net.bible.service.db.DatabaseContainer
import net.bible.service.db.bookmark.BookmarkDatabaseDefinition.BookmarkColumn
import net.bible.service.db.bookmark.BookmarkDatabaseDefinition.BookmarkLabelColumn
import net.bible.service.db.bookmark.BookmarkDatabaseDefinition.LabelColumn
import org.apache.commons.lang3.StringUtils
import org.crosswire.jsword.passage.NoSuchKeyException
import org.crosswire.jsword.passage.VerseRangeFactory
import org.crosswire.jsword.versification.BibleBook
import org.crosswire.jsword.versification.Versification
import org.crosswire.jsword.versification.system.Versifications
import java.util.*

/**
 * DAO for bookmark, bookmark_label and label tables
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class BookmarkDBAdapter {
	private val db = DatabaseContainer.db.openHelper.readableDatabase

    fun insertOrUpdateBookmark(bookmark: BookmarkDto): BookmarkDto { // Create a new row of values to insert.
        val newValues = ContentValues()
        val key = bookmark.verseRange
        // must save a Key's versification along with the key!
        val v11nName = key.versification.name
        // Gets the current system time in milliseconds
        val now = System.currentTimeMillis()
        newValues.put(BookmarkColumn.KEY, key.osisRef)
        newValues.put(BookmarkColumn.VERSIFICATION, v11nName)
        newValues.put(BookmarkColumn.CREATED_ON, now)
        val playbackSettings = bookmark.playbackSettings
        if (playbackSettings != null) {
            newValues.put(BookmarkColumn.PLAYBACK_SETTINGS, playbackSettings.toJson())
        } else {
            newValues.putNull(BookmarkColumn.PLAYBACK_SETTINGS)
        }
        return if (bookmark.id != null) {
            db.update(BookmarkDatabaseDefinition.Table.BOOKMARK, CONFLICT_FAIL, newValues, BookmarkColumn._ID + "=" + bookmark.id, null)
            bookmark
        } else {
            val newId = db.insert(BookmarkDatabaseDefinition.Table.BOOKMARK, CONFLICT_FAIL, newValues)
            getBookmarkDto(newId)!!
        }
    }

    fun removeBookmark(bookmark: BookmarkDto): Boolean {
        Log.d(TAG, "Removing bookmark:" + bookmark.verseRange)
        return db.delete(BookmarkDatabaseDefinition.Table.BOOKMARK, BookmarkColumn._ID + "=" + bookmark.id, null) > 0
    }

    fun updateBookmarkDate(bookmark: BookmarkDto): BookmarkDto? { // Create a new row of values to insert.
		val bookmarkId = bookmark.id
		if(bookmarkId == null) {
			Log.e(TAG, "updateBookmarkDate failed, as bookmark id is null")
			return null
		}
        val newValues = ContentValues()
        // Gets the current system time in milliseconds
        val now = System.currentTimeMillis()
        newValues.put(BookmarkColumn.CREATED_ON, now)
        db.update(BookmarkDatabaseDefinition.Table.BOOKMARK, CONFLICT_FAIL, newValues, BookmarkColumn._ID + "=" + bookmarkId, null)
        return getBookmarkDto(bookmarkId)
    }

    fun removeLabel(label: LabelDto): Boolean {
        Log.d(TAG, "Removing label:" + label.name)
        return db.delete(BookmarkDatabaseDefinition.Table.LABEL, LabelColumn._ID + "=" + label.id, null) > 0
    }

    fun insertLabel(label: LabelDto): LabelDto { // Create a new row of values to insert.
        val newValues = ContentValues()
        newValues.put(LabelColumn.NAME, label.name)
        newValues.put(LabelColumn.BOOKMARK_STYLE, label.bookmarkStyleAsString)
        val newId = db.insert(BookmarkDatabaseDefinition.Table.LABEL, CONFLICT_FAIL, newValues)
        return getLabelDto(newId)!!
    }

    fun updateLabel(label: LabelDto): LabelDto { // Create a new row of values to insert.
        val newValues = ContentValues()
        newValues.put(LabelColumn.NAME, label.name)
        newValues.put(LabelColumn.BOOKMARK_STYLE, label.bookmarkStyleAsString)
        db.update(BookmarkDatabaseDefinition.Table.LABEL, CONFLICT_FAIL, newValues, "_id=?", arrayOf(label.id.toString())).toLong()
        return label
    }

    fun removeBookmarkLabelJoin(bookmark: BookmarkDto, label: LabelDto): Boolean {
        return db.delete(BookmarkDatabaseDefinition.Table.BOOKMARK_LABEL, BookmarkLabelColumn.BOOKMARK_ID + "=" + bookmark.id + " AND " + BookmarkLabelColumn.LABEL_ID + "=" + label.id, null) > 0
    }

    val allBookmarks: List<BookmarkDto>
        get() {
            val allBookmarks: MutableList<BookmarkDto> = ArrayList()
			val c = db.query(builder(BookmarkQuery.TABLE).columns(BookmarkQuery.COLUMNS).create())
			c.use { c ->
				if (c.moveToFirst()) {
					while (!c.isAfterLast) {
						val bookmark = getBookmarkDto(c)
						allBookmarks.add(bookmark)
						c.moveToNext()
					}
				}
			}
            return allBookmarks
        }

    fun getBookmarksInBook(book: BibleBook): List<BookmarkDto> {
        Log.d(TAG, "about to getBookmarksInPassage:" + book.osis)
        val bookmarkList: MutableList<BookmarkDto> = ArrayList()
		val c = db.query(builder(BookmarkQuery.TABLE).columns(BookmarkQuery.COLUMNS).selection(BookmarkColumn.KEY + " LIKE ?", arrayOf(book.osis + ".%")).create())
		c.use { c ->
			if (c.moveToFirst()) {
				while (!c.isAfterLast) {
					val bookmark = getBookmarkDto(c)
					bookmarkList.add(bookmark)
					c.moveToNext()
				}
			}
		}
        Log.d(TAG, "bookmarksInPassage set to " + bookmarkList.size + " item long list")
        return bookmarkList
    }

    fun getBookmarksWithLabel(label: LabelDto): List<BookmarkDto> {
        val sql = "SELECT " + SQLHelper.getColumnsForQuery(BookmarkQuery.TABLE, BookmarkQuery.COLUMNS) +
            " FROM bookmark " +
            "JOIN bookmark_label ON (bookmark._id = bookmark_label.bookmark_id) " +
            "JOIN label ON (bookmark_label.label_id = label._id) " +
            "WHERE label._id = ? "
        val allBookmarks: MutableList<BookmarkDto> = ArrayList()
        val args = arrayOf(label.id.toString())
        val c = db.query(sql, args)
		c.use { c ->
			if (c.moveToFirst()) {
				while (!c.isAfterLast) {
					val bookmark = getBookmarkDto(c)
					allBookmarks.add(bookmark)
					c.moveToNext()
				}
			}
		}
        return allBookmarks
    }

    val unlabelledBookmarks: List<BookmarkDto>
        get() {
            val sql = "SELECT " + SQLHelper.getColumnsForQuery(BookmarkQuery.TABLE, BookmarkQuery.COLUMNS) +
                " FROM bookmark " +
                " WHERE NOT EXISTS (SELECT * FROM bookmark_label WHERE bookmark._id = bookmark_label.bookmark_id)"
            val bookmarks: MutableList<BookmarkDto> = ArrayList()
            val c = db.query(sql, null)
			c.use { c ->
				if (c.moveToFirst()) {
					while (!c.isAfterLast) {
						val bookmark = getBookmarkDto(c)
						bookmarks.add(bookmark)
						c.moveToNext()
					}
				}
			}
            return bookmarks
        }

    val allLabels: List<LabelDto>
        get() {
            val allLabels: MutableList<LabelDto> = ArrayList()
			val c = db.query(builder(LabelQuery.TABLE).columns(LabelQuery.COLUMNS).orderBy(LabelColumn.NAME).create())
			c.use { c ->
				if (c.moveToFirst()) {
					while (!c.isAfterLast) {
						val bookmark = getLabelDto(c)
						allLabels.add(bookmark)
						c.moveToNext()
					}
				}
			}
            return allLabels
        }

    fun getBookmarkLabels(bookmark: BookmarkDto): List<LabelDto> {
        val sql = "SELECT label._id, label.name, label.bookmark_style " +
            "FROM label " +
            "JOIN bookmark_label ON (label._id = bookmark_label.label_id) " +
            "JOIN bookmark ON (bookmark_label.bookmark_id = bookmark._id) " +
            "WHERE bookmark._id = ?"
        val labels: MutableList<LabelDto> = ArrayList()
        val args = arrayOf(bookmark.id.toString())
        val c = db.query(sql, args)
		c.use { c ->
			if (c.moveToFirst()) {
				while (!c.isAfterLast) {
					val label = getLabelDto(c)
					labels.add(label)
					c.moveToNext()
				}
			}
		}
        return labels
    }

    fun insertBookmarkLabelJoin(bookmark: BookmarkDto, label: LabelDto) { // Create a new row of values to insert.
        val newValues = ContentValues()
        newValues.put(BookmarkLabelColumn.BOOKMARK_ID, bookmark.id)
        newValues.put(BookmarkLabelColumn.LABEL_ID, label.id)
        //long newId =
        db.insert(BookmarkDatabaseDefinition.Table.BOOKMARK_LABEL, CONFLICT_FAIL, newValues)
    }

    fun getBookmarkDto(id: Long): BookmarkDto? {
        var bookmark: BookmarkDto? = null
		//val c = db2.query(BookmarkQuery.TABLE, BookmarkQuery.COLUMNS, BookmarkColumn._ID + "=?", arrayOf(id.toString()), null, null, null)
		val c = db.query(builder(BookmarkQuery.TABLE).columns(BookmarkQuery.COLUMNS).selection(BookmarkColumn._ID + "=?", arrayOf(id.toString())).create())
		c.use { c ->
			if (c.moveToFirst()) {
				bookmark = getBookmarkDto(c)
			}
		}
        return bookmark
    }

    /**
     * Find bookmark starting at the location specified by key.
     * If it starts there then the initial part of the key should match.
     */
    fun getBookmarkByStartKey(key: String): BookmarkDto? {
        var bookmark: BookmarkDto? = null
        var c: Cursor? = null
        try { // exact match
			c = db.query(builder(BookmarkQuery.TABLE).columns(BookmarkQuery.COLUMNS).selection(BookmarkColumn.KEY + "=?", arrayOf(key)).create())
            if (!c.moveToFirst()) { // start of verse range
				c = db.query(builder(BookmarkQuery.TABLE).columns(BookmarkQuery.COLUMNS).selection(BookmarkColumn.KEY + " LIKE ?", arrayOf("$key-%")).create())
                if (!c.moveToFirst()) {
                    return null
                }
            }
            bookmark = getBookmarkDto(c)
        } finally {
            c!!.close()
        }
        return bookmark
    }

    /** return Dto from current cursor position or null
     */
    private fun getBookmarkDto(c: Cursor?): BookmarkDto {
        val dto = BookmarkDto()
        try { //Id
            val id = c!!.getLong(BookmarkQuery.ID)
            dto.id = id
            //Verse
            val key = c.getString(BookmarkQuery.KEY)
            var v11n: Versification? = null
            if (!c.isNull(BookmarkQuery.VERSIFICATION)) {
                val v11nString = c.getString(BookmarkQuery.VERSIFICATION)
                if (!StringUtils.isEmpty(v11nString)) {
                    v11n = Versifications.instance().getVersification(v11nString)
                }
            }
            if (v11n == null) { // use default v11n
                v11n = Versifications.instance().getVersification(Versifications.DEFAULT_V11N)
            }
            dto.verseRange = VerseRangeFactory.fromString(v11n, key)
            //Created date
            val created = c.getLong(BookmarkQuery.CREATED_ON)
            dto.createdOn = Date(created)
            val playbackSettingsStr = c.getString(BookmarkQuery.PLAYBACK_SETTINGS)
            if (playbackSettingsStr != null) {
                dto.playbackSettings = fromJson(playbackSettingsStr)
            }
        } catch (nke: NoSuchKeyException) {
            Log.e(TAG, "Key error", nke)
        }
        return dto
    }

    private fun getLabelDto(id: Long): LabelDto? {
        var label: LabelDto? = null
		val c = db.query(builder(LabelQuery.TABLE).columns(LabelQuery.COLUMNS).selection(LabelColumn._ID + "=?", arrayOf(id.toString())).create())
		c.use { c ->
			if (c.moveToFirst()) {
				label = getLabelDto(c)
			}
		}
        return label
    }

    /** return Dto from current cursor position or null
     */
    private fun getLabelDto(c: Cursor): LabelDto {
        val dto = LabelDto()
        val id = c.getLong(LabelQuery.ID)
        dto.id = id
        val name = c.getString(LabelQuery.NAME)
        dto.name = name
        val style = c.getString(LabelQuery.BOOKMARK_STYLE)
        dto.setBookmarkStyleFromString(style)
        return dto
    }

    val orCreateSpeakLabel: LabelDto
        get() {
            var label: LabelDto? = null
			val c = db.query(builder(LabelQuery.TABLE).columns(LabelQuery.COLUMNS).selection(LabelColumn.BOOKMARK_STYLE + "=?", arrayOf(BookmarkStyle.SPEAK.toString())).create())
			c.use { c ->
				if (c.moveToFirst()) {
					label = getLabelDto(c)
				}
			}
            if (label == null) {
                label = LabelDto().apply {
					bookmarkStyle = BookmarkStyle.SPEAK
				}
                label = insertLabel(label!!)
            }
            return label!!
        }

    private interface BookmarkQuery {
        companion object {
            const val TABLE = BookmarkDatabaseDefinition.Table.BOOKMARK
            val COLUMNS = arrayOf(
				BookmarkColumn._ID, BookmarkColumn.KEY, BookmarkColumn.VERSIFICATION,
				BookmarkColumn.CREATED_ON, BookmarkColumn.PLAYBACK_SETTINGS
			)
            const val ID = 0
            const val KEY = 1
            const val VERSIFICATION = 2
            const val CREATED_ON = 3
            const val PLAYBACK_SETTINGS = 4
        }
    }

    private interface LabelQuery {
        companion object {
            const val TABLE = BookmarkDatabaseDefinition.Table.LABEL
            val COLUMNS = arrayOf(LabelColumn._ID, LabelColumn.NAME, LabelColumn.BOOKMARK_STYLE)
            const val ID = 0
            const val NAME = 1
            const val BOOKMARK_STYLE = 2
        }
    }

    companion object {
        private const val TAG = "BookmarkDBAdapter"
    }
}

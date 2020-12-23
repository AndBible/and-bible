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
package net.bible.android.database

import android.util.Base64
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import net.bible.android.database.bookmarks.BookmarkDao
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.android.database.bookmarks.BookmarkStyle
import net.bible.android.database.bookmarks.PlaybackSettings
import net.bible.android.database.readingplan.ReadingPlanDao
import net.bible.android.database.readingplan.ReadingPlanEntities
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.passage.Key
import org.crosswire.jsword.passage.VerseRange
import org.crosswire.jsword.passage.VerseRangeFactory
import org.crosswire.jsword.versification.Versification
import org.crosswire.jsword.versification.system.Versifications
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.StreamCorruptedException

import java.util.*

const val DATABASE_VERSION = 35

class Converters {
    @TypeConverter
    fun toBookmarkStyle(value: String?) = if(value==null) null else BookmarkStyle.valueOf(value)

    @TypeConverter
    fun fromBookmarkStyle(value: BookmarkStyle?) = value?.name

    @TypeConverter
    fun fromTimestamp(value: Long): Date = Date(value)

    @TypeConverter
    fun dateToTimestamp(date: Date): Long = date.time

    @TypeConverter
    fun verseRangeToStr(v: VerseRange?): String? =
        if (v!=null) "${v.versification.name}::${v.osisRef}" else null

    @TypeConverter
    fun strToVerseRange(s: String?): VerseRange? {
        if(s == null) return null
        val splitted = s.split("::")
        val v11n = Versifications.instance().getVersification(splitted[0])
        return VerseRangeFactory.fromString(v11n, splitted[1])
    }

    @TypeConverter
    fun versificationToStr(v: Versification): String = v.name

    @TypeConverter
    fun strToVersification(s: String): Versification {
        return Versifications.instance().getVersification(s)
    }

    @TypeConverter
    fun bookToStr(v: Book?): String? = v?.initials

    @TypeConverter
    fun strToBook(s: String?): Book? = s?.let { Books.installed().getBook(s) }

    @TypeConverter
    fun playbackSettingsToStr(p: PlaybackSettings?): String? {
        return p?.toJson()
    }

    @TypeConverter
    fun strToPlaybackSettings(s: String?): PlaybackSettings? {
        return if (s != null) PlaybackSettings.fromJson(s) else null
    }

    @TypeConverter
    fun keyToStr(key: Key?): String? {
        if(key == null) return null
        val out = ByteArrayOutputStream()
        val obj = ObjectOutputStream(out)
        obj.writeObject(key)
        return Base64.encodeToString(out.toByteArray(), Base64.DEFAULT)
    }

    @TypeConverter
    fun strToKey(s: String?): Key? {
        if(s == null) return null
        try {
            val inp = ByteArrayInputStream(Base64.decode(s, Base64.DEFAULT))
            val obj = ObjectInputStream(inp)
            return obj.readObject() as Key
        } catch (e: StreamCorruptedException) {
            return null
        }
    }
}

@Database(
    entities = [
        BookmarkEntities.Bookmark::class,
        BookmarkEntities.Label::class,
        BookmarkEntities.BookmarkToLabel::class,
        MyNote::class,
        ReadingPlanEntities.ReadingPlan::class,
        ReadingPlanEntities.ReadingPlanStatus::class,
        WorkspaceEntities.Workspace::class,
        WorkspaceEntities.Window::class,
        WorkspaceEntities.HistoryItem::class,
        WorkspaceEntities.PageManager::class,
        Document::class
    ],
    version = DATABASE_VERSION
)
@TypeConverters(Converters::class)
abstract class AppDatabase: RoomDatabase() {
    abstract fun readingPlanDao(): ReadingPlanDao
    abstract fun workspaceDao(): WorkspaceDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun documentDao(): DocumentDao

    fun sync() { // Sync all data so far into database file
        val cur = openHelper.writableDatabase
            .query("PRAGMA wal_checkpoint(FULL)")
        cur.moveToFirst()
        cur.close()
    }
}

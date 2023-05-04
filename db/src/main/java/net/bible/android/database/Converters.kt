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

package net.bible.android.database


import android.util.Base64
import androidx.room.TypeConverter
import kotlinx.serialization.serializer
import net.bible.android.database.bookmarks.BookmarkStyle
import net.bible.android.database.bookmarks.BookmarkType
import net.bible.android.database.bookmarks.LabelType
import net.bible.android.database.bookmarks.PlaybackSettings
import net.bible.android.database.bookmarks.SpeakSettings
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.basic.AbstractPassageBook
import org.crosswire.jsword.passage.Key
import org.crosswire.jsword.passage.VerseRange
import org.crosswire.jsword.passage.VerseRangeFactory
import org.crosswire.jsword.versification.Versification
import org.crosswire.jsword.versification.system.Versifications
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

import java.util.*

class Converters {
    @TypeConverter
    fun toLabelType(value: String?) = if(value==null) null else LabelType.valueOf(value)

    @TypeConverter
    fun fromLabelType(value: LabelType?) = value?.name

    @TypeConverter
    fun toBookmarkType(value: String?) = if(value==null) null else BookmarkType.valueOf(value)

    @TypeConverter
    fun fromBookmarkType(value: BookmarkType?) = value?.name


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
    fun bookToStr(v: AbstractPassageBook?): String? = v?.initials

    @TypeConverter
    fun strToBook(s: String?): AbstractPassageBook? = s?.let {
        val book = Books.installed().getBook(s)
        if(book is AbstractPassageBook) book else null
    }

    @TypeConverter
    fun speakSettingsToStr(p: SpeakSettings?): String? {
        return p?.toJson()
    }

    @TypeConverter
    fun strToSpeakSettings(s: String?): SpeakSettings? {
        return if (s != null) SpeakSettings.fromJson(s) else null
    }


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
        return try {
            val inp = ByteArrayInputStream(Base64.decode(s, Base64.DEFAULT))
            val obj = ObjectInputStream(inp)
            obj.readObject() as Key
        } catch (e: Exception) {
            null
        }
    }

    @TypeConverter
    fun strToList1(s: String?): List<Long>? {
        if(s == null) return null
        return json.decodeFromString(serializer(), s)
    }

    @TypeConverter
    fun listToStr1(obj: List<Long>?): String? {
        if(obj == null) return null
        return json.encodeToString(serializer(), obj)
    }

    @TypeConverter
    fun strToList2(s: String?): MutableList<WorkspaceEntities.RecentLabel> {
        if(s == null) return mutableListOf()
        return json.decodeFromString(serializer(), s)
    }

    @TypeConverter
    fun listToStr2(obj: List<WorkspaceEntities.RecentLabel>?): String? {
        if(obj == null) return null
        return json.encodeToString(serializer(), obj)
    }

    @TypeConverter
    fun strToSet1(s: String?): MutableSet<Long> {
        if(s == null) return mutableSetOf()
        return json.decodeFromString(serializer(), s)
    }

    @TypeConverter
    fun setToStr1(obj: Set<Long>?): String? {
        if(obj == null) return null
        return json.encodeToString(serializer(), obj)
    }

    @TypeConverter
    fun strToSet2(s: String?): MutableSet<String> {
        if(s == null) return mutableSetOf()
        return json.decodeFromString(serializer(), s)
    }

    @TypeConverter
    fun setToStr2(obj: Set<String>?): String? {
        if(obj == null) return null
        return json.encodeToString(serializer(), obj)
    }
}

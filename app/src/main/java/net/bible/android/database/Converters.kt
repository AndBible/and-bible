/*
 * Copyright (c) 2023-2026 Martin Denham, Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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
import android.util.Log
import androidx.room3.ColumnTypeConverter
import kotlinx.serialization.SerializationException
import kotlinx.serialization.serializer
import net.bible.android.database.bookmarks.BookmarkStyle
import net.bible.android.database.bookmarks.BookmarkType
import net.bible.android.database.bookmarks.LabelType
import net.bible.android.database.bookmarks.TextContentType
import net.bible.android.database.bookmarks.PlaybackSettings
import net.bible.android.database.bookmarks.SpeakSettings
import net.bible.sharedcore.bookmark.BookmarkDisplayStyle
import net.bible.android.database.progress.ReadingSource
import net.bible.service.llm.AgentTool
import net.bible.service.llm.ApiFormat
import net.bible.service.llm.PromptContext
import net.bible.service.llm.agent.PermissionMode
import org.crosswire.jsword.book.Book
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
import java.util.Date


class Converters {
    @ColumnTypeConverter
    fun toLabelType(value: String?) = if(value==null) null else LabelType.valueOf(value)

    @ColumnTypeConverter
    fun idTypeToBlob(value: IdType?): ByteArray? = value?.toByteArray()

    @ColumnTypeConverter
    fun blobToIdType(value: ByteArray?): IdType? = IdType.fromByteArray(value)

    @ColumnTypeConverter
    fun fromLabelType(value: LabelType?) = value?.name

    @ColumnTypeConverter
    fun toBookmarkType(value: String?) = if(value==null) null else BookmarkType.valueOf(value)

    @ColumnTypeConverter
    fun fromBookmarkType(value: BookmarkType?) = value?.name


    @ColumnTypeConverter
    fun toBookmarkStyle(value: String?) = if(value==null) null else BookmarkStyle.valueOf(value)

    @ColumnTypeConverter
    fun fromBookmarkStyle(value: BookmarkStyle?) = value?.name

    /**
     * `Label.displayStyle` / `displayStyleWholeVerse`, stored as the enum's ordinal so the label's
     * own style and `WorkspaceLabelOverride.overrideMode` share one encoding (0..3). Unlike the
     * name-string converters above, this one is deliberately numeric — see
     * `docs/superpowers/specs/2026-08-19-label-style-schema-migration-design.md` §4.
     * `null` means "inherit the selection style" and is preserved as `null`.
     */
    @ColumnTypeConverter
    fun fromBookmarkDisplayStyle(value: BookmarkDisplayStyle?): Int? = value?.ordinal

    @ColumnTypeConverter
    fun toBookmarkDisplayStyle(value: Int?): BookmarkDisplayStyle? =
        if (value == null) null else BookmarkDisplayStyle.entries.getOrNull(value) ?: BookmarkDisplayStyle.HIGHLIGHT

    @ColumnTypeConverter
    fun toTextContentType(value: String?) = if(value==null) null else TextContentType.valueOf(value)

    @ColumnTypeConverter
    fun fromTextContentType(value: TextContentType?) = value?.name

    @ColumnTypeConverter
    fun fromTimestamp(value: Long): Date = Date(value)

    @ColumnTypeConverter
    fun dateToTimestamp(date: Date): Long = date.time

    @ColumnTypeConverter
    fun verseRangeToStr(v: VerseRange?): String? =
        if (v!=null) "${v.versification.name}::${v.getOsisRef()}" else null

    @ColumnTypeConverter
    fun strToVerseRange(s: String?): VerseRange? {
        if(s == null) return null
        val splitted = s.split("::")
        val v11n = Versifications.instance().getVersification(splitted[0])
        return VerseRangeFactory.fromString(v11n, splitted[1])
    }

    @ColumnTypeConverter
    fun versificationToStr(v: Versification): String = v.name

    @ColumnTypeConverter
    fun strToVersification(s: String): Versification {
        return Versifications.instance().getVersification(s)!!
    }

    @ColumnTypeConverter
    fun bookToStr(v: AbstractPassageBook?): String? = v?.initials

    @ColumnTypeConverter
    fun strToBook(s: String?): AbstractPassageBook? = s?.let {
        val book = Books.installed().getBook(s)
        if(book is AbstractPassageBook) book else null
    }

    @ColumnTypeConverter
    fun bookToStr2(v: Book?): String? = v?.initials

    @ColumnTypeConverter
    fun strToBook2(s: String?): Book? = s?.let {
        return Books.installed().getBook(s)
    }


    @ColumnTypeConverter
    fun speakSettingsToStr(p: SpeakSettings?): String? {
        return p?.toJson()
    }

    @ColumnTypeConverter
    fun strToSpeakSettings(s: String?): SpeakSettings? {
        return if (s != null) SpeakSettings.fromJson(s) else null
    }


    @ColumnTypeConverter
    fun playbackSettingsToStr(p: PlaybackSettings?): String? {
        return p?.toJson()
    }

    @ColumnTypeConverter
    fun strToPlaybackSettings(s: String?): PlaybackSettings? {
        return if (s != null) PlaybackSettings.fromJson(s) else null
    }

    @ColumnTypeConverter
    fun keyToStr(key: Key?): String? {
        if(key == null) return null
        val out = ByteArrayOutputStream()
        val obj = ObjectOutputStream(out)
        obj.writeObject(key)
        return Base64.encodeToString(out.toByteArray(), Base64.DEFAULT)
    }

    @ColumnTypeConverter
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

    @ColumnTypeConverter
    fun strToList4(s: String?): List<IdType>? {
        if(s == null) return null
        return try { json.decodeFromString(serializer(), s) } catch(e: SerializationException) {
            Log.e("Converters", "Error in deserializing $s", e)
            null
        }
    }

    @ColumnTypeConverter
    fun listToStr4(obj: List<IdType>?): String? {
        if(obj == null) return null
        return json.encodeToString(serializer(), obj)
    }


    @ColumnTypeConverter
    fun strToList1(s: String?): List<Long>? {
        if(s == null) return null
        return try { json.decodeFromString(serializer(), s) } catch(e: SerializationException) {
            Log.e("Converters", "Error in deserializing $s", e)
            null
        }
    }

    @ColumnTypeConverter
    fun listToStr1(obj: List<Long>?): String? {
        if(obj == null) return null
        return json.encodeToString(serializer(), obj)
    }

    @ColumnTypeConverter
    fun strToList3(s: String?): List<String>? {
        if(s == null) return null
        return try { json.decodeFromString(serializer(), s) } catch(e: SerializationException) {
            Log.e("Converters", "Error in deserializing $s", e)
            null
        }
    }

    @ColumnTypeConverter
    fun listToStr3(obj: List<String>?): String? {
        if(obj == null) return null
        return json.encodeToString(serializer(), obj)
    }

    @ColumnTypeConverter
    fun strToList2(s: String?): MutableList<WorkspaceEntities.RecentLabel> {
        if(s == null) return mutableListOf()
        return try { json.decodeFromString(serializer(), s) } catch(e: SerializationException) {
            Log.e("Converters", "Error in deserializing $s", e)
            mutableListOf()
        }
    }

    @ColumnTypeConverter
    fun listToStr2(obj: List<WorkspaceEntities.RecentLabel>?): String? {
        if(obj == null) return null
        return json.encodeToString(serializer(), obj)
    }

    @ColumnTypeConverter
    fun strToSet3(s: String?): MutableSet<IdType> {
        if(s == null) return mutableSetOf()
        return try { json.decodeFromString(serializer(), s) } catch(e: SerializationException) {
            Log.e("Converters", "Error in deserializing $s", e)
            mutableSetOf()
        }
    }

    @ColumnTypeConverter
    fun setToStr3(obj: Set<IdType>?): String? {
        if(obj == null) return null
        return json.encodeToString(serializer(), obj)
    }

    @ColumnTypeConverter
    fun strToSet1(s: String?): MutableSet<Long> {
        if(s == null) return mutableSetOf()
        return try { json.decodeFromString(serializer(), s) } catch(e: SerializationException) {
            Log.e("Converters", "Error in deserializing $s", e)
            mutableSetOf()
        }
    }

    @ColumnTypeConverter
    fun setToStr1(obj: Set<Long>?): String? {
        if(obj == null) return null
        return json.encodeToString(serializer(), obj)
    }

    @ColumnTypeConverter
    fun strToSet2(s: String?): MutableSet<String> {
        if(s == null) return mutableSetOf()
        return try { json.decodeFromString(serializer(), s) } catch(e: SerializationException) {
            Log.e("Converters", "Error in deserializing $s", e)
            mutableSetOf()
        }
    }

    @ColumnTypeConverter
    fun setToStr2(obj: Set<String>?): String? {
        if(obj == null) return null
        return json.encodeToString(serializer(), obj)
    }

    @ColumnTypeConverter
    fun strToMapIdTypeInt(s: String?): MutableMap<IdType, Int> {
        if(s == null) return mutableMapOf()
        return try { json.decodeFromString(serializer(), s) } catch(e: SerializationException) {
            Log.e("Converters", "Error in deserializing $s", e)
            mutableMapOf()
        }
    }

    @ColumnTypeConverter
    fun mapIdTypeIntToStr(obj: Map<IdType, Int>?): String? {
        if(obj == null) return null
        return json.encodeToString(serializer(), obj)
    }

    @ColumnTypeConverter
    fun strToMapIdTypeNullableIdType(s: String?): Map<IdType, IdType?>? {
        if(s == null) return null
        return try { json.decodeFromString(serializer(), s) } catch(e: SerializationException) {
            Log.e("Converters", "Error in deserializing Map<IdType, IdType?>: $s", e)
            null
        }
    }

    @ColumnTypeConverter
    fun mapIdTypeNullableIdTypeToStr(obj: Map<IdType, IdType?>?): String? {
        if(obj == null) return null
        return json.encodeToString(serializer(), obj)
    }

    @ColumnTypeConverter
    fun strToAgentToolSet(s: String?): Set<AgentTool>? {
        if(s == null) return null
        return try { json.decodeFromString(serializer(), s) } catch(e: SerializationException) {
            Log.e("Converters", "Error in deserializing AgentTool set: $s", e)
            null
        }
    }

    @ColumnTypeConverter
    fun agentToolSetToStr(obj: Set<AgentTool>?): String? {
        if(obj == null) return null
        return json.encodeToString(serializer(), obj)
    }

    @ColumnTypeConverter
    fun strToPromptContextSet(s: String?): Set<PromptContext> {
        if(s == null) return emptySet()
        return try { json.decodeFromString(serializer(), s) } catch(e: SerializationException) {
            Log.e("Converters", "Error in deserializing PromptContext set: $s", e)
            emptySet()
        }
    }

    @ColumnTypeConverter
    fun promptContextSetToStr(obj: Set<PromptContext>?): String? {
        if(obj == null) return null
        return json.encodeToString(serializer(), obj)
    }

    @ColumnTypeConverter
    fun strToApiFormat(s: String?): ApiFormat? {
        if (s == null) return null
        return try { ApiFormat.valueOf(s) } catch (_: IllegalArgumentException) { null }
    }

    @ColumnTypeConverter
    fun apiFormatToStr(obj: ApiFormat?): String? = obj?.name

    @ColumnTypeConverter
    fun strToPermissionMode(s: String?): PermissionMode? {
        if(s == null) return null
        return try { PermissionMode.valueOf(s) } catch(e: IllegalArgumentException) { null }
    }

    @ColumnTypeConverter
    fun permissionModeToStr(obj: PermissionMode?): String? = obj?.name

    @ColumnTypeConverter
    fun toReadingSource(value: String?): ReadingSource? {
        if (value == null) return null
        return try { ReadingSource.valueOf(value) } catch (_: IllegalArgumentException) { null }
    }

    @ColumnTypeConverter
    fun fromReadingSource(value: ReadingSource?) = value?.name
}

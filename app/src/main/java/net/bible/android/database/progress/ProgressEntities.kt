/*
 * Copyright (c) 2024-2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.android.database.progress

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import net.bible.android.database.IdType
import net.bible.android.database.bookmarks.KJVA
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.passage.VerseRange

enum class ReadingSource {
    MANUAL, AUTO_SCROLL, AUTO_TTS,
}

@Entity(
    indices = [
        Index(value = ["kjvOrdinal"], unique = true)
    ]
)
data class MemorizedVerse(
    @PrimaryKey var id: IdType = IdType(),
    val kjvOrdinal: Int,
    val memorizedAt: Long = System.currentTimeMillis(),
) {
    @get:Ignore
    val verse: Verse
        get() = Verse(KJVA, kjvOrdinal)
}

@Entity
data class MemorizationTarget(
    @PrimaryKey var id: IdType = IdType(),
    val kjvOrdinalStart: Int,
    val kjvOrdinalEnd: Int,
    val createdAt: Long = System.currentTimeMillis(),
) {
    @get:Ignore
    val verseRange: VerseRange
        get() = VerseRange(KJVA, Verse(KJVA, kjvOrdinalStart), Verse(KJVA, kjvOrdinalEnd))

    @get:Ignore
    val verseCount: Int
        get() = kjvOrdinalEnd - kjvOrdinalStart + 1
}

@Entity(
    indices = [
        Index(value = ["kjvBookOrdinal", "chapter", "cycle"])
    ]
)
data class ChapterReadHistory(
    @PrimaryKey var id: IdType = IdType(),
    val kjvBookOrdinal: Int,
    val chapter: Int,
    val cycle: Int = 1,
    val readAt: Long = System.currentTimeMillis(),
    /** The SWORD book initials of the Bible version used when this chapter was tapped (empty for migrated records). */
    val bookInitials: String = "",
    /** How this read was recorded — manual tap or one of the auto-track sources. Defaults to MANUAL for migrated/legacy callers. */
    @ColumnInfo(defaultValue = "MANUAL") val source: ReadingSource = ReadingSource.MANUAL,
)

@Entity
data class GlobalReadingProgressSettings(
    @PrimaryKey val id: IdType = SINGLETON_ID,
    @ColumnInfo(defaultValue = "0") val autoTrackReading: Boolean = false,
    @ColumnInfo(defaultValue = "1") val autoMarkMemorized: Boolean = true,
    @ColumnInfo(defaultValue = "0") val memorizeTypeFullWords: Boolean = false,
    @ColumnInfo(defaultValue = "light") val memorizeWordVisibility: String = "light",
    @ColumnInfo(defaultValue = "1") val memorizeErrorHeatmap: Boolean = true,
    @ColumnInfo(defaultValue = "0") val memorizeScrambleHideUsed: Boolean = false,
    @ColumnInfo(defaultValue = "1") val memorizeIncludeReference: Boolean = true,
    @ColumnInfo(defaultValue = "0") val activeCycle: Int = 0,
) {
    companion object {
        val SINGLETON_ID = IdType.fromString("b2000000-0000-0000-0000-000000000001")
    }
}

/*
 * Copyright (c) 2024 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import net.bible.android.database.IdType

enum class ReadingSource {
    MANUAL, AUTO_SCROLL, AUTO_TTS
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
)

@Entity(
    indices = [
        Index(value = ["kjvBookOrdinal", "chapter", "cycle"], unique = true)
    ]
)
data class ChapterReadingRecord(
    @PrimaryKey var id: IdType = IdType(),
    val kjvBookOrdinal: Int,
    val chapter: Int,
    val cycle: Int = 1,
    val readAt: Long = System.currentTimeMillis(),
    val source: ReadingSource = ReadingSource.MANUAL,
)

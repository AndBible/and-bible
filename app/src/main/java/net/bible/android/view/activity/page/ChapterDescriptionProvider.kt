/*
 * Copyright (c) 2026 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.android.view.activity.page

import androidx.annotation.StringRes
import net.bible.android.activity.R
import org.crosswire.jsword.versification.BibleBook

data class ChapterDescription(
    @param:StringRes val titleResId: Int,
    @param:StringRes val bodyResId: Int,
)

object ChapterDescriptionProvider {
    private val descriptions = mapOf(
        Pair(BibleBook.GEN, 1) to ChapterDescription(
            R.string.chapter_description_mock_gen_1_title,
            R.string.chapter_description_mock_gen_1_body
        ),
        Pair(BibleBook.GEN, 2) to ChapterDescription(
            R.string.chapter_description_mock_gen_2_title,
            R.string.chapter_description_mock_gen_2_body
        ),
    )

    fun find(book: BibleBook, chapter: Int): ChapterDescription? = descriptions[Pair(book, chapter)]
}

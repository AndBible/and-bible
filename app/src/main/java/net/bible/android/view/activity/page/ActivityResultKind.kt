/*
 * Copyright (c) 2020-2026 Martin Denham, Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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

/**
 * What kind of result an activity handed back to [MainBibleActivity], carried as an explicit extra
 * on the result Intent.
 *
 * This replaces dispatch on `data.component?.className`, which coupled the reading view to the
 * CLASSIC Activity classes: every Compose screen had to build a result Intent naming its classic
 * counterpart purely so the name comparison would match (eleven such spoof Intents existed). The
 * class-name channel also did not mean what it said — `ChooseDictionaryWordComposeActivity` declared
 * itself `ChooseGeneralBookKey` because the two share a result shape. This enum names the result
 * shape directly, which is what the dispatch actually branches on.
 *
 * One entry per branch of [MainBibleActivity]'s `STD_REQUEST_CODE` dispatch. [PassageGrid],
 * [Bookmarks] and [ReadingProgress] share a branch but must stay distinguishable inside it: the
 * memorize path is selected by [ReadingProgress] and `isFromBookmark` by [Bookmarks].
 */
enum class ActivityResultKind {
    ChooseDocument,
    MyDocuments,
    MyDocumentPages,
    PassageGrid,
    Bookmarks,
    ReadingProgress,
    GenBookKey,
    ;

    companion object {
        /** Result-Intent extra key carrying an [ActivityResultKind.name]. */
        const val EXTRA = "abResultKind"

        /** The kind [value] names, or `null` for an absent or unrecognised value. */
        fun fromExtra(value: String?): ActivityResultKind? = entries.firstOrNull { it.name == value }
    }
}

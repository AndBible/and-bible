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
package net.bible.android.control.versification.sort

import net.bible.android.control.versification.toV11n
import net.bible.android.database.bookmarks.BookmarkEntities.Bookmark
import net.bible.android.database.bookmarks.VerseRangeUser
import org.crosswire.jsword.passage.VerseRange
import org.crosswire.jsword.versification.Versification
import java.util.*

/**
 * Comparator for ConvertibleVerseRanges.
 * Compares them in a consistent order according to which v11n is most used and compatible.
 * Ensures the same v11n is chosen when comparing v1, v2 and v2, v1 so that the order is consistent.
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class VerseRangeComparator private constructor(
    private val compatibleVersificationChooser: CompatibleVersificationChooser
) : Comparator<VerseRangeUser?> {
    override fun compare(a: VerseRangeUser?, b: VerseRangeUser?): Int {
        if (a == null) {
            return if (b == null) {
                0
            } else -1
        } else if (b == null) {
            return 1
        }
        val aCvr: VerseRange = a.verseRange
        val bCvr: VerseRange = b.verseRange

        // must compare in the same (but opposite) order when comparing b,a and a,b so cannot just use a.v11n()
        val v11n = compatibleVersificationChooser.findPreferredCompatibleVersification(aCvr, bCvr)
        return aCvr.toV11n(v11n).compareTo(bCvr.toV11n(v11n))
    }

    class Builder {
        private var prioritisedVersifications: List<Versification>? = null
        fun withBookmarks(bookmarks: List<Bookmark>): Builder {
            prioritisedVersifications = VersificationPrioritiser(bookmarks).prioritisedVersifications
            return this
        }

        fun build(): VerseRangeComparator {
            val compatibleVersificationChooser = CompatibleVersificationChooser(prioritisedVersifications!!)
            return VerseRangeComparator(compatibleVersificationChooser)
        }
    }
}

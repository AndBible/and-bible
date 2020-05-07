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
package net.bible.android.view.activity.page

import net.bible.android.control.page.ChapterVerse
import java.util.*

/** Automatically find current verse at top of display to aid quick movement to Commentary.
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class VerseCalculator {
    private var verseByOffset: SortedMap<Int, ChapterVerse> = TreeMap()

    /**
     * when a page is displayed js calls this function to recored the position of all verses to enable current verse calculation
     */
    fun registerVersePosition(chapterVerse: ChapterVerse, offset: Int) {
        if (!verseByOffset.containsKey(offset)) {
            verseByOffset[offset] = chapterVerse
        }
    }

    /** compare scrollOffset to the verseByOffset to find which verse is at the top of the screen
     *
     * @param scrollOffset    distance from the top of the screen.
     * @return verse number
     */
    fun calculateCurrentVerse(scrollOffset: Int): ChapterVerse {
        var startValue : ChapterVerse? = null

        for ((offset, value) in verseByOffset) {
            if(offset < scrollOffset) {
                startValue = value
            }
            if(offset > scrollOffset) {
                return startValue ?: value
            }
        }
        // maybe scrolled off bottom
        return verseByOffset.values.last()
    }

    fun clear() {
        verseByOffset.clear()
    }

    companion object {
        // going to a verse pushes the offset a couple of pixels past the verse position on large screens i.e. going to Judg 5:11 will show Judg 5:12
        private const val SLACK_FOR_JUMP_TO_VERSE = 5
    }
}

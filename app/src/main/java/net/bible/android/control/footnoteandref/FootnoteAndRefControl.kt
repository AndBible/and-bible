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
package net.bible.android.control.footnoteandref

import net.bible.android.BibleApplication.Companion.application
import net.bible.android.activity.R
import net.bible.android.control.ApplicationScope
import net.bible.android.control.page.CurrentPageManager
import net.bible.android.control.page.window.ActiveWindowPageManagerProvider
import net.bible.android.control.versification.BibleTraverser
import net.bible.android.view.activity.base.Dialogs.Companion.instance
import net.bible.service.format.Note
import org.apache.commons.lang3.StringUtils
import org.crosswire.jsword.passage.VerseRange
import org.crosswire.jsword.versification.BookName
import java.util.*
import javax.inject.Inject

/** Support the Compare Translations screen
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
@ApplicationScope
class FootnoteAndRefControl @Inject constructor(private val bibleTraverser: BibleTraverser, private val activeWindowPageManagerProvider: ActiveWindowPageManagerProvider) {
    fun getTitle(verseRange: VerseRange): String {
        val stringBuilder = StringBuilder()
        synchronized(BookName::class) {
            val wasFullBookname = BookName.isFullBookName()
            BookName.setFullBookName(false)
            stringBuilder.append(application.getString(R.string.notes))
                .append(": ")
                .append(verseRange.name)
            BookName.setFullBookName(wasFullBookname)
        }
        return stringBuilder.toString()
    }

    /** Shuffle verseRange forward but stay in same chapter because those are the only notes fetched
     */
    fun next(verseRange: VerseRange): VerseRange {
        return bibleTraverser.getNextVerseRange(currentPageManager.currentPassageDocument, verseRange, false)
    }

    /** Shuffle verseRange backward but stay in same chapter because those are the only notes fetched
     */
    fun previous(verseRange: VerseRange): VerseRange {
        return bibleTraverser.getPreviousVerseRange(currentPageManager.currentPassageDocument, verseRange, false)
    }

    val currentPageManager: CurrentPageManager
        get() = activeWindowPageManagerProvider.activeWindowPageManager

    /**
     * Jump to the verse in the ref
     * If the osisRef is available then use that because sometimes the noteText itself misses out the book of the bible
     */
    fun navigateTo(note: Note) {
        val ref: String
        ref = if (StringUtils.isNotEmpty(note.osisRef)) {
            note.osisRef
        } else {
            note.noteText
        }
        val currentPageControl = activeWindowPageManagerProvider.activeWindowPageManager
        currentPageControl.currentBible.setKey(ref)
        currentPageControl.showBible()
    }

    companion object {
        private const val TAG = "FootnoteAndRefControl"
    }

}

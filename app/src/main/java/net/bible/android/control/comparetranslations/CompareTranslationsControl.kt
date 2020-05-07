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
package net.bible.android.control.comparetranslations

import android.util.Log
import net.bible.android.BibleApplication.Companion.application
import net.bible.android.activity.R
import net.bible.android.control.ApplicationScope
import net.bible.android.control.page.CurrentPageManager
import net.bible.android.control.page.window.ActiveWindowPageManagerProvider
import net.bible.android.control.versification.BibleTraverser
import net.bible.android.control.versification.ConvertibleVerseRange
import net.bible.service.common.CommonUtils.getKeyDescription
import net.bible.service.font.FontControl
import net.bible.service.sword.SwordContentFacade
import net.bible.service.sword.SwordDocumentFacade
import org.apache.commons.lang3.StringUtils
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.basic.AbstractPassageBook
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.passage.VerseRange
import org.crosswire.jsword.versification.BookName
import java.io.File
import java.util.*
import javax.inject.Inject

/** Support the Compare Translations screen
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
@ApplicationScope
class CompareTranslationsControl @Inject constructor(
    private val bibleTraverser: BibleTraverser,
    private val swordDocumentFacade: SwordDocumentFacade,
    private val swordContentFacade: SwordContentFacade,
    private val activeWindowPageManagerProvider: ActiveWindowPageManagerProvider
) {
    fun getTitle(verseRange: VerseRange?): String {
        val stringBuilder = StringBuilder()
        synchronized(BookName::class) {
            val wasFullBookname = BookName.isFullBookName()
            BookName.setFullBookName(false)
            stringBuilder.append(application.getString(R.string.compare_translations))
                .append(": ")
                .append(getKeyDescription(verseRange!!))
            BookName.setFullBookName(wasFullBookname)
        }
        return stringBuilder.toString()
    }

    fun setVerse(verse: Verse?) {
        currentPageManager.currentBible.doSetKey(verse)
    }

    /** Calculate next verse
     */
    fun getNextVerseRange(verseRange: VerseRange): VerseRange {
        return bibleTraverser.getNextVerseRange(currentPageManager.currentPassageDocument, verseRange)
    }

    /** Calculate next verse
     */
    fun getPreviousVerseRange(verseRange: VerseRange): VerseRange {
        return bibleTraverser.getPreviousVerseRange(currentPageManager.currentPassageDocument, verseRange)
    }

    /** return the list of verses to be displayed
     */
    fun getAllTranslations(verseRange: VerseRange): List<TranslationDto> {
        val retval: MutableList<TranslationDto> = ArrayList()
        val books = swordDocumentFacade.bibles
        val fontControl: FontControl = FontControl.instance
        val convertibleVerseRange = ConvertibleVerseRange(verseRange)
        for (book in books) {
            try {
                val text = swordContentFacade.getPlainText(book, convertibleVerseRange.getVerseRange((book as AbstractPassageBook).versification))
                if (text.length > 0) {

                    // does this book require a custom font to display it
                    var fontFile: File? = null
                    val fontForBook = fontControl.getFontForBook(book)
                    if (StringUtils.isNotEmpty(fontForBook)) {
                        fontFile = fontControl.getFontFile(fontForBook!!)
                    }

                    // create DTO with all required info to display this Translation text
                    retval.add(TranslationDto(book, text, fontFile))
                }
            } catch (nske: Exception) {
                Log.d(TAG, "$verseRange not in $book")
            }
        }
        return retval
    }

    fun showTranslationForVerseRange(translationDto: TranslationDto, verseRange: VerseRange) {
        currentPageManager.setCurrentDocumentAndKey(translationDto.book, verseRange.start)
    }

    val currentPageManager: CurrentPageManager
        get() = activeWindowPageManagerProvider.activeWindowPageManager

    companion object {
        private const val TAG = "CompareTranslationsCtrl"
    }

}

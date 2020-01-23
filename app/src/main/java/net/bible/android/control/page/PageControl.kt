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
package net.bible.android.control.page

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.util.Log
import net.bible.android.BibleApplication.Companion.application
import net.bible.android.activity.R
import net.bible.android.control.ApplicationScope
import net.bible.android.control.document.DocumentControl
import net.bible.android.control.page.window.ActiveWindowPageManagerProvider
import net.bible.android.control.page.window.Window
import net.bible.android.control.versification.Scripture
import net.bible.android.view.activity.base.CurrentActivityHolder
import net.bible.android.view.activity.base.Dialogs
import net.bible.service.common.CommonUtils.sharedPreferences
import net.bible.service.font.FontControl
import net.bible.service.sword.SwordContentFacade
import net.bible.service.sword.SwordDocumentFacade
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.passage.VerseRange
import org.crosswire.jsword.versification.BibleBook
import java.util.*
import javax.inject.Inject

/**
 * SesionFacade for CurrentPage used by View classes
 * @author Martin Denham [mjdenham at gmail dot com]
 */
@ApplicationScope
open class PageControl @Inject constructor(
	private val swordDocumentFacade: SwordDocumentFacade,
	private val swordContentFacade: SwordContentFacade,
	private val documentControl: DocumentControl,
	private val activeWindowPageManagerProvider: ActiveWindowPageManagerProvider
) {
    /** Paste the current verse to the system clipboard
     */
    fun copyToClipboard(verseRange: VerseRange) {
        try {
            val book = currentPageManager.currentPage.currentDocument
            val clipboard = application.getSystemService(Activity.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("verseText", getCopyShareText(book, verseRange)))
        } catch (e: Exception) {
            Log.e(TAG, "Error pasting to clipboard", e)
            Dialogs.getInstance().showErrorMsg("Error copying to clipboard")
        }
    }

    /** send the current verse via social applications installed on user's device
     */
    fun shareVerse(verseRange: VerseRange) {
        try {
            val book = currentPageManager.currentPage.currentDocument
            val sendIntent = Intent(Intent.ACTION_SEND)
            sendIntent.type = "text/plain"
            sendIntent.putExtra(Intent.EXTRA_TEXT, getCopyShareText(book, verseRange))
            // subject is used when user chooses to send verse via e-mail
            sendIntent.putExtra(Intent.EXTRA_SUBJECT, application.getText(R.string.share_verse_subject))
            val activity = CurrentActivityHolder.getInstance().currentActivity
            activity.startActivity(Intent.createChooser(sendIntent, activity.getString(R.string.share_verse)))
        } catch (e: Exception) {
            Log.e(TAG, "Error sharing verse", e)
            Dialogs.getInstance().showErrorMsg("Error sharing verse")
        }
    }

    private fun getCopyShareText(book: Book?, verseRange: VerseRange): String? {
 		if(book == null) return null
        return try {
            val referenceName = verseRange.getNameInLocale(null, Locale(book.language.code))
            referenceName + "\n" + "\n" + swordContentFacade.getTextWithVerseNumbers(book, verseRange)
        } catch (e: Exception) {
            Log.e(TAG, "Error converting verse from OSIS to text.", e)
            null
        }
    }

    /** This is only called after the very first bible download to attempt to ensure the first page is not 'Verse not found'
     * go through a list of default verses until one is found in the first/only book installed
     */
    fun setFirstUseDefaultVerse() {
        try {
            val versification = currentPageManager.currentBible.versification
            val defaultVerses = arrayOf(
                Verse(versification, BibleBook.JOHN, 3, 16),
                Verse(versification, BibleBook.GEN, 1, 1),
                Verse(versification, BibleBook.PS, 1, 1))
            val bibles = swordDocumentFacade.bibles
            if (bibles.size == 1) {
                val bible = bibles[0]
                for (verse in defaultVerses) {
                    if (bible.contains(verse)) {
                        currentPageManager.currentBible.doSetKey(verse)
                        return
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Verse error")
        }
    }

    open val currentBibleVerse: Verse
        get() = currentPageManager.currentBible.singleKey

    /** font size may be adjusted for certain fonts e.g. SBLGNT
     */
    fun getDocumentFontSize(window: Window): Int { // get base font size
        val fontSize = window.pageManager.actualTextDisplaySettings.fontSize!!
        // if book has a special font it may require an adjusted font size
        val book = window.pageManager.currentPage.currentDocument
        val font = FontControl.getInstance().getFontForBook(book)
        val fontSizeAdjustment = FontControl.getInstance().getFontSizeAdjustment(font, book)
        return fontSize + fontSizeAdjustment
    }

    /**
     * Return false if current page is not scripture, but only if the page is valid
     */
    val isCurrentPageScripture: Boolean
        get() {
            val currentVersePage = currentPageManager.currentVersePage
            val currentVersification = currentVersePage.versification
            val currentBibleBook = currentVersePage.currentBibleVerse.currentBibleBook
            val isCurrentBibleBookScripture = Scripture.isScripture(currentBibleBook)
            // Non-scriptural pages are not so safe.  They may be synched with the other screen but not support the current dc book
            return isCurrentBibleBookScripture ||
                !currentVersification.containsBook(currentBibleBook)
        }

    val currentPageManager: CurrentPageManager
        get() = activeWindowPageManagerProvider.activeWindowPageManager

    companion object {
        private const val TAG = "PageControl"
    }

}

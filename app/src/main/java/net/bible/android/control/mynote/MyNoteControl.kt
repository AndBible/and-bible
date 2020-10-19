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
/**
 *
 */
package net.bible.android.control.mynote

import android.text.Html
import android.util.Log
import android.widget.Toast
import net.bible.android.BibleApplication.Companion.application
import net.bible.android.activity.R
import net.bible.android.control.ApplicationScope
import net.bible.android.control.page.CurrentPageManager
import net.bible.android.control.page.window.ActiveWindowPageManagerProvider
import net.bible.android.view.activity.page.MainBibleActivity
import net.bible.service.common.CommonUtils.getResourceString
import net.bible.service.common.CommonUtils.getSharedPreference
import net.bible.service.common.CommonUtils.limitTextLength
import net.bible.service.common.CommonUtils.saveSharedPreference
import net.bible.service.db.mynote.MyNoteDto
import org.crosswire.jsword.passage.KeyUtil
import org.crosswire.jsword.passage.VerseRange
import javax.inject.Inject

/**
 * User Note controller methods
 *
 * @author John D. Lewis [balinjdl at gmail dot com]
 * @author Martin Denham [mjdenham at gmail dot com]
 */
@ApplicationScope
class MyNoteControl @Inject constructor(val activeWindowPageManagerProvider: ActiveWindowPageManagerProvider, private val myNoteDAO: MyNoteDAO) {
    /**
     * Start chain of actions to switch to MyNote view
     * @param verseRange
     */
    fun showMyNote(verseRange: VerseRange) { // if existing MyNote exists with same start verse then adjust range to match the note that will be edited
        var verseRange = verseRange
        val existingMyNoteWithSameStartVerse = myNoteDAO.getMyNoteByStartVerse(verseRange)
        if (existingMyNoteWithSameStartVerse != null) {
            verseRange = existingMyNoteWithSameStartVerse.getVerseRange(verseRange.versification)?: return
        }
        currentPageManager.showMyNote(verseRange)
    }

    fun showNoteView(noteDto: MyNoteDto) {
        currentPageManager.showMyNote(noteDto.verseRange)
        MainBibleActivity.mainBibleActivity.updateActions()
    }

    fun getMyNoteVerseKey(myNote: MyNoteDto): String {
        var keyText = ""
        try {
            val versification = currentPageManager.currentBible.versification
            keyText = myNote.getVerseRange(versification)!!.name
        } catch (e: Exception) {
            Log.e(TAG, "Error getting verse text", e)
        }
        return keyText
    }

    fun saveMyNoteText(myNote: String?): Boolean {
        val dto = currentMyNoteDto
        dto.noteText = myNote?: ""
        return saveMyNote(dto)
    }

    //
    private val currentMyNoteDto: MyNoteDto
        get() { //
            val key = currentPageManager.currentMyNotePage.key
            val verseRange: VerseRange
            // The key should be a VerseRange
            verseRange = if (key is VerseRange) {
                key
            } else {
                val verse = KeyUtil.getVerse(key)
                VerseRange(verse.versification, verse)
            }
            // get a dto
            var myNote = myNoteDAO.getMyNoteByStartVerse(verseRange)
            // return an empty note dto
            if (myNote == null) {
                myNote = MyNoteDto()
                myNote.verseRange = verseRange
            }
            return myNote
        }

    /** save the note to the database if it is new or has been updated
     */
    private fun saveMyNote(myNoteDto: MyNoteDto): Boolean {
        Log.d(TAG, "saveMyNote started...")
        var isSaved = false
        if (myNoteDto.isNew) {
            if (!myNoteDto.isEmpty) {
                myNoteDAO.addMyNote(myNoteDto)
                isSaved = true
            }
        } else {
            val oldNote = myNoteDAO.getMyNoteByStartVerse(myNoteDto.verseRange)
            // delete empty notes
            if (myNoteDto.isEmpty) {
                myNoteDAO.deleteMyNote(myNoteDto)
            } else if (myNoteDto != oldNote) { // update changed notes
                myNoteDAO.updateMyNote(myNoteDto)
                isSaved = true
            }
        }
        if (isSaved) {
            Toast.makeText(application.applicationContext, R.string.mynote_saved, Toast.LENGTH_SHORT).show()
        }
        return isSaved
    }

    fun getMyNoteText(myNote: MyNoteDto, abbreviated: Boolean): String? {
        var text: String? = ""
        try {
            text = myNote.noteText
            if (abbreviated) { //TODO allow longer lines if portrait or tablet
                val singleLine = true
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    text = Html.fromHtml(text, Html.FROM_HTML_MODE_LEGACY).toString();
                } else {
                    text = Html.fromHtml(text).toString();
                }
                text = limitTextLength(text, 40, singleLine)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting user note text", e)
        }
        return text
    }
    // pure myNote methods
    /** get all myNotes  */
    val allMyNotes: List<MyNoteDto>
        get() = myNoteDAO.getAllMyNotes(sortOrder)

    /** delete this user note (and any links to labels)  */
    fun deleteMyNote(myNote: MyNoteDto?): Boolean {
        return myNoteDAO.deleteMyNote(myNote)
    }

    fun changeSortOrder() {
        sortOrder = if (sortOrder == MyNoteSortOrder.BIBLE_BOOK) {
            MyNoteSortOrder.DATE_CREATED
        } else {
            MyNoteSortOrder.BIBLE_BOOK
        }
    }

    var sortOrder: MyNoteSortOrder
        get() {
            val sortOrderStr = getSharedPreference(MYNOTE_SORT_ORDER, MyNoteSortOrder.BIBLE_BOOK.toString())
            return MyNoteSortOrder.valueOf(sortOrderStr!!)
        }
        private set(sortOrder) {
            saveSharedPreference(MYNOTE_SORT_ORDER, sortOrder.toString())
        }

    val sortOrderDescription: String
        get() = if (MyNoteSortOrder.BIBLE_BOOK == sortOrder) {
            getResourceString(R.string.sort_by_bible_book)
        } else {
            getResourceString(R.string.sort_by_date)
        }

    val currentPageManager: CurrentPageManager
        get() = activeWindowPageManagerProvider.activeWindowPageManager

    companion object {
        private const val MYNOTE_SORT_ORDER = "MyNoteSortOrder"
        private const val TAG = "MyNoteControl"
    }

}

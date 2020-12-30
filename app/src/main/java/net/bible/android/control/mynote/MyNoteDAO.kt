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
package net.bible.android.control.mynote

import net.bible.android.activity.R
import net.bible.android.control.ApplicationScope
import net.bible.android.view.activity.base.Dialogs
import net.bible.service.db.mynote.MyNoteDBAdapter
import net.bible.service.db.mynote.MyNoteDto
import org.crosswire.jsword.passage.Key
import org.crosswire.jsword.passage.KeyUtil
import java.util.*
import javax.inject.Inject

/**
 * MYNote Data access object
 */
@ApplicationScope
open class MyNoteDAO @Inject constructor() {
    fun getMyNoteTextByKey(verseRange: Key): String { // get a dto
        val myNote = getMyNoteByStartVerse(verseRange)
        // return an empty note dto
        var noteText: String = ""
        if (myNote != null) {
            noteText = myNote.noteText
        }
        return noteText
    }

    /**
     * get all myNotes
     * @param sortOrder
     */
    fun getAllMyNotes(sortOrder: MyNoteSortOrder): List<MyNoteDto> {
        val db = MyNoteDBAdapter()
        var myNoteList: List<MyNoteDto>
        try {
            myNoteList = db.allMyNotes
            myNoteList = getSortedMyNotes(myNoteList, sortOrder)
        } finally {}
        return myNoteList
    }

    /**
     * get user note with this key if it exists or return null
     */
    fun getMyNoteByStartVerse(key: Key?): MyNoteDto? {
        val startVerse = KeyUtil.getVerse(key)
        val db = MyNoteDBAdapter()
        var myNote: MyNoteDto? = null
        myNote = try {
            db.getMyNoteByStartVerse(startVerse.osisRef)
        } finally {}
        return myNote
    }

    /**
     * delete this user note (and any links to labels)
     */
    fun deleteMyNote(myNote: MyNoteDto?): Boolean {
        var bOk = false
        if (myNote != null && myNote.id != null) {
            val db = MyNoteDBAdapter()
            bOk = try {
                db.removeMyNote(myNote)
            } finally {}
        }
        return bOk
    }

    /**
     * create a new myNote
     */
    fun addMyNote(myNote: MyNoteDto?): MyNoteDto? {
        val db = MyNoteDBAdapter()
        var newMyNote: MyNoteDto? = null
        newMyNote = try {
            db.insertMyNote(myNote!!)
        } finally {}
        return newMyNote
    }

    /**
     *update a myNote
     */
    fun updateMyNote(myNote: MyNoteDto?): MyNoteDto? {
        val db = MyNoteDBAdapter()
        var updatedMyNote: MyNoteDto? = null
        updatedMyNote = try {
            db.updateMyNote(myNote!!)
        } finally {}
        return updatedMyNote
    }

    private fun getSortedMyNotes(myNoteList: List<MyNoteDto>, sortOrder: MyNoteSortOrder): List<MyNoteDto> {
		val comparator = when (sortOrder) {
			MyNoteSortOrder.DATE_CREATED -> MyNoteCreationDateComparator()
			MyNoteSortOrder.BIBLE_BOOK -> MyNoteDtoBibleOrderComparator(myNoteList)
		}
        // the new Java 7 sort is stricter and occasionally generates errors, so prevent total crash on listing notes
        try {
            Collections.sort(myNoteList, comparator)
        } catch (e: Exception) {
            Dialogs.instance.showErrorMsg(R.string.error_occurred, e)
        }
        return myNoteList
    }
}

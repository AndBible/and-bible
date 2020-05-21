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
package net.bible.android.view.activity.footnoteandref

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.ListView
import kotlinx.android.synthetic.main.notes.*
import net.bible.android.activity.R
import net.bible.android.control.footnoteandref.FootnoteAndRefControl
import net.bible.android.control.footnoteandref.NoteDetailCreator
import net.bible.android.control.page.window.WindowControl
import net.bible.android.view.activity.base.IntentHelper
import net.bible.android.view.activity.base.ListActivityBase
import net.bible.android.view.util.swipe.SwipeGestureEventHandler
import net.bible.android.view.util.swipe.SwipeGestureListener
import net.bible.service.format.Note
import net.bible.service.sword.SwordContentFacade
import org.apache.commons.lang3.StringUtils
import org.crosswire.jsword.passage.VerseRange
import java.util.*
import javax.inject.Inject

/** Show Notes and Cross references for the current verse
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class FootnoteAndRefActivity : ListActivityBase(), SwipeGestureEventHandler {
    private lateinit var currentVerseRange: VerseRange
    private lateinit var notesList: List<Note>
    private lateinit var verseNotesList: MutableList<Note>
    private lateinit var notesListAdapter: ItemAdapter
    private val intentHelper = IntentHelper()

    // detect swipe left/right
    private lateinit var gestureDetector: GestureDetector
    @Inject
    lateinit var footnoteAndRefControl: FootnoteAndRefControl
    @Inject
    lateinit var noteDetailCreator: NoteDetailCreator
    @Inject
    lateinit var swordContentFacade: SwordContentFacade
    @Inject
    lateinit var windowControl: WindowControl

    /** Called when the activity is first created.  */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "Displaying notes")
        setContentView(R.layout.notes)
        buildActivityComponent().inject(this)

        currentVerseRange = intentHelper.getIntentVerseRangeOrDefault(intent)

        initialiseView()

        // create gesture related objects
        gestureDetector = GestureDetector(SwipeGestureListener(this))
    }

    private fun initialiseView() {
        verseNotesList = ArrayList()
        showCurrentVerse()
        populateVerseNotesList()
        prepareWarningMsg()
        notesListAdapter = ItemAdapter(this, LIST_ITEM_TYPE, verseNotesList, noteDetailCreator)
        listAdapter = notesListAdapter
    }

    override fun onListItemClick(l: ListView, v: View, position: Int, id: Long) {
        noteSelected(verseNotesList[position])
    }

    /** swiped left
     */
    override fun onNext() {
        Log.d(TAG, "Next")
        currentVerseRange = footnoteAndRefControl.next(currentVerseRange)
        onVerseChanged()
    }

    /** swiped right
     */
    override fun onPrevious() {
        Log.d(TAG, "Previous")
        currentVerseRange = footnoteAndRefControl.previous(currentVerseRange)
        onVerseChanged()
    }

    private fun onVerseChanged() {
        showCurrentVerse()
        populateVerseNotesList()
        notifyDataSetChanged()
        prepareWarningMsg()
    }

    private fun populateVerseNotesList() {
        val pageManager = windowControl.activeWindowPageManager
        notesList = swordContentFacade.readFootnotesAndReferences(
            pageManager.currentPassageDocument,
            currentVerseRange,
            pageManager.actualTextDisplaySettings)

        verseNotesList.clear()
        val startVerseNo = currentVerseRange.start.verse
        val endVerseNo = currentVerseRange.end.verse
        for (note in notesList) {
            val noteVerseNo = note.verseNo
            if (noteVerseNo in startVerseNo..endVerseNo) {
                verseNotesList.add(note)
            }
        }
    }

    private fun prepareWarningMsg() {
        var warning = ""
        if (notesList.isEmpty()) {
            warning = getString(R.string.no_chapter_notes)
        } else if (verseNotesList.size == 0) {
            warning = getString(R.string.no_verse_notes)
        }
        warningText.text = warning
        if (StringUtils.isNotEmpty(warning)) {
            warningText.visibility = View.VISIBLE
            listView.visibility = View.GONE
        } else {
            warningText.visibility = View.GONE
            listView.visibility = View.VISIBLE
        }
    }

    private fun showCurrentVerse() {
        title = footnoteAndRefControl.getTitle(currentVerseRange)
    }

    private fun noteSelected(note: Note?) {
        Log.i(TAG, "chose:$note")
        if (note!!.isNavigable) {
            footnoteAndRefControl.navigateTo(note)
        }
        doFinish()
    }

    fun onFinish(v: View?) {
        Log.i(TAG, "CLICKED")
        doFinish()
    }

    private fun doFinish() {
        val resultIntent = Intent()
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }

    // handle swipe left and right
    override fun dispatchTouchEvent(motionEvent: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(motionEvent)
        return super.dispatchTouchEvent(motionEvent)
    }

    companion object {
        private const val TAG = "NotesActivity"
        protected const val LIST_ITEM_LINE1 = "line1"
        protected const val LIST_ITEM_LINE2 = "line2"
        private const val LIST_ITEM_TYPE = android.R.layout.simple_list_item_2
    }
}

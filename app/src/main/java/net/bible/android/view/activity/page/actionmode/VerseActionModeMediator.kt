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

package net.bible.android.view.activity.page.actionmode

import android.content.Intent
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.view.ActionMode

import net.bible.android.activity.R
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.event.passage.PassageChangedEvent
import net.bible.android.control.event.window.CurrentWindowChangedEvent
import net.bible.android.control.page.ChapterVerse
import net.bible.android.control.page.PageControl
import net.bible.android.view.activity.page.ChapterVerseRange

import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.passage.VerseRange
import java.lang.ref.WeakReference

/**
 * Control the verse selection action mode
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class VerseActionModeMediator(
        private val mainBibleActivity: ActionModeMenuDisplay,
        private val bibleView: VerseHighlightControl,
        private val pageControl: PageControl,
        private val verseMenuCommandHandler: VerseMenuCommandHandler,
        private val bookmarkControl: BookmarkControl)
{

    private var chapterVerseRange: ChapterVerseRange? = null

    private var actionMode: ActionMode? = null

    val isActionMode: Boolean
        get() = actionMode != null

    private val startVerse: Verse?
        get() {
            val chapVer = chapterVerseRange
            return if (chapVer?.start == null) {
                null
            } else {
                val mainVerse = pageControl.currentBibleVerse
                val start = chapVer.start
                Verse(mainVerse.versification, mainVerse.book, start.chapter, start.verse)
            }
        }

    private val verseRange: VerseRange?
        get() {
            val startVerse = startVerse
            val chapVer = chapterVerseRange
            return if (startVerse == null || chapVer?.end == null) {
                null
            } else {
                val v11n = startVerse.versification
                val end = chapVer.end
                val endVerse = Verse(v11n, startVerse.book, end.chapter, end.verse)
                VerseRange(v11n, startVerse, endVerse)
            }
        }

    private val actionModeCallbackHandler = object : ActionMode.Callback {
        override fun onCreateActionMode(actionMode: ActionMode, menu: Menu): Boolean {
            this@VerseActionModeMediator.actionMode = actionMode

            // Inflate our menu from a resource file
            actionMode.menuInflater.inflate(R.menu.verse_action_mode_menu, menu)

            // Return true so that the action mode is shown
            return true
        }

        override fun onPrepareActionMode(actionMode: ActionMode, menu: Menu): Boolean {
            // if start verse already bookmarked then enable Delete and Labels Bookmark menu item
            val startVerse = startVerse
            val isVerseBookmarked = startVerse != null && bookmarkControl.isBookmarkForKey(startVerse)
            menu.findItem(R.id.add_bookmark).isVisible = true
            menu.findItem(R.id.delete_bookmark).isVisible = isVerseBookmarked
            menu.findItem(R.id.edit_bookmark_labels).isVisible = isVerseBookmarked

            // must return true if menu changed
            return true
        }

        override fun onActionItemClicked(actionMode: ActionMode, menuItem: MenuItem): Boolean {
            Log.i(TAG, "Action menu item clicked: $menuItem")

            // Similar to menu handling in Activity.onOptionsItemSelected()
            val verseRange = verseRange
            if(verseRange != null) {
                verseMenuCommandHandler.handleMenuRequest(menuItem.itemId, verseRange)
            }

            endVerseActionMode()

            // handle all
            return true
        }

        override fun onDestroyActionMode(actionMode: ActionMode) {
            Log.i(TAG, "On destroy action mode")
            endVerseActionMode()
        }
    }

    init {

        // Be notified if the associated window loses focus
        ABEventBus.getDefault().register(this)
    }

    fun verseLongPress(verse: ChapterVerse) {
        Log.d(TAG, "Verse selected event:$verse")
        startVerseActionMode(verse)
    }

    /**
     * Handle selection and deselection of extra verses after initial verse
     */
    fun verseTouch(verse: ChapterVerse) {
        Log.d(TAG, "Verse touched event:$verse")

        val origRange = chapterVerseRange
        chapterVerseRange = chapterVerseRange!!.toggleVerse(verse)

        if (chapterVerseRange!!.isEmpty()) {
            endVerseActionMode()
        } else {
            val toSelect = origRange!!.getExtrasIn(chapterVerseRange!!)
            val toDeselect = chapterVerseRange!!.getExtrasIn(origRange)

            for (verseNo in toSelect) {
                bibleView.highlightVerse(verseNo)
            }
            for (verseNo in toDeselect) {
                bibleView.unhighlightVerse(verseNo)
            }
        }
    }

    fun onEvent(event: CurrentWindowChangedEvent) {
        endVerseActionMode()
    }

    fun onEvent(event: PassageChangedEvent) {
        endVerseActionMode()
    }

    private fun startVerseActionMode(startChapterVerse: ChapterVerse) {
        Log.d(TAG, "startVerseActionMode")
        if(!mainBibleActivity.isVerseActionModeAllowed()) {
            return
        }
        if (actionMode != null) {
            Log.i(TAG, "Action mode already started so ignoring restart.")
            return
        }

        Log.i(TAG, "Start verse action mode. verse no:$startChapterVerse")
        bibleView.highlightVerse(startChapterVerse, true)

        val currentVerse = pageControl.currentBibleVerse
        chapterVerseRange = ChapterVerseRange(currentVerse.versification, currentVerse.book,
                startChapterVerse, startChapterVerse)

        mainBibleActivity.showVerseActionModeMenu(actionModeCallbackHandler)
        bibleView.enableVerseTouchSelection()
    }

    /**
     * Ensure all state is left tidy
     */

    private fun endVerseActionMode() {
        // prevent endless loop by onDestroyActionMode calling this calling onDestroyActionMode etc.
        val actionMode = this.actionMode
        if (actionMode != null) {
            mainBibleActivity.clearVerseActionMode(actionMode)
            this.actionMode = null

            bibleView.clearVerseHighlight()
            bibleView.disableVerseTouchSelection()
            chapterVerseRange = null

        }
    }

    interface ActionModeMenuDisplay {
        fun showVerseActionModeMenu(actionModeCallbackHandler: ActionMode.Callback)
        fun clearVerseActionMode(actionMode: ActionMode)
        fun isVerseActionModeAllowed(): Boolean
        fun startActivityForResult(intent: Intent, requestCode: Int)
    }

    interface VerseHighlightControl {
        fun enableVerseTouchSelection()
        fun disableVerseTouchSelection()
        fun highlightVerse(chapterVerse: ChapterVerse, start: Boolean=false)
        fun unhighlightVerse(chapterVerse: ChapterVerse)
        fun clearVerseHighlight()
    }

    companion object {

        private const val TAG = "VerseActionModeMediator"
    }
}

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
import net.bible.android.activity.R
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.control.mynote.MyNoteControl
import net.bible.android.control.page.PageControl
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.base.IntentHelper
import net.bible.android.view.activity.comparetranslations.CompareTranslations
import net.bible.android.view.activity.footnoteandref.FootnoteAndRefActivity
import net.bible.android.view.activity.page.MainBibleActivity
import org.crosswire.jsword.passage.VerseRange

/** Handle requests from the selected verse action menu
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
open class VerseMenuCommandHandler(
    private val mainActivity: MainBibleActivity,
    private val pageControl: PageControl,
    private val bookmarkControl: BookmarkControl,
    private val myNoteControl: MyNoteControl
) {
    private val intentHelper = IntentHelper()

    /**
     * on Click handler for Selected verse menu
     */
    fun handleMenuRequest(menuItemId: Int, verseRange: VerseRange): Boolean {
        var isHandled = false
        var handlerIntent: Intent? = null
        val requestCode = ActivityBase.STD_REQUEST_CODE
        when (menuItemId) {
            R.id.compareTranslations -> {
                handlerIntent = Intent(mainActivity, CompareTranslations::class.java)
                isHandled = true
            }
            R.id.notes -> {
                handlerIntent = Intent(mainActivity, FootnoteAndRefActivity::class.java)
                isHandled = true
            }
            R.id.add_bookmark -> {
                bookmarkControl.addBookmarkForVerseRange(verseRange)
                // refresh view to show new bookmark icon
                isHandled = true
            }
            R.id.delete_bookmark -> {
                bookmarkControl.deleteBookmarkForVerseRange(verseRange)
                // refresh view to show new bookmark icon
                isHandled = true
            }
            R.id.edit_bookmark_labels -> {
                bookmarkControl.editBookmarkLabelsForVerseRange(verseRange)
                isHandled = true
            }
            R.id.myNoteAddEdit -> {
                myNoteControl.showMyNote(verseRange)
                mainActivity.invalidateOptionsMenu()
                mainActivity.documentViewManager.buildView()
                isHandled = true
            }
            R.id.copy -> {
                pageControl.copyToClipboard(verseRange)
                isHandled = true
            }
            R.id.shareVerse -> {
                pageControl.shareVerse(verseRange)
                isHandled = true
            }
        }
        if (handlerIntent != null) {
            intentHelper.updateIntentWithVerseRange(handlerIntent, verseRange)
            mainActivity.startActivityForResult(handlerIntent, requestCode)
        }
        return isHandled
    }

    companion object {
        private const val TAG = "VerseMenuCommandHandler"
    }

}

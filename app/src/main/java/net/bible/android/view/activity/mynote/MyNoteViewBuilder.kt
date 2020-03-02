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
package net.bible.android.view.activity.mynote

import android.view.ViewGroup
import net.bible.android.control.mynote.MyNoteControl
import net.bible.android.control.page.window.ActiveWindowPageManagerProvider
import net.bible.android.view.activity.MainBibleActivityScope
import net.bible.android.view.activity.base.DocumentView
import net.bible.android.view.activity.page.MainBibleActivity
import javax.inject.Inject

/**
 * Build a MyNote TextView for viewing or editing notes
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
@MainBibleActivityScope
class MyNoteViewBuilder @Inject constructor(private val mainActivity: MainBibleActivity, myNoteControl: MyNoteControl?, activeWindowPageManagerProvider: ActiveWindowPageManagerProvider) {
    private val myNoteText: MyNoteEditTextView
    private val activeWindowPageManagerProvider: ActiveWindowPageManagerProvider
    /** return true if the current page should show a NyNote
     */
    val isMyNoteViewType: Boolean
        get() = activeWindowPageManagerProvider.activeWindowPageManager.isMyNoteShown

    fun addMyNoteView(parent: ViewGroup): MyNoteEditTextView {
        parent.addView(myNoteText)
        mainActivity.registerForContextMenu(myNoteText)
        return myNoteText
    }

    fun afterRemove() {
        mainActivity.unregisterForContextMenu(myNoteText)
    }

    val view: DocumentView
        get() = myNoteText

    companion object {
        private const val MYNOTE_TEXT_ID = 992
        private const val TAG = "MyNoteViewBuilder"
    }

    init {
        myNoteText = MyNoteEditTextView(mainActivity, myNoteControl!!)
        myNoteText.id = MYNOTE_TEXT_ID
        this.activeWindowPageManagerProvider = activeWindowPageManagerProvider
    }
}

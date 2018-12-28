/*
 * Copyright (c) 2018 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
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

package net.bible.android.view.activity.page.actionbar

import android.app.Activity
import android.content.Intent
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.ActionBar
import androidx.appcompat.widget.Toolbar
import net.bible.android.activity.R

import net.bible.android.control.event.ABEventBus
import net.bible.android.control.event.passage.CurrentVerseChangedEvent
import net.bible.android.control.page.PageControl
import net.bible.android.view.activity.MainBibleActivityScope
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.base.CurrentActivityHolder
import net.bible.android.view.activity.page.MainBibleActivity

import javax.inject.Inject

/**
 * Show current verse/key and document on left of actionBar
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
@MainBibleActivityScope
class MainBibleTitle @Inject constructor(
        private val pageControl: PageControl,
        private val mainBibleActivity: MainBibleActivity

) {

    private lateinit var actionBar: ActionBar
    protected lateinit var activity: Activity
        private set

    private lateinit var homeButton: ImageButton
    private lateinit var documentTitle: TextView
    private lateinit var pageTitle: TextView

    fun addToBar(actionBar: ActionBar, activity: Activity) {
        this.actionBar = actionBar
        this.activity = activity

        actionBar.setCustomView(R.layout.maine_bible_title)
        homeButton = actionBar.customView.findViewById(R.id.homeButton)
        documentTitle = actionBar.customView.findViewById(R.id.documentTitle)
        pageTitle = actionBar.customView.findViewById(R.id.pageTitle)
        homeButton.setOnClickListener { onHomeButtonClick() }

        // clicking page title shows appropriate key selector
        val pageTitleContainer = actionBar.customView.findViewById<ViewGroup>(R.id.pageTitleContainer)
        pageTitleContainer.setOnClickListener { onPageTitleClick() }

        actionBar.displayOptions = ActionBar.DISPLAY_SHOW_CUSTOM or ActionBar.DISPLAY_SHOW_HOME

        update(true)

        // do not display the app icon in the actionbar

        // remove a small amount of extra padding at the left of the actionbar see: http://stackoverflow.com/questions/27354812/android-remove-left-margin-from-actionbars-custom-layout
        val toolbar = actionBar.customView.parent
        if (toolbar is Toolbar) {
            toolbar.setContentInsetsAbsolute(0, 0)
        }
        // listen for verse change events
        ABEventBus.getDefault().safelyRegister(this)
    }


    fun update() {
        // update everything if called externally
        update(true)
    }

    protected fun update(everything: Boolean) {
        CurrentActivityHolder.getInstance().runOnUiThread {
                // always update verse number
            pageTitle.text = pageTitleText

            // don't always need to redisplay document name
            if (everything) {
                documentTitle.text = documentTitleText
            }
        }
    }

    private fun onHomeButtonClick() {
        mainBibleActivity.onHomeButtonClick();
    }

    private val documentTitleText: String
        get() = pageControl.currentPageManager.currentPassageDocument.name

    private val pageTitleText: String
        get() = pageControl.currentBibleVerse.name


    /**
     * Receive verse change events
     */
    fun onEvent(passageEvent: CurrentVerseChangedEvent) {
        update(false)
    }

    private fun onPageTitleClick() {
        val intent = Intent(activity, pageControl.currentPageManager.currentPage.keyChooserActivity)
        activity.startActivityForResult(intent, ActivityBase.STD_REQUEST_CODE)
    }
}

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

package net.bible.android.view.activity.readingplan.actionbar

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import androidx.appcompat.app.ActionBar
import androidx.appcompat.widget.Toolbar
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView

import net.bible.android.activity.R
import net.bible.android.control.ApplicationScope
import net.bible.android.control.readingplan.ReadingPlanControl
import net.bible.android.view.activity.base.CurrentActivityHolder
import net.bible.android.view.activity.readingplan.DailyReadingList
import net.bible.android.view.activity.readingplan.ReadingPlanSelectorList
import net.bible.service.common.CommonUtils
import net.bible.service.common.TitleSplitter

import org.apache.commons.lang3.ArrayUtils
import org.apache.commons.lang3.StringUtils
import javax.inject.Inject

/**
 * Show current verse/key and document on left of actionBar
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */



@ApplicationScope
class ReadingPlanTitle @Inject
constructor(private val readingPlanControl: ReadingPlanControl) {


    private lateinit var actionBar: ActionBar
    private lateinit var activity: Activity
        private set

    private lateinit var documentTitle: TextView
    private lateinit var documentSubtitle: TextView
    private lateinit var pageTitle: TextView
    private lateinit var pageSubtitle: TextView

    private val twoPageTitleParts: Array<String>
        get() {
            return try {
                unsplitIfLandscape(pageTitleParts)
            } catch (e: Exception) {
                Log.e(TAG, "Error getting reading plan title", e)
                arrayOf("", "")
            }

        }

    private val twoDocumentTitleParts: Array<String>
        get() {
            return try {
                unsplitIfLandscape(documentTitleParts)
            } catch (e: Exception) {
                Log.e(TAG, "Error getting reading plan title", e)
                arrayOf("", "")
            }

        }

    fun addToBar(actionBar: ActionBar, activity: Activity) {
        this.actionBar = actionBar
        this.activity = activity

        actionBar.setCustomView(R.layout.reading_plan_title)
        val homeButton: ImageButton = actionBar.customView.findViewById(R.id.homeButton)
        homeButton.visibility = View.GONE
        
        documentTitle = actionBar.customView.findViewById(R.id.documentTitle)
        documentSubtitle = actionBar.customView.findViewById(R.id.documentSubtitle)
        pageTitle = actionBar.customView.findViewById(R.id.pageTitle)
        pageSubtitle = actionBar.customView.findViewById(R.id.pageSubtitle)


        // clicking document title shows document selector
        val documentTitleContainer = actionBar.customView.findViewById<ViewGroup>(R.id.documentTitleContainer)
        documentTitleContainer.setOnClickListener { onDocumentTitleClick() }

        // clicking page title shows appropriate key selector
        val pageTitleContainer = actionBar.customView.findViewById<ViewGroup>(R.id.pageTitleContainer)
        pageTitleContainer.setOnClickListener { onPageTitleClick() }

        actionBar.displayOptions = ActionBar.DISPLAY_SHOW_CUSTOM or ActionBar.DISPLAY_SHOW_HOME

        update(true)

        // remove a small amount of extra padding at the left of the actionbar see: http://stackoverflow.com/questions/27354812/android-remove-left-margin-from-actionbars-custom-layout
        val toolbar = actionBar.customView.parent
        if (toolbar is Toolbar) {
            toolbar.setContentInsetsAbsolute(0, 0)
        }
    }


    fun update() {
        // update everything if called externally
        update(true)
    }

    private fun update(everything: Boolean) {
        CurrentActivityHolder.getInstance().runOnUiThread {
            // always update verse number
            val pageParts = twoPageTitleParts
            if (pageParts.isNotEmpty()) pageTitle.text = pageParts[0]
            if (pageParts.size > 1) pageSubtitle.text = pageParts[1]
            pageSubtitle.visibility = if (pageParts.size > 1) View.VISIBLE else View.GONE

            // don't always need to redisplay document name
            if (everything) {
                val documentParts = twoDocumentTitleParts
                if (documentParts.isNotEmpty()) documentTitle.text = documentParts[0]
                if (documentParts.size > 1) documentSubtitle.text = documentParts[1]
                documentSubtitle.visibility = if (documentParts.size > 1) View.VISIBLE else View.GONE
            }
        }
    }

    private fun getTwoTitleParts(title: String, lastAreMoreSignificant: Boolean): Array<String> {
        var parts: Array<String>? = titleSplitter.split(title)
        parts = reduceTo2Parts(parts, lastAreMoreSignificant)
        return parts
    }

    private fun reduceTo2Parts(parts: Array<String>?, lastAreMoreSignificant: Boolean): Array<String> {
        // return the last 2 parts as only show 2 and last are normally most significant
        return if (lastAreMoreSignificant) {
            ArrayUtils.subarray(parts, parts!!.size - 2, parts.size)
        } else {
            ArrayUtils.subarray(parts, 0, 2)
        }
    }

    private fun unsplitIfLandscape(parts: Array<String>): Array<String> {
        var parts = parts
        // un-split if in landscape because landscape actionBar has more width but less height
        if (activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            parts = arrayOf(StringUtils.join(parts, " "))
        }
        return parts
    }

    private val documentTitleParts: Array<String>
        get() {
            val title = readingPlanControl.shortTitle
            return getTwoTitleParts(title, false)
        }

    private val pageTitleParts: Array<String>
        get() {
            val planDayDesc = readingPlanControl.currentDayDescription
            return getTwoTitleParts(planDayDesc, true)
        }

    private fun onDocumentTitleClick() {
        val readingPlanActivity = activity
        val docHandlerIntent = Intent(readingPlanActivity, ReadingPlanSelectorList::class.java)
        readingPlanActivity.startActivityForResult(docHandlerIntent, 1)
        readingPlanActivity.finish()
    }

    private fun onPageTitleClick() {
        val currentActivity = activity
        val pageHandlerIntent = Intent(currentActivity, DailyReadingList::class.java)
        currentActivity.startActivityForResult(pageHandlerIntent, 1)
        currentActivity.finish()
    }

    companion object {

        private val titleSplitter = TitleSplitter()

        private const val TAG = "Title"
    }

}

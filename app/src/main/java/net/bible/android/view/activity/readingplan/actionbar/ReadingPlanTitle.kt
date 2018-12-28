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

package net.bible.android.view.activity.readingplan.actionbar

import android.app.Activity
import android.content.Intent
import androidx.appcompat.app.ActionBar

import net.bible.android.control.ApplicationScope
import net.bible.android.control.readingplan.ReadingPlanControl
import net.bible.android.view.activity.base.actionbar.Title
import net.bible.android.view.activity.readingplan.DailyReadingList
import net.bible.android.view.activity.readingplan.ReadingPlanSelectorList

import javax.inject.Inject

/**
 * Show current verse/key and document on left of actionBar
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
@ApplicationScope
class ReadingPlanTitle @Inject
constructor(private val readingPlanControl: ReadingPlanControl) : Title() {

    override val documentTitleParts: Array<String>
        get() {
            val title = readingPlanControl.shortTitle
            return getTwoTitleParts(title, false)
        }

    override val pageTitleParts: Array<String>
        get() {
            val planDayDesc = readingPlanControl.currentDayDescription
            return getTwoTitleParts(planDayDesc, true)
        }

    override fun addToBar(actionBar: ActionBar, activity: Activity) {
        super.addToBar(actionBar, activity)

    }

    override fun onDocumentTitleClick() {
        val readingPlanActivity = activity
        val docHandlerIntent = Intent(readingPlanActivity, ReadingPlanSelectorList::class.java)
        readingPlanActivity.startActivityForResult(docHandlerIntent, 1)
        readingPlanActivity.finish()
    }

    override fun onPageTitleClick() {
        val currentActivity = activity
        val pageHandlerIntent = Intent(currentActivity, DailyReadingList::class.java)
        currentActivity.startActivityForResult(pageHandlerIntent, 1)
        currentActivity.finish()
    }

    override fun onHomeButtonClick() {

    }
}

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
import androidx.appcompat.app.ActionBar
import android.view.Menu

import net.bible.android.control.ApplicationScope
import net.bible.android.control.event.ABEventBus
import net.bible.android.view.activity.base.CurrentActivityHolder
import net.bible.android.view.activity.base.actionbar.ActionBarManager
import net.bible.android.view.activity.base.actionbar.DefaultActionBarManager
import net.bible.service.device.speak.event.SpeakEvent

import javax.inject.Inject

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 */
@ApplicationScope
class ReadingPlanActionBarManager @Inject
constructor(private val readingPlanTitle: ReadingPlanTitle, private val pauseActionBarButton: ReadingPlanPauseActionBarButton, private val stopActionBarButton: ReadingPlanStopActionBarButton, private val bibleActionBarButton: ReadingPlanBibleActionBarButton, private val commentaryActionBarButton: ReadingPlanCommentaryActionBarButton, private val dictionaryActionBarButton: ReadingPlanDictionaryActionBarButton) : DefaultActionBarManager(), ActionBarManager {

    init {

        ABEventBus.getDefault().register(this)
    }

    fun onEvent(e: SpeakEvent) {
        updateButtons()
    }

    override fun prepareOptionsMenu(activity: Activity, menu: Menu, actionBar: ActionBar) {
        super.prepareOptionsMenu(activity, menu, actionBar)

        readingPlanTitle.addToBar(actionBar, activity)

        // order is important to keep bible, cmtry, ... in same place on right
        stopActionBarButton.addToMenu(menu)
        pauseActionBarButton.addToMenu(menu)

        dictionaryActionBarButton.addToMenu(menu)
        commentaryActionBarButton.addToMenu(menu)
        bibleActionBarButton.addToMenu(menu)
    }

    override fun updateButtons() {
        super.updateButtons()

        CurrentActivityHolder.getInstance().runOnUiThread {
            readingPlanTitle.update()

            bibleActionBarButton.update()
            commentaryActionBarButton.update()
            dictionaryActionBarButton.update()

            pauseActionBarButton.update()
            stopActionBarButton.update()
        }
    }
}

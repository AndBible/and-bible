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

import android.graphics.Color
import androidx.appcompat.widget.AppCompatEditText
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout

import net.bible.android.control.event.ABEventBus
import net.bible.android.control.event.apptobackground.AppToBackgroundEvent
import net.bible.android.control.event.passage.BeforeCurrentPageChangeEvent
import net.bible.android.control.event.passage.SynchronizeWindowsEvent
import net.bible.android.control.mynote.MyNoteControl
import net.bible.android.view.activity.base.DocumentView
import net.bible.android.view.activity.page.MainBibleActivity
import net.bible.service.common.CommonUtils
import net.bible.service.device.ScreenSettings


/**
 * Show a User Note and allow view/edit
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class MyNoteEditTextView(private val mainBibleActivity: MainBibleActivity, private val myNoteControl: MyNoteControl) :
		AppCompatEditText(mainBibleActivity), DocumentView {

    init {

        setSingleLine(false)
        val layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
        )
        setLayoutParams(layoutParams)
        gravity = Gravity.TOP
        isVerticalScrollBarEnabled = true
        updatePadding()
    }

    private fun updatePadding() {
        setPadding(mainBibleActivity.leftOffset1.toInt(),
                mainBibleActivity.topOffsetWithActionBar.toInt(),
                mainBibleActivity.rightOffset1.toInt(),
                mainBibleActivity.bottomOffset2.toInt())
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updatePadding()
        load()

        // register for passage change events
        ABEventBus.getDefault().register(this)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()

        // register for passage change events
        ABEventBus.getDefault().unregister(this)
    }

    /** allow current page to save any settings or data before being changed
     */
    fun onEvent(event: BeforeCurrentPageChangeEvent) {
        // force MyNote.save if in MyNote and suddenly change to another view
        save()
        ABEventBus.getDefault().post(SynchronizeWindowsEvent())
    }

	fun onEvent(event: AppToBackgroundEvent) {
		save()
	}

    private fun save() {
        myNoteControl.saveMyNoteText(text!!.toString())
    }

    fun load() {
        applyPreferenceSettings()
        val currentPage = myNoteControl.activeWindowPageManagerProvider.activeWindowPageManager.currentMyNotePage
        setText(currentPage.currentPageContent)
        updatePadding()
    }

    override fun applyPreferenceSettings() {
        changeBackgroundColour()

        val fontSize = mainBibleActivity.windowRepository.textDisplaySettings.fontSize!!
        setTextSize(TypedValue.COMPLEX_UNIT_DIP, fontSize.toFloat())
    }

    override fun changeBackgroundColour() {
        if (ScreenSettings.nightMode) {
            setBackgroundColor(Color.BLACK)
            setTextColor(Color.WHITE)
        } else {
            setBackgroundColor(Color.WHITE)
            setTextColor(Color.BLACK)
        }
    }

    override val isPageNextOkay = false

    override val isPagePreviousOkay = false

    override fun pageDown(toBottom: Boolean): Boolean {
        return false
    }

    override val currentPosition = 0f

    override fun asView(): View {
        return this
    }

    override fun onScreenTurnedOn() {
        // NOOP
    }

    override fun onScreenTurnedOff() {
        // NOOP
    }

    companion object {

        private val TAG = "MyNoteEditTextView"
    }
}

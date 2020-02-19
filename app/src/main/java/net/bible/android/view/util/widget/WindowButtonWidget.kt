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

package net.bible.android.view.util.widget

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import kotlinx.android.synthetic.main.window_button.view.*
import net.bible.android.activity.R
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.event.window.CurrentWindowChangedEvent
import net.bible.android.control.page.window.Window
import net.bible.android.control.page.window.WindowChangedEvent
import net.bible.android.database.WorkspaceEntities

@SuppressLint("ViewConstructor")
class WindowButtonWidget(
    val window: Window?,
    var activeWindow: Window,
    context: Context,
    attributeSet: AttributeSet? = null
): LinearLayout(context, attributeSet)
{
    lateinit var value: WorkspaceEntities.MarginSize
    init {
        val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        inflater.inflate(R.layout.window_button, this, true)
        updateSettings()
        updateBackground()
    }

    fun onEvent(event: CurrentWindowChangedEvent) {
        activeWindow = event.activeWindow
        updateBackground()
    }

    fun onEvent(event: WindowChangedEvent) {
        if(event.window.id == window?.id) {
            updateSettings()
        }
    }

    private fun updateSettings() {
        synchronize.visibility = if(window?.isSynchronised == true) View.VISIBLE else View.INVISIBLE
        swapMode.visibility = if(window?.isSwapMode == true) View.VISIBLE else View.INVISIBLE
    }

    private fun updateBackground() {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT) {
            windowButton.setBackgroundResource(if (window?.id == activeWindow.id) R.drawable.window_button_active else R.drawable.window_button)
        }
    }

    override fun setOnClickListener(l: OnClickListener?) {
        windowButton.setOnClickListener(l)
    }

    override fun setOnLongClickListener(l: OnLongClickListener?) {
        windowButton.setOnLongClickListener(l)
    }

    var text: String
        get() = buttonText.text.toString()

        set(value) {
            buttonText.text = value
        }

    override fun onAttachedToWindow() {
        ABEventBus.getDefault().register(this)
        super.onAttachedToWindow()
    }

    override fun onDetachedFromWindow() {
        ABEventBus.getDefault().unregister(this)
        super.onDetachedFromWindow()
    }
}

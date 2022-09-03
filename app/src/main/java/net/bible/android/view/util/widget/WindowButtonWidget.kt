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
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import androidx.core.text.HtmlCompat
import net.bible.android.activity.R
import net.bible.android.activity.databinding.WindowButtonBinding
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.event.window.CurrentWindowChangedEvent
import net.bible.android.control.page.window.Window
import net.bible.android.control.page.window.WindowChangedEvent
import net.bible.android.control.page.window.WindowControl
import net.bible.android.view.activity.download.imageResource
import net.bible.service.common.CommonUtils
import net.bible.service.common.CommonUtils.getResourceColor

@SuppressLint("ViewConstructor")
class WindowButtonWidget(
    val window: Window?,
    var windowControl: WindowControl,
    private val isRestoreButton: Boolean,
    context: Context,
    attributeSet: AttributeSet? = null

): LinearLayout(context, attributeSet)
{

    private val binding = WindowButtonBinding.inflate(
        context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater,
        this, true
    )

    init {
        updateSettings()
        updateBackground()
    }

    fun onEvent(event: CurrentWindowChangedEvent) {
        updateBackground()
    }

    fun onEvent(event: WindowChangedEvent) {
        if(event.window.id == window?.id) {
            updateSettings()
        }
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if(event?.keyCode == KeyEvent.KEYCODE_SPACE) {
            performLongClick()
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    private val isMaximised get() = windowControl.windowRepository.isMaximized

    fun updateSettings() {
        binding.synchronize.visibility = if(window !== null && window.isSyncable && window.isSynchronised && !isMaximised)
            View.VISIBLE
        else View.INVISIBLE
        binding.docType.visibility = if(isMaximised) View.INVISIBLE else View.VISIBLE
        if(CommonUtils.booleanSettings.get("enable_show_verse_in_button_pref", false)) {
            binding.pinMode.visibility = GONE
        } else {
            binding.pinMode.visibility =
                if (!windowControl.windowRepository.workspaceSettings.autoPin
                    && window?.isPinMode == true
                    && !isMaximised
                )
                    View.VISIBLE
                else
                    View.INVISIBLE
        }
    }

    private fun updateBackground() {
        val isPinnedWindow = window?.isPinMode == true
        val isLinksWindow = window?.isLinksWindow == true
        val isActive = windowControl.activeWindow.id == window?.id && !isMaximised
        val isWindowVisible = if(isRestoreButton) {
            window?.isVisible == true
        }
        else {
            window?.id == windowControl.activeWindow.id && !isMaximised
        }

        binding.apply {
            if (isRestoreButton) {
                windowButton.setBackgroundResource(
                    when {
                        isActive && (isPinnedWindow || isLinksWindow) -> R.drawable.bar_window_button_active
                        isWindowVisible && (isPinnedWindow || isLinksWindow) -> R.drawable.bar_window_button_visible
                        isPinnedWindow || isLinksWindow -> R.drawable.bar_window_button
                        isActive -> R.drawable.bar_window_unpinned_button_active
                        isWindowVisible -> R.drawable.bar_window_unpinned_button_visible
                        else -> R.drawable.bar_window_unpinned_button
                    }
                )
            } else {
                windowButton.setBackgroundResource(
                    when {
                        isActive -> R.drawable.window_button_active
                        isWindowVisible -> R.drawable.window_button_visible
                        else -> R.drawable.window_button
                    }
                )
            }
            if (isRestoreButton) {
                buttonText.textSize = 13.0f
                val color = getResourceColor(R.color.bar_window_button_text_colour)
                buttonText.setTextColor(color)
                val image = window?.pageManager?.currentPage?.currentDocument?.imageResource
                if (image != null)
                    docType.setImageResource(image)
            } else {
                buttonText.setTextColor(getResourceColor(R.color.window_button_text_colour))
                windowButton.setTextColor(getResourceColor(R.color.window_button_text_colour))
                buttonText.visibility = View.GONE
                docType.visibility = View.GONE
            }
            if (window?.isLinksWindow == true && !isMaximised) {
                docType.setImageResource(R.drawable.ic_link_black_24dp)
                docType.setColorFilter(getResourceColor(R.color.links_button_icon_color))
                docType.visibility = View.VISIBLE
            }
            unMaximiseImage.visibility = if (isMaximised) View.VISIBLE else View.GONE
        }
    }

    override fun setOnClickListener(l: OnClickListener?) {
        binding.apply {
            unMaximiseImage.setOnClickListener(l)
            windowButton.setOnClickListener(l)
            buttonText.setOnClickListener(l)
            synchronize.setOnClickListener(l)
            pinMode.setOnClickListener(l)
            docType.setOnClickListener(l)
        }
        super.setOnClickListener(l)
    }

    override fun setOnLongClickListener(l: OnLongClickListener?) {
        binding.apply {
            unMaximiseImage.setOnLongClickListener(l)
            windowButton.setOnLongClickListener(l)
            buttonText.setOnLongClickListener(l)
            synchronize.setOnLongClickListener(l)
            pinMode.setOnLongClickListener(l)
            docType.setOnLongClickListener(l)
        }
        super.setOnLongClickListener(l)
    }

    override fun setOnTouchListener(l: OnTouchListener?) {
        binding.apply {
            windowButton.setOnTouchListener(l)
        }
        super.setOnTouchListener(l)
    }

    var text: String
        get() = (if(isRestoreButton) binding.buttonText else binding.windowButton).toString()

        set(value) {
            (if(isRestoreButton) binding.buttonText else binding.windowButton).text = value
        }

    var topText: String
        get() = (if(isRestoreButton) binding.topButtonText else binding.windowButton).toString()

        set(value) {
            (if(isRestoreButton) binding.topButtonText else binding.windowButton).text = value
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

@SuppressLint("ViewConstructor")
class AddNewWindowButtonWidget(
    context: Context,
    attributeSet: AttributeSet? = null
): LinearLayout(context, attributeSet)
{

    private val binding = WindowButtonBinding.inflate(
        context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater,
        this, true
    )

    init {
        binding.apply {
            windowButton.setTextColor(getResourceColor(R.color.window_button_text_colour))
            windowButtonIcon.visibility = View.VISIBLE
            windowButtonIcon.setImageDrawable(
                CommonUtils.getTintedDrawable(R.drawable.ic_window_add_outline_black_24dp, R.color.window_button_text_colour)
            )
            buttonText.text = ""
            synchronize.visibility = View.GONE
            docType.visibility = View.GONE
            pinMode.visibility = View.GONE
            unMaximiseImage.visibility = View.GONE
            windowButton.setBackgroundResource(R.drawable.new_window_button)
        }
    }

    override fun setOnClickListener(l: OnClickListener?) {
        binding.apply {
            windowButton.setOnClickListener(l)
            buttonText.setOnClickListener(l)
        }
        super.setOnClickListener(l)
    }

    override fun setOnLongClickListener(l: OnLongClickListener?) {
        binding.apply {
            unMaximiseImage.setOnLongClickListener(l)
            buttonText.setOnLongClickListener(l)
        }
        super.setOnLongClickListener(l)
    }

    override fun setOnTouchListener(l: OnTouchListener?) {
        binding.apply {
            buttonText.setOnTouchListener(l)
        }
        super.setOnTouchListener(l)
    }
}

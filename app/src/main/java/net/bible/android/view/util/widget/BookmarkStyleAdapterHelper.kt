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

import android.content.Context
import android.graphics.Color
import android.text.SpannableString
import android.text.style.ImageSpan
import android.text.style.UnderlineSpan
import android.view.Gravity
import android.widget.TextView
import net.bible.android.activity.R
import net.bible.android.control.bookmark.BookmarkStyle
import net.bible.android.view.util.UiUtils.getThemeBackgroundColour
import net.bible.android.view.util.UiUtils.getThemeTextColour
import net.bible.service.common.CommonUtils.convertDipsToPx
import net.bible.service.common.CommonUtils.getResourceString
import org.apache.commons.lang3.StringUtils

/**
 * Set each list view item to represent background colour of icon of the relevant bookmark style.
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class BookmarkStyleAdapterHelper {
    private val sampleText = getResourceString(R.string.prefs_text_size_sample_text)

    @JvmOverloads
    fun styleView(
        view: TextView,
        bookmarkStyle: BookmarkStyle?,
        context: Context,
        overrideText: Boolean,
        centreText: Boolean,
        emphasize: Boolean = false
    ) {

        // prepare text to be shown
        var baseText: String
        if (overrideText) {
            baseText = sampleText
        } else {
            baseText = view.text.toString()
            // avoid multiple *'s
            if (baseText.startsWith("*")) {
                StringUtils.strip(baseText, "*")
            }
        }
        if (emphasize) {
            baseText = "⤇ $baseText ⤆"
        }
        var backgroundColor = Color.WHITE
        val imgText: CharSequence
        when (bookmarkStyle) {
            BookmarkStyle.SPEAK -> {
                backgroundColor = getThemeBackgroundColour(context)
                view.setTextColor(getThemeTextColour(context))
                imgText = addImageAtStart("* $baseText", R.drawable.hearing, context)
                view.setText(imgText, TextView.BufferType.SPANNABLE)
            }
            BookmarkStyle.YELLOW_STAR -> {
                backgroundColor = getThemeBackgroundColour(context)
                view.setTextColor(getThemeTextColour(context))
                imgText = addImageAtStart("* $baseText", R.drawable.goldstar16x16, context)
                view.setText(imgText, TextView.BufferType.SPANNABLE)
            }
            BookmarkStyle.RED_HIGHLIGHT -> {
                backgroundColor = BookmarkStyle.RED_HIGHLIGHT.backgroundColor
                view.text = baseText
            }
            BookmarkStyle.YELLOW_HIGHLIGHT -> {
                backgroundColor = BookmarkStyle.YELLOW_HIGHLIGHT.backgroundColor
                view.text = baseText
            }
            BookmarkStyle.GREEN_HIGHLIGHT -> {
                backgroundColor = BookmarkStyle.GREEN_HIGHLIGHT.backgroundColor
                view.text = baseText
            }
            BookmarkStyle.BLUE_HIGHLIGHT -> {
                backgroundColor = BookmarkStyle.BLUE_HIGHLIGHT.backgroundColor
                view.text = baseText
            }
            BookmarkStyle.ORANGE_HIGHLIGHT -> {
                backgroundColor = BookmarkStyle.ORANGE_HIGHLIGHT.backgroundColor
                view.text = baseText
            }
            BookmarkStyle.PURPLE_HIGHLIGHT -> {
                backgroundColor = BookmarkStyle.PURPLE_HIGHLIGHT.backgroundColor
                view.text = baseText
            }
            BookmarkStyle.UNDERLINE -> {
                backgroundColor = getThemeBackgroundColour(context)
                view.setTextColor(getThemeTextColour(context))
                var text = SpannableString(baseText)
                text.setSpan(UnderlineSpan(), 0, text.length, 0)
                view.setText(text)
            }
        }
        view.setBackgroundColor(backgroundColor)
        view.height = convertDipsToPx(30)
        if (centreText) {
            view.gravity = Gravity.CENTER
        }
    }

    /**
     * Replace first character of text with image
     */
    private fun addImageAtStart(text: String, drawableImage: Int, context: Context): CharSequence {
        val imageSpan = ImageSpan(context, drawableImage, ImageSpan.ALIGN_BASELINE)
        val spannableString = SpannableString(text)
        spannableString.setSpan(imageSpan, 0, 1, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE)
        return spannableString
    }
}

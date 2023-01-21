/*
 * Copyright (c) 2020-2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */
package net.bible.android.view.util.widget

import android.content.Context
import android.text.SpannableString
import android.text.style.ImageSpan
import android.view.Gravity
import android.widget.TextView
import net.bible.android.activity.R
import net.bible.android.database.bookmarks.BookmarkEntities
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

    fun styleView(
        view: TextView,
        label: BookmarkEntities.Label,
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
        val imgText: CharSequence
        if (label.isSpeakLabel) {
            imgText = addImageAtStart("* $baseText", R.drawable.ic_baseline_headphones_24, context)
            view.setText(imgText, TextView.BufferType.SPANNABLE)
        } else {
            view.text = baseText
        }
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

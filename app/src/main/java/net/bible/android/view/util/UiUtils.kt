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

package net.bible.android.view.util

import android.app.Activity
import android.content.Context
import android.graphics.drawable.ColorDrawable
import androidx.appcompat.app.ActionBar
import android.util.TypedValue
import android.view.View

import net.bible.android.activity.R
import net.bible.android.view.activity.base.CurrentActivityHolder
import net.bible.service.common.CommonUtils
import net.bible.service.device.ScreenSettings

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 */
object UiUtils {

    private val ACTIONBAR_BACKGROUND_NIGHT = CommonUtils.getResourceColor(R.color.actionbar_background_night)
    private val ACTIONBAR_BACKGROUND_DAY = CommonUtils.getResourceColor(R.color.actionbar_background_day)

    private val BIBLEVIEW_BACKGROUND_NIGHT = CommonUtils.getResourceColor(R.color.bible_background_night)
    private val BIBLEVIEW_BACKGROUND_DAY = CommonUtils.getResourceColor(R.color.bible_background_day)

    // taken from css
    private val BIBLEVIEW_TEXT_NIGHT = CommonUtils.getResourceColor(R.color.bible_text_night)
    private val BIBLEVIEW_TEXT_DAY = CommonUtils.getResourceColor(R.color.bible_text_day)

    private val backgroundColour: Int
        get() = if (ScreenSettings.nightMode) BIBLEVIEW_BACKGROUND_NIGHT else BIBLEVIEW_BACKGROUND_DAY
    private val textColour: Int
        get() = if (ScreenSettings.nightMode) BIBLEVIEW_TEXT_NIGHT else BIBLEVIEW_TEXT_DAY


    /** Change actionBar colour according to day/night state
     */
    fun setActionBarColor(actionBar: ActionBar?) {
        val newColor = if (ScreenSettings.nightMode) ACTIONBAR_BACKGROUND_NIGHT else ACTIONBAR_BACKGROUND_DAY

        if (actionBar != null) {
            CurrentActivityHolder.getInstance().runOnUiThread {
                val colorDrawable = ColorDrawable(newColor)
                actionBar.setBackgroundDrawable(colorDrawable)
            }
        }
    }

    fun setBibleViewBackgroundColour(bibleView: View, nightMode: Boolean) {
        bibleView.setBackgroundColor(if (nightMode) BIBLEVIEW_BACKGROUND_NIGHT else BIBLEVIEW_BACKGROUND_DAY)
    }

    fun getThemeBackgroundColour(context: Context): Int {
        val a = TypedValue()
        context.theme.resolveAttribute(android.R.attr.windowBackground, a, true)

        return if (a.type >= TypedValue.TYPE_FIRST_COLOR_INT && a.type <= TypedValue.TYPE_LAST_COLOR_INT) {
            // windowBackground is a color
            a.data
        } else {
            // windowBackground is not a color, probably a drawable so just guess a colour, but hopefully theme backgrounds are always colors anyway
            backgroundColour
        }
    }

    fun getThemeTextColour(context: Context): Int {
        val a = TypedValue()
        context.theme.resolveAttribute(android.R.attr.textColor, a, true)

        return if (a.type >= TypedValue.TYPE_FIRST_COLOR_INT && a.type <= TypedValue.TYPE_LAST_COLOR_INT) {
            // textColor is a color
            a.data
        } else {
            // textColor is not a color, probably a drawable so just guess a colour, but hopefully theme backgrounds are always colors anyway
            textColour
        }
    }
}

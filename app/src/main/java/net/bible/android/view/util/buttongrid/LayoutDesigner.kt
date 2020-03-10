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
package net.bible.android.view.util.buttongrid

import android.content.res.Configuration
import android.util.Log
import android.view.View
import org.apache.commons.lang3.StringUtils

/** Calculate the number of columns and rows to be used to layout a grid of bible books, numbers, or whatever
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class LayoutDesigner(private val view: View) {

    class RowColLayout {
        var rows = 0
        var cols = 0

        /** column order if portrait mode to provide longer 'runs'  */
        var columnOrder = false
    }

    companion object {
        private const val MIN_COLS = 5
        private const val MIN_COLS_LAND = 8
        private const val TAG = "LayoutDesigner"
        private val BIBLE_BOOK_LAYOUT = RowColLayout()
        private val BIBLE_BOOK_LAYOUT_LAND = RowColLayout()

        init {
            BIBLE_BOOK_LAYOUT.rows = 11
            BIBLE_BOOK_LAYOUT.cols = 6
            BIBLE_BOOK_LAYOUT_LAND.rows = 6
            BIBLE_BOOK_LAYOUT_LAND.cols = 11
        }
    }

    fun calculateLayout(buttonInfoList: List<ButtonInfo>): RowColLayout {
        var rowColLayout = RowColLayout()
        val numButtons = buttonInfoList.size

        // is it the list of bible books
        if (buttonInfoList.size == 66 && !StringUtils.isNumeric(buttonInfoList[0].name)) {
            // bible books
            rowColLayout = if (isPortrait) {
                BIBLE_BOOK_LAYOUT
            } else {
                BIBLE_BOOK_LAYOUT_LAND
            }
        } else {
            // a list of chapters or verses
            if (numButtons <= 50) {
                if (isPortrait) {
                    rowColLayout.rows = 10
                } else {
                    rowColLayout.rows = 5
                }
            } else if (numButtons <= 100) {
                rowColLayout.rows = 10
            } else {
                if (isPortrait) {
                    rowColLayout.rows = 15
                } else {
                    rowColLayout.rows = 10
                }
            }
            rowColLayout.cols = Math.ceil(numButtons.toFloat() / rowColLayout.rows.toDouble()).toInt()

            // if there are too few buttons/rows you just see a couple of large buttons on the screen so ensure there are enough rows to look nice
            val minCols = if (isPortrait) MIN_COLS else MIN_COLS_LAND
            rowColLayout.cols = Math.max(minCols, rowColLayout.cols)
        }
        rowColLayout.columnOrder = isPortrait
        Log.d(TAG, "Rows:" + rowColLayout.rows + " Cols:" + rowColLayout.cols)
        return rowColLayout
    }

    private val isPortrait: Boolean
        get() = view.context.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT

}

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
package net.bible.android.view.activity.bookmark

import android.app.AlertDialog
import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.TextView
import com.jaredrummler.android.colorpicker.ColorPickerView
import net.bible.android.activity.R
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.android.view.activity.base.Callback
import net.bible.service.common.displayName
import javax.inject.Inject

/**
 * Label dialogs - edit or create label.  Used in a couple of places so extracted.
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class LabelDialogs @Inject constructor(private val bookmarkControl: BookmarkControl) {
    fun createLabel(context: Context, label: BookmarkEntities.Label, onCreateCallback: Callback) {
        showDialog(context, R.string.new_label, label, onCreateCallback)
    }

    fun editLabel(context: Context, label: BookmarkEntities.Label, onCreateCallback: Callback) {
        showDialog(context, R.string.edit, label, onCreateCallback)
    }

    private fun showDialog(context: Context, titleId: Int, label: BookmarkEntities.Label, onCreateCallback: Callback) {
        Log.i(TAG, "Edit label clicked")
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.bookmark_label_edit, null)
        val labelName = view.findViewById<View>(R.id.labelName) as EditText
        val colorExample = view.findViewById<View>(R.id.labelColorExample) as TextView
        labelName.setText(label.displayName)
        val color = view.findViewById<View>(R.id.colorPicker) as ColorPickerView
        color.color = label.color
        colorExample.setBackgroundColor(label.color)
        color.setOnColorChangedListener {
            colorExample.setBackgroundColor(it)
        }
        AlertDialog.Builder(context)
            .setTitle(titleId)
            .setView(view)
            .setPositiveButton(R.string.okay) { _, _ ->
                val name = labelName.text.toString()
                label.name = name
                // let's remove alpha
                label.color = color.color or (255 shl 24)
                bookmarkControl.insertOrUpdateLabel(label)
                onCreateCallback.okay()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    companion object {
        private const val TAG = "LabelDialogs"
    }
}

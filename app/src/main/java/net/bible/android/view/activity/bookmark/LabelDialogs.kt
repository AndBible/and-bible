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
import android.widget.Spinner
import net.bible.android.activity.R
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.android.view.activity.base.Callback
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
        labelName.setText(label.name)
        val adp = BookmarkStyleAdapter(context, android.R.layout.simple_spinner_item)
        val labelStyle = view.findViewById<View>(R.id.labelStyle) as Spinner
        labelStyle.adapter = adp
        labelStyle.setSelection(adp.getBookmarkStyleOffset(label.bookmarkStyle))
        val alert = AlertDialog.Builder(context)
            .setTitle(titleId)
            .setView(view)
        alert.setPositiveButton(R.string.okay) { dialog, whichButton ->
            val name = labelName.text.toString()
            label.name = name
            label.bookmarkStyle = adp.getBookmarkStyleForOffset(labelStyle.selectedItemPosition)
            bookmarkControl.insertOrUpdateLabel(label)
            onCreateCallback.okay()
        }
        alert.setNegativeButton(R.string.cancel) { dialog, whichButton ->
            // Canceled.
        }
        val dialog = alert.show()
    }

    companion object {
        private const val TAG = "LabelDialogs"
    }
}

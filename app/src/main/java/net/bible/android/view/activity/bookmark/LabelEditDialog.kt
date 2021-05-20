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
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import net.bible.android.activity.databinding.BookmarkLabelEditBinding
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.android.view.activity.page.MainBibleActivity.Companion.mainBibleActivity
import net.bible.service.common.displayName
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Label dialogs - edit or create label.  Used in a couple of places so extracted.
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */

class LabelEditWidget(context: Context, attributeSet: AttributeSet?, label: BookmarkEntities.Label): LinearLayout(context, attributeSet) {
    private val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
    val bindings = BookmarkLabelEditBinding.inflate(inflater, this, true)
    init {
        bindings.apply {
            if (label.isUnlabeledLabel) {
                labelName.isEnabled = false
            }
            labelName.setText(label.displayName)
            colorPicker.color = label.color
            labelColorExample.setBackgroundColor(label.color)
            colorPicker.setOnColorChangedListener {
                labelColorExample.setBackgroundColor(it)
            }
        }
    }
}

enum class LabelDialogResult {OK, CANCEL, REMOVE}

class LabelEditDialog @Inject constructor(
    private val bookmarkControl: BookmarkControl,
) {
    private val workspaceSettings get() = mainBibleActivity.windowRepository.windowBehaviorSettings

    suspend fun showDialog(context: Context, label: BookmarkEntities.Label) = suspendCoroutine<LabelDialogResult> { r->
        Log.i(TAG, "Edit label clicked")
        val view = LabelEditWidget(context, null, label)
        val dialog = AlertDialog.Builder(context)
            .setView(view)
            .setCancelable(false)
            .create()
        val isNewLabel = label.id == 0L
        view.bindings.favouriteLabelCheckBox.isChecked = workspaceSettings.favouriteLabels.contains(label.id)
        view.bindings.autoAssignCheckBox.isChecked = workspaceSettings.autoAssignLabels.contains(label.id)
        view.bindings.primaryAutoAssignCheckBox.isChecked = workspaceSettings.autoAssignPrimaryLabel == label.id
        view.bindings.okButton.setOnClickListener {
            if(!label.isUnlabeledLabel) {
                val name = view.bindings.labelName.text.toString()
                label.name = name
            }
            // let's remove alpha
            label.color = view.bindings.colorPicker.color or (255 shl 24)
            bookmarkControl.insertOrUpdateLabel(label)

            if(view.bindings.favouriteLabelCheckBox.isChecked) {
                workspaceSettings.favouriteLabels.add(label.id)
            } else {
                workspaceSettings.favouriteLabels.remove(label.id)
            }

            if(view.bindings.autoAssignCheckBox.isChecked) {
                workspaceSettings.autoAssignLabels.add(label.id)
            } else {
                workspaceSettings.autoAssignLabels.remove(label.id)
            }
            if(view.bindings.primaryAutoAssignCheckBox.isChecked) {
                workspaceSettings.autoAssignPrimaryLabel = label.id
            }

            r.resume(LabelDialogResult.OK)
            dialog.dismiss()
        }
        if(isNewLabel) {
            view.bindings.removeButton.visibility = View.GONE
        }
        view.bindings.cancelButton.setOnClickListener {
            r.resume(LabelDialogResult.CANCEL)
            dialog.dismiss()
        }
        view.bindings.removeButton.setOnClickListener {
            r.resume(LabelDialogResult.REMOVE)
            bookmarkControl.deleteLabel(label)
            dialog.dismiss()
        }
        dialog.show()
    }

    companion object {
        private const val TAG = "LabelDialogs"
    }
}

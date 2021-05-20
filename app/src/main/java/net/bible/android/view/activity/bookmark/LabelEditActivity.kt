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

import android.app.Activity
import android.os.Bundle
import android.view.View
import net.bible.android.activity.databinding.BookmarkLabelEditBinding
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.android.view.activity.ActivityScope
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.page.MainBibleActivity.Companion.mainBibleActivity
import net.bible.service.common.displayName
import javax.inject.Inject

@ActivityScope
class LabelEditActivity: ActivityBase() {
    lateinit var binding: BookmarkLabelEditBinding
    @Inject lateinit var bookmarkControl: BookmarkControl
    private val workspaceSettings get() = mainBibleActivity.windowRepository.windowBehaviorSettings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = BookmarkLabelEditBinding.inflate(layoutInflater)
        setContentView(binding.root)
        buildActivityComponent().inject(this)
        val labelId = intent.getLongExtra("labelId", 0)
        val label = if(labelId > 0) bookmarkControl.labelById(labelId)!! else BookmarkEntities.Label()

        binding.apply {
            if (label.isUnlabeledLabel) {
                labelName.isEnabled = false
            }
            labelName.setText(label.displayName)
            colorPicker.color = label.color
            labelColorExample.setBackgroundColor(label.color)
            colorPicker.setOnColorChangedListener {
                labelColorExample.setBackgroundColor(it)
            }


            val isNewLabel = label.id == 0L
            favouriteLabelCheckBox.isChecked = workspaceSettings.favouriteLabels.contains(label.id)
            autoAssignCheckBox.isChecked = workspaceSettings.autoAssignLabels.contains(label.id)
            primaryAutoAssignCheckBox.isChecked = workspaceSettings.autoAssignPrimaryLabel == label.id
            okButton.setOnClickListener {
                if(!label.isUnlabeledLabel) {
                    val name = labelName.text.toString()
                    label.name = name
                }
                // let's remove alpha
                label.color = colorPicker.color or (255 shl 24)
                bookmarkControl.insertOrUpdateLabel(label)

                if(favouriteLabelCheckBox.isChecked) {
                    workspaceSettings.favouriteLabels.add(label.id)
                } else {
                    workspaceSettings.favouriteLabels.remove(label.id)
                }

                if(autoAssignCheckBox.isChecked) {
                    workspaceSettings.autoAssignLabels.add(label.id)
                } else {
                    workspaceSettings.autoAssignLabels.remove(label.id)
                }
                if(primaryAutoAssignCheckBox.isChecked) {
                    workspaceSettings.autoAssignPrimaryLabel = label.id
                }
                setResult(Activity.RESULT_OK)
                finish()
            }
            if(isNewLabel) {
                removeButton.visibility = View.GONE
            }
            cancelButton.setOnClickListener {
                setResult(Activity.RESULT_CANCELED)
                finish()
            }
            removeButton.setOnClickListener {
                setResult(RESULT_REMOVE)
                finish()
            }
        }
    }
    companion object {
        const val RESULT_REMOVE = 999
    }
}

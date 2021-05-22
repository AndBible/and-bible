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
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import com.jaredrummler.android.colorpicker.ColorPickerDialog
import com.jaredrummler.android.colorpicker.ColorPickerDialogListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import net.bible.android.activity.R
import net.bible.android.activity.databinding.BookmarkLabelEditBinding
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.android.view.activity.ActivityScope
import net.bible.android.view.activity.base.ActivityBase
import net.bible.service.common.CommonUtils.json
import net.bible.service.common.displayName
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@ActivityScope
class LabelEditActivity: ActivityBase(), ColorPickerDialogListener {
    lateinit var binding: BookmarkLabelEditBinding

    override fun onColorSelected(dialogId: Int, color: Int) {
        // let's remove alpha
        data.label.color = color or (255 shl 24)
    }

    private fun updateColor() {
        binding.titleIcon.setColorFilter(data.label.color)
    }

    override fun onDialogDismissed(dialogId: Int) {}

    @Serializable
    data class LabelData (
        val isAssigning: Boolean,
        var label: BookmarkEntities.Label,

        var isAutoAssign: Boolean,
        var isFavourite: Boolean,

        var isAutoAssignPrimary: Boolean,
        var isThisBookmarkPrimary: Boolean,
        var delete: Boolean = false,
    ) {
        fun toJSON(): String = json.encodeToString(serializer(), this)

        companion object {
            fun fromJSON(str: String): LabelData = json.decodeFromString(serializer(), str)
        }
    }

    private lateinit var data: LabelData

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = BookmarkLabelEditBinding.inflate(layoutInflater)
        setContentView(binding.root)
        buildActivityComponent().inject(this)

        data = LabelData.fromJSON(intent.getStringExtra("data")!!)

        val isNewLabel = data.label.id == 0L

        fun updateData() = binding.apply {
            if(!data.label.isUnlabeledLabel) {
                val name = labelName.text.toString()
                data.label.name = name
            }

            data.isFavourite = favouriteLabelCheckBox.isChecked
            data.isAutoAssign = autoAssignCheckBox.isChecked
            data.isAutoAssignPrimary = primaryAutoAssignCheckBox.isChecked
            if(!data.isAutoAssign) {
                data.isAutoAssignPrimary = false
            }
            data.isThisBookmarkPrimary = primaryLabelCheckBox.isChecked
        }

        fun updateUI() = binding.apply {
            favouriteLabelCheckBox.isChecked = data.isFavourite
            autoAssignCheckBox.isChecked = data.isAutoAssign
            primaryAutoAssignCheckBox.isChecked = data.isAutoAssignPrimary
            primaryLabelCheckBox.isChecked = data.isThisBookmarkPrimary
            labelName.setText(data.label.displayName)
            updateColor()
            if (data.label.isUnlabeledLabel) {
                labelName.isEnabled = false
            }
            primaryAutoAssignCheckBox.isEnabled = data.isAutoAssign

            thisBookmarkCategory.visibility = if(data.isAssigning) View.VISIBLE else View.GONE
            removeButton.visibility = if(isNewLabel) View.GONE else View.VISIBLE
        }

        binding.apply {
            updateData()
            updateUI()

            editColorButton.setOnClickListener {
                ColorPickerDialog.newBuilder()
                    .setColor(data.label.color)
                    .show(this@LabelEditActivity)
            }

            okButton.setOnClickListener {
                updateData()

                val resultIntent = Intent()
                resultIntent.putExtra("data", data.toJSON())
                setResult(Activity.RESULT_OK, resultIntent)
                finish()
            }

            autoAssignCheckBox.setOnCheckedChangeListener { buttonView, isChecked ->
                updateData()
                updateUI()
            }

            cancelButton.setOnClickListener {
                setResult(Activity.RESULT_CANCELED)
                finish()
            }
            removeButton.setOnClickListener {
                updateData()

                GlobalScope.launch(Dispatchers.Main) {
                    val result = suspendCoroutine<Boolean> {
                        AlertDialog.Builder(this@LabelEditActivity)
                            .setMessage(getString(R.string.delete_label_confirmation, data.label.name))
                            .setPositiveButton(R.string.yes) { _, _ -> it.resume(true) }
                            .setNegativeButton(R.string.no) {_, _ -> it.resume(false)}
                            .setCancelable(true)
                            .create().show()
                    }
                    if(result) {
                        data.delete = true

                        val resultIntent = Intent()
                        resultIntent.putExtra("data", data.toJSON())
                        setResult(Activity.RESULT_OK, resultIntent)
                        finish()
                    }
                }
            }
        }
    }
    companion object {
        const val RESULT_REMOVE = 999
    }
}

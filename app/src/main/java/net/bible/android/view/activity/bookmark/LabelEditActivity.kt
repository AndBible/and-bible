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
package net.bible.android.view.activity.bookmark

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.SpannableString
import android.text.style.ImageSpan
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.View.GONE
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatCheckBox
import androidx.lifecycle.lifecycleScope
import com.jaredrummler.android.colorpicker.ColorPickerDialog
import com.jaredrummler.android.colorpicker.ColorPickerDialogListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import net.bible.android.activity.R
import net.bible.android.activity.databinding.BookmarkLabelEditBinding
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.android.view.activity.ActivityScope
import net.bible.android.view.activity.base.ActivityBase
import net.bible.service.common.CommonUtils.getTintedDrawable
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
        updateColor()
    }

    private fun updateColor() {
        binding.titleIcon.setColorFilter(data.label.color)
    }

    override fun onDialogDismissed(dialogId: Int) {
        Log.i(TAG, "onDialogDismissed")
    }

    override fun onBackPressed() {
        Log.i(TAG, "onBackPressed")
        saveAndExit()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        Log.i(TAG, "onCreateOptionsMenu")
        menuInflater.inflate(R.menu.edit_label_options_menu, menu)
        if(data.label.isSpecialLabel) {
            menu.findItem(R.id.removeLabel).isVisible = false
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        Log.i(TAG, "onOptionsItemSelected ${item.title}")
        var isHandled = true
        when(item.itemId){
            R.id.removeLabel -> remove()
            android.R.id.home -> saveAndExit()
            else -> isHandled = false
        }
        if (!isHandled) {
            isHandled = super.onOptionsItemSelected(item)
        }
        return isHandled
    }

    @Serializable
    data class LabelData (
        val isAssigning: Boolean,
        var label: BookmarkEntities.Label,

        var isAutoAssign: Boolean,

        var isAutoAssignPrimary: Boolean,
        var isThisBookmarkSelected: Boolean,
        var isThisBookmarkPrimary: Boolean,
        var delete: Boolean = false,
    ) {
        fun toJSON(): String = json.encodeToString(serializer(), this)

        companion object {
            fun fromJSON(str: String): LabelData = json.decodeFromString(serializer(), str)
        }
    }

    private lateinit var data: LabelData


    private fun updateData() = binding.apply {
        Log.i(TAG, "updateData")
        if(!data.label.isSpecialLabel) {
            val name = labelName.text.toString()
            data.label.name = name
        }
        data.label.underlineStyle = underLineStyle.isChecked
        data.label.underlineStyleWholeVerse = underLineStyleWholeVerse.isChecked
        data.label.markerStyle = markerStyle.isChecked
        data.label.markerStyleWholeVerse = markerStyleWholeVerse.isChecked
        data.label.favourite = favouriteLabelCheckBox.isChecked
        data.isAutoAssign = autoAssignCheckBox.isChecked
        data.isAutoAssignPrimary = primaryAutoAssignCheckBox.isChecked
        if(!data.isAutoAssign) {
            data.isAutoAssignPrimary = false
        }
        data.isThisBookmarkSelected = selectedLabelCheckBox.isChecked
        data.isThisBookmarkPrimary = primaryLabelCheckBox.isChecked
        if(!data.isThisBookmarkSelected) {
            data.isThisBookmarkPrimary = false
        }
    }

    private fun updateUI() = binding.apply {
        Log.i(TAG, "updateUI")
        favouriteLabelCheckBox.isChecked = data.label.favourite
        autoAssignCheckBox.isChecked = data.isAutoAssign
        primaryAutoAssignCheckBox.isChecked = data.isAutoAssignPrimary
        primaryLabelCheckBox.isChecked = data.isThisBookmarkPrimary
        labelName.setText(data.label.displayName)
        underLineStyle.isChecked = data.label.underlineStyle
        underLineStyleWholeVerse.isChecked = data.label.underlineStyleWholeVerse
        val isMarkerStyle = data.label.markerStyle
        val isMarkerStyleWholeVerse = data.label.markerStyleWholeVerse
        markerStyle.isChecked = isMarkerStyle
        markerStyleWholeVerse.isChecked = isMarkerStyleWholeVerse
        underLineStyle.isEnabled = !isMarkerStyle
        underLineStyleWholeVerse.isEnabled = !isMarkerStyleWholeVerse
        updateColor()
        if (data.label.isSpecialLabel) {
            labelName.isEnabled = false
            thisWorkspaceTitle.visibility = GONE
            favouriteLabelCheckBox.visibility = GONE
            autoAssignCheckBox.visibility = GONE
            primaryAutoAssignCheckBox.visibility = GONE
        }
        selectedLabelCheckBox.isChecked = data.isThisBookmarkSelected
        primaryLabelCheckBox.isEnabled = data.isThisBookmarkSelected
        primaryAutoAssignCheckBox.isEnabled = data.isAutoAssign

        thisBookmarkCategory.visibility = if(data.isAssigning) View.VISIBLE else View.GONE
    }

    private fun saveAndExit() {
        Log.i(TAG, "saveAndExit")

        updateData()

        val resultIntent = Intent()
        resultIntent.putExtra("data", data.toJSON())
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }

    private fun remove() {
        Log.i(TAG, "remove")
        updateData()

        lifecycleScope.launch(Dispatchers.Main) {
            val result = suspendCoroutine {
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = BookmarkLabelEditBinding.inflate(layoutInflater)
        setContentView(binding.root)
        buildActivityComponent().inject(this)

        data = LabelData.fromJSON(intent.getStringExtra("data")!!)

        binding.apply {
            addImage(favouriteLabelCheckBox, R.drawable.ic_baseline_favorite_24)
            addImage(autoAssignCheckBox, R.drawable.ic_label_circle)
            addImage(primaryAutoAssignCheckBox, R.drawable.ic_baseline_bookmark_24)
            addImage(primaryLabelCheckBox, R.drawable.ic_baseline_bookmark_24)

            updateUI()
            updateData()
            updateUI()

            titleIcon.setOnClickListener { editColor() }

            for(v in listOf(autoAssignCheckBox, markerStyle, markerStyleWholeVerse, selectedLabelCheckBox)) {
                v.setOnCheckedChangeListener { _, _ ->
                    updateData()
                    updateUI()
                }
            }

            if(data.label.name == "") {
                labelName.requestFocus()
            }
        }
    }

    private fun editColor() {
        closeKeyboard()
        ColorPickerDialog.newBuilder()
            .setColor(data.label.color)
            .show(this@LabelEditActivity)
    }

    private fun addImage(view: AppCompatCheckBox, icon: Int) {
        val imageSpan = ImageSpan(getTintedDrawable(icon))
        val spannableString = SpannableString("${view.text} *")
        val l = view.text.length+1
        spannableString.setSpan(imageSpan, l, l+1, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE)
        view.setText(spannableString, TextView.BufferType.SPANNABLE)
    }
}

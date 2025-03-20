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
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.GridLayout
import android.widget.GridLayout.LayoutParams
import android.widget.GridView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatCheckBox
import androidx.core.content.ContextCompat
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
import net.bible.service.db.exportStudyPads
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

val customIconMap = mapOf(
    "star" to R.drawable.icon_star,
    "book" to R.drawable.icon_book,
    "flag" to R.drawable.icon_flag,
    "user" to R.drawable.icon_user,
    "info" to R.drawable.icon_info,
    "question" to R.drawable.icon_question,
    "lightbulb" to R.drawable.icon_lightbulb,
    "bell" to R.drawable.icon_bell,
    "globe" to R.drawable.icon_globe,
    "clock" to R.drawable.icon_clock,
    "envelope" to R.drawable.icon_envelope,
    "map-marker" to R.drawable.icon_map_marker,
)

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
            R.id.share -> lifecycleScope.launch { exportStudyPads(this@LabelEditActivity, data.label) }
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
        data.label.hideStyle = hideStyle.isChecked
        data.label.hideStyleWholeVerse = hideStyleWholeVerse.isChecked
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
        val isHideStyle = data.label.hideStyle
        val isHideStyleWholeVerse = data.label.hideStyleWholeVerse
        val isMarkerStyle = data.label.markerStyle
        val isMarkerStyleWholeVerse = data.label.markerStyleWholeVerse
        markerStyle.isChecked = isMarkerStyle
        markerStyleWholeVerse.isChecked = isMarkerStyleWholeVerse

        hideStyle.isChecked = isHideStyle
        hideStyleWholeVerse.isChecked = isHideStyleWholeVerse

        underLineStyle.isEnabled = !isHideStyle && !isMarkerStyle
        underLineStyleWholeVerse.isEnabled = !isHideStyleWholeVerse && !isMarkerStyleWholeVerse
        markerStyle.isEnabled = !isHideStyle
        markerStyleWholeVerse.isEnabled = !isHideStyleWholeVerse

        updateColor()
        if (data.label.isSpecialLabel) {
            labelName.isEnabled = false
            thisWorkspaceTitle.visibility = GONE
            favouriteLabelCheckBox.visibility = GONE
            autoAssignCheckBox.visibility = GONE
            primaryAutoAssignCheckBox.visibility = GONE
        }

        if (data.label.isSpeakLabel) {
            customIconSelector.visibility = GONE
        } else {
            customIconSelector.visibility = View.VISIBLE
            val iconName = data.label.customIcon
            val drawableId = customIconMap[iconName] ?: R.drawable.ic_baseline_bookmark_24
            val rawDrawable = ContextCompat.getDrawable(root.context, drawableId)
            val drawable = rawDrawable?.let {
                val mutated = androidx.core.graphics.drawable.DrawableCompat.wrap(it).mutate()
                androidx.core.graphics.drawable.DrawableCompat.setTint(mutated, data.label.color)
                mutated
            }
            customIconSelector.setCompoundDrawablesWithIntrinsicBounds(drawable, null, null, null)
            customIconSelector.text = getString(R.string.choose_icon)
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
            customIconSelector.setOnClickListener { editCustomIcon() }

            for(v in listOf(
                autoAssignCheckBox,
                markerStyle,
                markerStyleWholeVerse,
                selectedLabelCheckBox,
                hideStyle,
                hideStyleWholeVerse,
            )) {
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

    private fun editCustomIcon() {
        val iconNames = listOf(getString(R.string.no_custom_icon)) + customIconMap.keys.toList()
        val gridView = GridView(this).apply {
            // Automatically determine number of columns based on available width.
            numColumns = GridView.AUTO_FIT
            // Set a desired column width (80dp converted to px)
            columnWidth = (80 * resources.displayMetrics.density).toInt()
            stretchMode = GridView.STRETCH_COLUMN_WIDTH

            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            minimumHeight = (resources.displayMetrics.heightPixels * 0.5).toInt()
            // Add padding to the grid layout (convert 16dp to px)
            val paddingPx = (16 * resources.displayMetrics.density).toInt()
            setPadding(paddingPx, paddingPx, paddingPx, paddingPx)
            adapter = object : BaseAdapter() {
                override fun getCount() = iconNames.size
                override fun getItem(position: Int) = iconNames[position]
                override fun getItemId(position: Int) = position.toLong()
                override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                    val button = convertView as? ImageButton ?: ImageButton(this@LabelEditActivity)
                    val name = getItem(position)
                    if (position == 0) {
                        val drawable = ContextCompat.getDrawable(context, R.drawable.icon_disabled)
                        button.setImageDrawable(drawable)
                    } else {
                        val drawableId = customIconMap[name]!!
                        val drawable = ContextCompat.getDrawable(context, drawableId)
                        button.setImageDrawable(drawable)
                    }
                    button.scaleType = ImageView.ScaleType.CENTER_INSIDE
                    button.adjustViewBounds = true
                    button.layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    // Disable individual click handling so GridView click events fire.
                    button.isClickable = false
                    button.isFocusable = false
                    // Remove background color.
                    button.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    return button
                }
            }
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Choose custom icon")
            .setView(gridView)
            .setNegativeButton("Cancel") { d, _ -> d.dismiss() }
            .create()
        gridView.setOnItemClickListener { _, _, position, _ ->
            val selected = iconNames[position]
            data.label.customIcon = if (position == 0) null else selected
            updateUI()
            dialog.dismiss()
        }
        dialog.show()
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

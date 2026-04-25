/*
 * Copyright (c) 2020-2026 Martin Denham, Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.SpannableString
import android.text.TextWatcher
import android.text.style.ImageSpan
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.View.GONE
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatCheckBox
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.lifecycle.lifecycleScope
import com.jaredrummler.android.colorpicker.ColorPickerDialog
import com.jaredrummler.android.colorpicker.ColorPickerDialogListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import net.bible.android.activity.R
import net.bible.android.activity.databinding.BookmarkLabelEditBinding
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.database.WorkspaceEntities
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.android.view.activity.ActivityScope
import net.bible.android.view.activity.base.ActivityBase
import javax.inject.Inject
import net.bible.service.common.CommonUtils
import net.bible.service.common.CommonUtils.getTintedDrawable
import net.bible.service.common.CommonUtils.json
import net.bible.service.common.displayName
import net.bible.service.db.exportStudyPads
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

// Reordered customIconMap with logical categories
val customIconMap = mapOf(
    // Religious / Spiritual
    "book" to R.drawable.icon_book,
    "book-bible" to R.drawable.icon_book_bible,
    "cross" to R.drawable.icon_cross,
    "church" to R.drawable.icon_church,
    "star-of-david" to R.drawable.icon_star_of_david,
    "person-praying" to R.drawable.icon_person_praying,

    // Informational / Symbolic
    "info" to R.drawable.icon_circle_info,
    "question" to R.drawable.icon_circle_question,
    "exclamation" to R.drawable.icon_circle_exclamation,
    "lightbulb" to R.drawable.icon_lightbulb,
    "bell" to R.drawable.icon_bell,
    "flag" to R.drawable.icon_flag,
    "star" to R.drawable.icon_star,
    "tag" to R.drawable.icon_tag,

    // Communication / Social
    "envelope" to R.drawable.icon_envelope,
    "comment" to R.drawable.icon_comment,
    "share-nodes" to R.drawable.icon_share_nodes,
    "link" to R.drawable.icon_link,
    "handshake" to R.drawable.icon_handshake,

    // Time & Location
    "clock" to R.drawable.icon_clock,
    "map-marker" to R.drawable.icon_location_dot,
    "globe" to R.drawable.icon_globe,
    "landmark" to R.drawable.icon_landmark,
    "calendar" to R.drawable.icon_calendar,

    // People & Media / Miscellaneous
    "user" to R.drawable.icon_user,
    "music" to R.drawable.icon_music,
    "microphone" to R.drawable.icon_microphone,
    "key" to R.drawable.icon_key,
    "crown" to R.drawable.icon_crown,
    "heart" to R.drawable.icon_heart,
    "heart-crack" to R.drawable.icon_heart_crack,
    "robot" to R.drawable.icon_robot
)

@ActivityScope
class LabelEditActivity: ActivityBase(), ColorPickerDialogListener {

    @Inject lateinit var bookmarkControl: BookmarkControl

    lateinit var binding: BookmarkLabelEditBinding
    private lateinit var initialDataJson: String
    private var suppressOverrideSpinnerUpdate = false


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
        cancelOrConfirmDiscard()
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
            R.id.save -> saveAndExit()
            R.id.removeLabel -> remove()
            R.id.share -> lifecycleScope.launch { exportStudyPads(this@LabelEditActivity, data.label) }
            android.R.id.home -> cancelOrConfirmDiscard()
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
        var deleteOrphanedBookmarks: Boolean = false,
        val suggestedName: String? = null,
        var workspaceOverride: WorkspaceEntities.WorkspaceLabelOverride? = null,
        var hasWorkspaceContext: Boolean = false,
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

        if (data.hasWorkspaceContext) {
            updateWorkspaceOverrideData()
        }
    }

    private fun updateWorkspaceOverrideData() {
        if (suppressOverrideSpinnerUpdate) return
        val override = data.workspaceOverride ?: return
        val selectedPosition = binding.displayModeSpinner.selectedItemPosition
        // Position 0 = "No override" (null), positions 1..4 = mode 0..3
        val overrideMode = if (selectedPosition == 0) null else selectedPosition - 1
        data.workspaceOverride = override.copy(overrideMode = overrideMode)
    }

    private fun updateUI() = binding.apply {
        Log.i(TAG, "updateUI")
        favouriteLabelCheckBox.isChecked = data.label.favourite
        autoAssignCheckBox.isChecked = data.isAutoAssign
        primaryAutoAssignCheckBox.isChecked = data.isAutoAssignPrimary
        primaryLabelCheckBox.isChecked = data.isThisBookmarkPrimary
        val displayName = if (data.label.name.isEmpty() && data.suggestedName != null) {
            data.suggestedName
        } else {
            data.label.displayName
        }
        labelName.setText(displayName)
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
                val mutated = DrawableCompat.wrap(it).mutate()
                DrawableCompat.setTint(mutated,
                    if (iconName == null) CommonUtils.getResourceColor(R.color.grey_500)
                    else data.label.color
                )
                mutated
            }
            customIconSelector.setCompoundDrawablesWithIntrinsicBounds(drawable, null, null, null)
            customIconSelector.text = getString(R.string.select_custom_icon)
        }
        selectedLabelCheckBox.isChecked = data.isThisBookmarkSelected
        primaryLabelCheckBox.isEnabled = data.isThisBookmarkSelected
        primaryAutoAssignCheckBox.isEnabled = data.isAutoAssign

        thisBookmarkCategory.visibility = if(data.isAssigning) View.VISIBLE else View.GONE
    }

    /**
     * Builds the spinner option list. Position 0 is the explicit "No override" entry;
     * positions 1..4 correspond to MODE_HIGHLIGHT..MODE_HIDDEN.
     */
    private fun buildOverrideSpinnerOptions(): Array<String> = arrayOf(
        getString(R.string.no_override_suffix),
        getString(R.string.display_mode_highlight),
        getString(R.string.display_mode_underline),
        getString(R.string.display_mode_marker),
        getString(R.string.display_mode_hidden),
    )

    private fun overrideModeToSpinnerPosition(overrideMode: Int?): Int =
        if (overrideMode == null) 0 else overrideMode + 1

    private fun setupWorkspaceOverrideUI() {
        if (!data.hasWorkspaceContext || data.label.isSpecialLabel) {
            binding.overrideStyleLabel.visibility = GONE
            binding.displayModeSpinner.visibility = GONE
            return
        }
        binding.overrideStyleLabel.visibility = View.VISIBLE
        binding.displayModeSpinner.visibility = View.VISIBLE

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, buildOverrideSpinnerOptions())
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.displayModeSpinner.adapter = adapter

        val selectedPosition = overrideModeToSpinnerPosition(data.workspaceOverride?.overrideMode)
        suppressOverrideSpinnerUpdate = true
        binding.displayModeSpinner.setSelection(selectedPosition)
        binding.displayModeSpinner.post { suppressOverrideSpinnerUpdate = false }

        binding.displayModeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                updateWorkspaceOverrideData()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    /** Re-applies the spinner selection after a global style change without inferring an override from the stale spinner state. */
    private fun refreshWorkspaceOverrideSpinner() {
        if (!data.hasWorkspaceContext || data.label.isSpecialLabel) return

        suppressOverrideSpinnerUpdate = true
        val selectedPosition = overrideModeToSpinnerPosition(data.workspaceOverride?.overrideMode)
        binding.displayModeSpinner.setSelection(selectedPosition)
        binding.displayModeSpinner.post { suppressOverrideSpinnerUpdate = false }
    }

    private fun saveAndExit() {
        Log.i(TAG, "saveAndExit")

        updateData()

        val resultIntent = Intent()
        resultIntent.putExtra("data", data.toJSON())
        setResult(RESULT_OK, resultIntent)
        finish()
    }

    private fun isDirty(): Boolean {
        updateData()
        return data.toJSON() != initialDataJson
    }

    private fun cancelOrConfirmDiscard() {
        if (isDirty()) {
            AlertDialog.Builder(this)
                .setMessage(R.string.discard_changes_confirmation)
                .setPositiveButton(R.string.yes) { _, _ -> cancelAndExit() }
                .setNegativeButton(R.string.no, null)
                .show()
        } else {
            cancelAndExit()
        }
    }

    private fun cancelAndExit() {
        setResult(RESULT_CANCELED)
        finish()
    }

    enum class RemoveOption {
        CANCEL,
        DELETE_LABEL_ONLY,
        DELETE_LABEL_AND_BOOKMARKS
    }

    private fun remove() {
        Log.i(TAG, "remove")
        updateData()

        lifecycleScope.launch(Dispatchers.Main) {
            val orphanedBookmarks = bookmarkControl.findOrphanedBookmarks(listOf(data.label.id))

            val (dialogMessage, showOrphanedOptions) = if (orphanedBookmarks.isNotEmpty()) {
                val baseMessage = getString(R.string.delete_label_confirmation, data.label.name)
                val orphanedMessage = getString(R.string.confirm_delete_orphaned_bookmarks, orphanedBookmarks.size)
                val combinedMessage = "$baseMessage\n\n$orphanedMessage"
                Pair(combinedMessage, true)
            } else {
                Pair(getString(R.string.delete_label_confirmation, data.label.name), false)
            }

            val result = if (showOrphanedOptions) {
                suspendCoroutine { continuation ->
                    AlertDialog.Builder(this@LabelEditActivity)
                        .setMessage(dialogMessage)
                        .setPositiveButton(R.string.delete_label_and_bookmarks) { _, _ ->
                            continuation.resume(RemoveOption.DELETE_LABEL_AND_BOOKMARKS)
                        }
                        .setNegativeButton(R.string.delete_label_only) { _, _ ->
                            continuation.resume(RemoveOption.DELETE_LABEL_ONLY)
                        }
                        .setNeutralButton(R.string.cancel) { _, _ ->
                            continuation.resume(RemoveOption.CANCEL)
                        }
                        .setCancelable(true)
                        .create().show()
                }
            } else {
                val confirmed = suspendCoroutine { continuation ->
                    AlertDialog.Builder(this@LabelEditActivity)
                        .setMessage(dialogMessage)
                        .setPositiveButton(R.string.yes) { _, _ -> continuation.resume(true) }
                        .setNegativeButton(R.string.no) { _, _ -> continuation.resume(false) }
                        .setCancelable(true)
                        .create().show()
                }
                if (confirmed) RemoveOption.DELETE_LABEL_ONLY else RemoveOption.CANCEL
            }

            when (result) {
                RemoveOption.DELETE_LABEL_AND_BOOKMARKS -> {
                    data.delete = true
                    data.deleteOrphanedBookmarks = true
                    finishWithResult()
                }
                RemoveOption.DELETE_LABEL_ONLY -> {
                    data.delete = true
                    data.deleteOrphanedBookmarks = false
                    finishWithResult()
                }
                RemoveOption.CANCEL -> {}
            }
        }
    }

    private fun finishWithResult() {
        val resultIntent = Intent()
        resultIntent.putExtra("data", data.toJSON())
        setResult(RESULT_OK, resultIntent)
        finish()
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

            setupWorkspaceOverrideUI()

            updateUI()
            suppressOverrideSpinnerUpdate = true
            updateData()
            suppressOverrideSpinnerUpdate = false
            updateUI()

            initialDataJson = data.toJSON()

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
                    suppressOverrideSpinnerUpdate = true
                    updateData()
                    suppressOverrideSpinnerUpdate = false
                    refreshWorkspaceOverrideSpinner()
                    updateUI()
                }
            }
            labelName.addTextChangedListener(object: TextWatcher {
                override fun afterTextChanged(s: android.text.Editable?) {
                    updateData()
                }
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            })

            if(data.label.name == "") {
                labelName.requestFocus()
            }
        }
    }

    private fun editCustomIcon() {
        val iconNames = customIconMap.keys.toList()
        val size = (40 * resources.displayMetrics.density).toInt()
        val gridView = GridView(this).apply {
            numColumns = GridView.AUTO_FIT
            columnWidth = size
            stretchMode = GridView.STRETCH_COLUMN_WIDTH
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            minimumHeight = (resources.displayMetrics.heightPixels * 0.5).toInt()
            val paddingPx = (16 * resources.displayMetrics.density).toInt()
            setPadding(paddingPx, paddingPx, paddingPx, paddingPx)
            adapter = object : BaseAdapter() {
                override fun getCount() = iconNames.size + 1
                override fun getItem(position: Int) = iconNames[position]
                override fun getItemId(position: Int) = position.toLong()
                override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                    val button = convertView as? ImageButton ?: ImageButton(this@LabelEditActivity)
                    if (position == count - 1) {
                        val drawable = ContextCompat.getDrawable(context, R.drawable.icon_disabled)
                        button.setImageDrawable(drawable)
                        button.setBackgroundColor(
                            if (data.label.customIcon == null) {
                                CommonUtils.getResourceColor(R.color.grey_500)
                            } else {
                                Color.TRANSPARENT
                            }
                        )
                    } else {
                        val name = getItem(position)
                        val drawableId = customIconMap[name]!!
                        val drawable = ContextCompat.getDrawable(context, drawableId)
                        button.setImageDrawable(drawable)
                        button.setBackgroundColor(
                            if (name == data.label.customIcon) {
                                CommonUtils.getResourceColor(R.color.grey_500)
                            } else {
                                Color.TRANSPARENT
                            }
                        )
                    }
                    button.scaleType = ImageView.ScaleType.CENTER_INSIDE
                    button.adjustViewBounds = true
                    button.layoutParams = ViewGroup.LayoutParams(size, size)
                    button.isClickable = false
                    button.isFocusable = false
                    return button
                }
            }
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.select_custom_icon)
            .setView(gridView)
            .setNegativeButton(R.string.cancel) { d, _ -> d.dismiss() }
            .create()
        gridView.setOnItemClickListener { _, _, position, _ ->
            data.label.customIcon = if (position == gridView.adapter.count - 1) null else iconNames[position]
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

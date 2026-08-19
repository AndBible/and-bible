/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.ImageButton
import android.widget.ImageView
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog as ComposeAlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import net.bible.android.activity.R
import net.bible.android.view.activity.base.ActivityBase
import net.bible.service.common.CommonUtils
import net.bible.service.db.exportStudyPads
import net.bible.sharedcore.bookmark.DeletePrompt
import net.bible.sharedcore.bookmark.LabelEditController
import net.bible.sharedcore.bookmark.LabelEditResult
import net.bible.sharedcore.bookmark.LabelEditService
import net.bible.sharedui.AbAppTheme
import net.bible.sharedui.bookmark.LabelEditScreen
import net.bible.sharedui.components.AbActionIconSize
import org.koin.android.ext.android.inject

/**
 * Compose host for the single-label editor — the new-path twin of classic [LabelEditActivity].
 * Reads the same `"data"`/[LabelEditActivity.LabelData] intent extra, maps it to a portable
 * [net.bible.sharedcore.bookmark.LabelEditState] via [LabelEditMapper], drives the shared
 * [LabelEditController], and renders [LabelEditScreen]. Writes the exact same `"data"` result
 * extra + `RESULT_OK`/`RESULT_CANCELED` contract as the classic activity so both are
 * interchangeable behind `ScreenLauncher`.
 *
 * Everything that needs Android resources stays host-side: the custom-icon grid picker (Android
 * drawables, classic `GridView`+`AlertDialog`), the discard-changes confirmation on back-press,
 * and the delete-orphaned-bookmarks prompts (rendered as Compose dialogs, driven by
 * [LabelEditController.deletePrompt]).
 */
class LabelEditComposeActivity : ActivityBase() {
    private val service: LabelEditService by inject()

    private lateinit var data: LabelEditActivity.LabelData

    private val controller: LabelEditController by lazy {
        LabelEditController(LabelEditMapper.toState(data), service, lifecycleScope, ::onFinish)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        data = LabelEditActivity.LabelData.fromJSON(intent.getStringExtra("data")!!)

        setContent {
            AbAppTheme {
                    val state by controller.state.collectAsState()
                    val deletePrompt by controller.deletePrompt.collectAsState()

                    LabelEditScreen(
                        state = state,
                        onName = controller::setName,
                        onColor = controller::setColor,
                        onEditIcon = { showCustomIconDialog() },
                        onSelectionStyle = controller::setSelectionStyle,
                        onWholeVerseStyle = controller::setWholeVerseStyle,
                        onToggleFavourite = controller::toggleFavourite,
                        onToggleSelected = controller::toggleThisBookmarkSelected,
                        onTogglePrimary = controller::toggleThisBookmarkPrimary,
                        onToggleAutoAssign = controller::toggleAutoAssign,
                        onToggleAutoAssignPrimary = controller::toggleAutoAssignPrimary,
                        onOverrideMode = controller::setOverrideMode,
                        onUp = { requestUp() },
                        iconSlot = { name -> AndroidLabelIcon(name, state.color) },
                        actions = { LabelEditActions(state.isSpecialLabel) },
                    )

                    deletePrompt?.let { prompt -> DeletePromptDialog(prompt, state.name) }
            }
        }
    }

    @Composable
    private fun RowScope.LabelEditActions(isSpecial: Boolean) {
        IconButton(onClick = { controller.save() }) {
            Icon(
                painter = painterResource(R.drawable.ic_check_24dp),
                contentDescription = getString(R.string.okay),
                modifier = Modifier.size(AbActionIconSize),
            )
        }
        if (!isSpecial) {
            IconButton(onClick = { controller.requestDelete() }) {
                Icon(
                    painter = painterResource(R.drawable.ic_delete_24dp),
                    contentDescription = getString(R.string.delete),
                    modifier = Modifier.size(AbActionIconSize),
                )
            }
        }
        IconButton(onClick = { shareLabel() }) {
            Icon(
                painter = painterResource(R.drawable.ic_baseline_share_24),
                contentDescription = getString(R.string.export),
                modifier = Modifier.size(AbActionIconSize),
            )
        }
    }

    /**
     * [labelName] comes from the LIVE controller state (not `data.label.name`, which is only
     * synced by [LabelEditMapper.applyToData] at save/delete/share time) — otherwise a name typed
     * but not yet saved would show stale in the confirmation message.
     */
    @Composable
    private fun DeletePromptDialog(prompt: DeletePrompt, labelName: String) {
        when (prompt) {
            is DeletePrompt.Orphaned -> ComposeAlertDialog(
                onDismissRequest = { controller.dismissDeletePrompt() },
                title = { Text(getString(R.string.delete_label_confirmation, labelName)) },
                text = { Text(getString(R.string.confirm_delete_orphaned_bookmarks, prompt.count)) },
                confirmButton = {
                    TextButton(onClick = { controller.confirmDelete(true) }) {
                        Text(getString(R.string.delete_label_and_bookmarks))
                    }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = { controller.confirmDelete(false) }) {
                            Text(getString(R.string.delete_label_only))
                        }
                        TextButton(onClick = { controller.dismissDeletePrompt() }) {
                            Text(getString(R.string.cancel))
                        }
                    }
                },
            )
            DeletePrompt.Confirm -> ComposeAlertDialog(
                onDismissRequest = { controller.dismissDeletePrompt() },
                title = { Text(getString(R.string.delete_label_confirmation, labelName)) },
                confirmButton = {
                    TextButton(onClick = { controller.confirmDelete(false) }) { Text(getString(R.string.yes)) }
                },
                dismissButton = {
                    TextButton(onClick = { controller.dismissDeletePrompt() }) { Text(getString(R.string.no)) }
                },
            )
        }
    }

    /** Mirrors classic `exportStudyPads` share action: applies pending (unsaved) edits first. */
    private fun shareLabel() {
        val current = LabelEditMapper.applyToData(data, controller.state.value)
        lifecycleScope.launch { exportStudyPads(this@LabelEditComposeActivity, current.label) }
    }

    private fun onFinish(result: LabelEditResult) {
        when (result) {
            is LabelEditResult.Save -> finishWithData(LabelEditMapper.applyToData(data, result.state))
            is LabelEditResult.Delete -> {
                data.delete = true
                data.deleteOrphanedBookmarks = result.deleteOrphaned
                finishWithData(LabelEditMapper.applyToData(data, result.state))
            }
            LabelEditResult.Cancel -> {
                setResult(RESULT_CANCELED)
                finish()
            }
        }
    }

    private fun finishWithData(updated: LabelEditActivity.LabelData) {
        val resultIntent = Intent()
        resultIntent.putExtra("data", updated.toJSON())
        setResult(RESULT_OK, resultIntent)
        finish()
    }

    private fun requestUp() {
        if (controller.isDirty()) {
            android.app.AlertDialog.Builder(this)
                .setMessage(R.string.discard_changes_confirmation)
                .setPositiveButton(R.string.yes) { _, _ -> controller.cancel() }
                .setNegativeButton(R.string.no, null)
                .show()
        } else {
            controller.cancel()
        }
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        requestUp()
    }

    /** Classic-style `GridView` icon picker over [customIconMap] (+ a trailing "no icon" cell). */
    private fun showCustomIconDialog() {
        val iconNames = customIconMap.keys.toList()
        val size = (40 * resources.displayMetrics.density).toInt()
        val currentIcon = controller.state.value.customIcon
        lateinit var dialog: android.app.AlertDialog
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
                override fun getItem(position: Int): String? = iconNames.getOrNull(position)
                override fun getItemId(position: Int) = position.toLong()
                override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                    val button = convertView as? ImageButton ?: ImageButton(this@LabelEditComposeActivity)
                    if (position == count - 1) {
                        button.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.icon_disabled))
                        button.setBackgroundColor(
                            if (currentIcon == null) CommonUtils.getResourceColor(R.color.grey_500) else Color.TRANSPARENT
                        )
                    } else {
                        val name = iconNames[position]
                        val drawableId = customIconMap[name]!!
                        button.setImageDrawable(ContextCompat.getDrawable(context, drawableId))
                        button.setBackgroundColor(
                            if (name == currentIcon) CommonUtils.getResourceColor(R.color.grey_500) else Color.TRANSPARENT
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
        dialog = android.app.AlertDialog.Builder(this)
            .setTitle(R.string.select_custom_icon)
            .setView(gridView)
            .setNegativeButton(R.string.cancel) { d, _ -> d.dismiss() }
            .create()
        gridView.setOnItemClickListener { _, _, position, _ ->
            controller.setCustomIcon(if (position == gridView.adapter.count - 1) null else iconNames[position])
            dialog.dismiss()
        }
        dialog.show()
    }
}

/**
 * Renders the current custom-icon selection: [customIconMap]`[name]` or the default bookmark
 * drawable, tinted `grey_500` when [name] is null (no custom icon), else [colorArgb] (the label
 * color) — parity with classic `updateUI()`'s `customIconSelector` icon tinting.
 */
@Composable
private fun AndroidLabelIcon(name: String?, colorArgb: Int) {
    val drawableId = customIconMap[name] ?: R.drawable.ic_baseline_bookmark_24
    val tint = if (name == null) colorResource(R.color.grey_500) else ComposeColor(colorArgb)
    Icon(
        painter = painterResource(drawableId),
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(24.dp),
    )
}

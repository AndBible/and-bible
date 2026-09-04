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
import android.os.Bundle
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import net.bible.android.activity.R
import net.bible.android.control.backup.SaveOrShare
import net.bible.android.view.activity.base.ActivityBase
import net.bible.service.db.exportStudyPads
import net.bible.sharedcore.bookmark.DeletePrompt
import net.bible.sharedcore.bookmark.LabelEditController
import net.bible.sharedcore.bookmark.LabelEditResult
import net.bible.sharedcore.bookmark.LabelEditService
import net.bible.sharedui.AbAppTheme
import net.bible.sharedui.bookmark.LabelEditScreen
import net.bible.sharedui.components.AbActionIconSize
import net.bible.sharedui.components.AbActionSheet
import net.bible.sharedui.components.AbActionSheetRow
import net.bible.sharedui.components.AbMenuItem
import net.bible.sharedui.components.AbOverflowMenu
import org.koin.android.ext.android.inject

/**
 * Compose host for the single-label editor — the new-path twin of classic [LabelEditActivity].
 * Reads the same `"data"`/[LabelEditContract.LabelData] intent extra, maps it to a portable
 * [net.bible.sharedcore.bookmark.LabelEditState] via [LabelEditMapper], drives the shared
 * [LabelEditController], and renders [LabelEditScreen]. Writes the exact same `"data"` result
 * extra + `RESULT_OK`/`RESULT_CANCELED` contract as the classic activity so both are
 * interchangeable behind `ScreenLauncher`.
 *
 * Everything that needs Android resources stays host-side: the custom-icon renderer
 * ([AndroidLabelIcon], which every cell of the shared [net.bible.sharedui.bookmark.
 * LabelIdentitySheet]'s icon grid renders through — the fix for the two icons whose vector
 * `fillColor` bypassed tinting under the old raw-drawable `GridView` picker), the discard-changes
 * confirmation on back-press, and the delete-orphaned-bookmarks prompts (rendered as Compose
 * dialogs, driven by [LabelEditController.deletePrompt]).
 */
class LabelEditComposeActivity : ActivityBase() {
    private val service: LabelEditService by inject()

    private lateinit var data: LabelEditContract.LabelData

    private val controller: LabelEditController by lazy {
        LabelEditController(LabelEditMapper.toState(data), service, lifecycleScope, ::onFinish)
    }

    /**
     * Non-null while the "Export to where?" chooser is awaiting an answer — rendered as a Compose
     * dialog in [onCreate]'s `setContent`, so [exportStudyPads] (via [BackupControl.saveOrShare])
     * skips its own platform `AlertDialog` and awaits this one instead. See [askDestination].
     */
    private var destinationRequest: CompletableDeferred<SaveOrShare?>? by mutableStateOf(null)

    private suspend fun askDestination(): SaveOrShare? {
        val deferred = CompletableDeferred<SaveOrShare?>()
        destinationRequest = deferred
        return try { deferred.await() } finally { destinationRequest = null }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        data = LabelEditContract.LabelData.fromJSON(intent.getStringExtra("data")!!)

        setContent {
            AbAppTheme {
                    val state by controller.state.collectAsState()
                    val deletePrompt by controller.deletePrompt.collectAsState()
                    val iconKeys = remember { customIconMap.keys.toList() + null }

                    LabelEditScreen(
                        state = state,
                        onName = controller::setName,
                        onColor = controller::setColor,
                        onCustomIcon = controller::setCustomIcon,
                        onSelectionStyle = controller::setSelectionStyle,
                        onWholeVerseStyle = controller::setWholeVerseStyle,
                        onToggleFavourite = controller::toggleFavourite,
                        onToggleSelected = controller::toggleThisBookmarkSelected,
                        onTogglePrimary = controller::toggleThisBookmarkPrimary,
                        onToggleAutoAssign = controller::toggleAutoAssign,
                        onToggleAutoAssignPrimary = controller::toggleAutoAssignPrimary,
                        onOverrideMode = controller::setOverrideMode,
                        onUp = { requestUp() },
                        iconKeys = iconKeys,
                        iconSlot = { name, tint -> AndroidLabelIcon(name, tint) },
                        actions = { LabelEditActions(state.isSpecialLabel) },
                    )

                    deletePrompt?.let { prompt -> DeletePromptDialog(prompt, state.name) }

                    destinationRequest?.let { req ->
                        // Round 14a G2.9: the export-destination chooser is an ACTION list, not a
                        // question with buttons — three `TextButton`s in two slots was the dialog
                        // shape fighting the content. Completing the deferred clears
                        // `destinationRequest` (see `askDestination`'s `finally`), which is what
                        // closes the sheet — so no row needs to dismiss it, and there is no Cancel
                        // row: `onDismiss` (swipe / scrim / back / ✕) is the "chose nothing" path,
                        // completing with null exactly as the dialog's `onDismissRequest` did.
                        AbActionSheet(
                            open = true,
                            title = getString(R.string.export_destination_title),
                            message = getString(R.string.export_destination_message),
                            onDismiss = { req.complete(null) },
                        ) {
                            AbActionSheetRow(
                                label = getString(R.string.share),
                                onClick = { req.complete(SaveOrShare.SHARE) },
                                icon = { Icon(painterResource(R.drawable.ic_baseline_share_24), contentDescription = null) },
                            )
                            AbActionSheetRow(
                                label = getString(R.string.backup_phone_storage),
                                onClick = { req.complete(SaveOrShare.SAVE) },
                                icon = { Icon(painterResource(R.drawable.ic_save_24dp), contentDescription = null) },
                            )
                        }
                    }
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
        AbOverflowMenu(contentDescription = null) { close ->
            AbMenuItem(
                text = getString(R.string.export),
                onClick = { close(); shareLabel() },
                icon = { Icon(painterResource(R.drawable.ic_baseline_share_24), contentDescription = null) },
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
        lifecycleScope.launch {
            exportStudyPads(this@LabelEditComposeActivity, current.label, chooseDestination = ::askDestination)
        }
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

    private fun finishWithData(updated: LabelEditContract.LabelData) {
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
}

/**
 * Renders the current custom-icon selection: [customIconMap]`[name]` or the default bookmark
 * drawable, tinted with the caller-supplied [tint] rather than deriving one here.
 *
 * [tint] used to be derived internally from the label's own colour (grey when [name] was null),
 * which is exactly what made the identity-row avatar's glyph disappear onto its same-coloured
 * disc (round-9a whole-branch review I1): a single internal rule cannot serve both a disc
 * filled with the label's colour (needs a contrast tint) and a neutral background (can use the
 * label's colour safely). Every call site in [net.bible.sharedui.bookmark.LabelEditScreen] /
 * [net.bible.sharedui.bookmark.LabelIdentitySheetContent] now picks its own tint instead.
 */
@Composable
private fun AndroidLabelIcon(name: String?, tint: Color) {
    val drawableId = customIconMap[name] ?: R.drawable.ic_baseline_bookmark_24
    Icon(
        painter = painterResource(drawableId),
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(24.dp),
    )
}

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
import android.text.SpannableString
import android.text.TextUtils.concat
import android.text.method.LinkMovementMethod
import android.text.style.ImageSpan
import android.util.Log
import android.widget.TextView
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.bible.android.activity.R
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.control.bookmark.LabelAddedOrUpdatedEvent
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.page.window.WindowControl
import net.bible.android.database.IdType
import net.bible.android.database.WorkspaceEntities
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.android.view.Screen
import net.bible.android.view.ScreenLauncher
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.base.Dialogs
import net.bible.android.view.activity.installzip.InstallZip
import net.bible.service.common.CommonUtils
import net.bible.service.common.CommonUtils.getTintedDrawable
import net.bible.service.common.displayName
import net.bible.service.common.htmlToSpan
import net.bible.service.common.labelsAndBookmarksPlaylist
import net.bible.service.db.DatabaseContainer
import net.bible.service.db.exportStudyPads
import net.bible.service.device.ScreenSettings
import net.bible.service.download.FakeBookFactory
import net.bible.service.sword.StudyPadKey
import net.bible.sharedcore.bookmark.ManageLabelsController
import net.bible.sharedcore.bookmark.ManageLabelsRow
import net.bible.sharedcore.bookmark.ManageLabelsService
import net.bible.sharedcore.bookmark.SearchMode
import net.bible.sharedui.ProvideAppLocals
import net.bible.sharedui.bookmark.ManageLabelsScreen
import net.bible.sharedui.components.AbMultiSelectDialog
import net.bible.sharedui.components.AbOverflowMenu
import net.bible.sharedui.theme.AbTheme
import org.koin.android.ext.android.inject

private const val TAG = "ManageLabelsCompose"

/**
 * Compose host for the label-management / selection / StudyPad-picker screen — the new-path twin
 * of classic [ManageLabels]. Reads the same `"data"`/[ManageLabels.ManageLabelsData] intent extra,
 * maps it to the shared [ManageLabelsController]'s seeds via [ManageLabelsMapper], and writes the
 * exact same `"data"` result extra + `RESULT_OK` contract, so both are interchangeable behind
 * [ScreenLauncher] for the 7 existing callers.
 *
 * Room/JSword types stay host-side: [labelsById] is this host's own authoritative
 * `BookmarkEntities.Label` map (mirrors classic `allLabels`) — the shared [LabelItem][net.bible.sharedcore.bookmark.LabelItem]
 * only carries display fields, not the style flags (`markerStyle`, `underlineStyle`, ...) that
 * [BookmarkControl.insertOrUpdateLabel] needs at [saveAndExit] time. Label-edit round-trips
 * ([onEditLabel]) and workspace-override persistence (`WorkspaceDao`) also live here, mirroring
 * classic `ManageLabels.editLabel`'s result handling (ManageLabels.kt:552-659) and `saveAndExit`
 * (ManageLabels.kt:673-723) field-for-field.
 */
class ManageLabelsComposeActivity : ActivityBase() {
    private val service: ManageLabelsService by inject()
    private val bookmarkControl: BookmarkControl by inject()
    private val windowControl: WindowControl by inject()

    private lateinit var data: ManageLabels.ManageLabelsData

    /** Host-side authoritative Label objects (style flags [net.bible.sharedcore.bookmark.LabelItem]
     *  doesn't carry), mirroring classic `allLabels`. Seeded broadly (includes the Unlabeled
     *  special label) so any label touched via [onEditLabel] can be looked up/saved later. */
    private val labelsById: MutableMap<String, BookmarkEntities.Label> by lazy {
        bookmarkControl.assignableLabels.associateByTo(mutableMapOf()) { it.id.toString() }
    }

    private val controller: ManageLabelsController by lazy {
        val highlightId = (windowControl.activeWindowPageManager.currentPage.key as? StudyPadKey)
            ?.takeIf { data.mode == ManageLabels.Mode.STUDYPAD }
            ?.label?.id?.toString()
        ManageLabelsController(
            mode = ManageLabelsMapper.toMode(data.mode),
            service = service,
            scope = lifecycleScope,
            initialSelected = ManageLabelsMapper.seedSelected(data),
            initialAutoAssign = ManageLabelsMapper.seedAutoAssign(data),
            initialAutoAssignPrimary = ManageLabelsMapper.seedAutoAssignPrimary(data),
            initialBookmarkPrimary = ManageLabelsMapper.seedBookmarkPrimary(data),
            highlightLabelId = highlightId,
            onEditLabel = ::onEditLabel,
            onSelectStudyPad = ::onSelectStudyPad,
            onSave = ::saveAndExit,
            onReset = ::reset,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        data = ManageLabels.ManageLabelsData.fromJSON(intent.getStringExtra("data")!!)

        // Restore the persisted STUDYPAD content-search mode (classic ManageLabels.kt:133-138
        // loadFilteringSettings, `labels_list_search_mode` — STUDYPAD only). Harmless to seed via
        // setSearchMode before the first collection: with no search text yet it's a no-op rebuild().
        if (data.mode == ManageLabels.Mode.STUDYPAD) {
            val modeOrdinal = CommonUtils.settings.getInt("labels_list_search_mode", SearchMode.NAME_START.ordinal)
            controller.setSearchMode(SearchMode.entries.getOrElse(modeOrdinal) { SearchMode.NAME_START })
        }

        setContent {
            ProvideAppLocals {
                AbTheme(
                    darkTheme = ScreenSettings.nightMode,
                    colorMode = CommonUtils.settings.displayColorMode,
                    disableAnimations = CommonUtils.settings.disableAnimations,
                ) {
                    val rows by controller.rows.collectAsState()
                    val searchText by controller.searchText.collectAsState()
                    val searchMode by controller.searchMode.collectAsState()
                    var showExportDialog by remember { mutableStateOf(false) }

                    ManageLabelsScreen(
                        title = getString(data.titleId),
                        rows = rows,
                        mode = controller.mode,
                        searchText = searchText,
                        searchMode = searchMode,
                        onSearch = controller::setSearch,
                        onSetSearchMode = controller::setSearchMode,
                        onRowClick = { id ->
                            if (data.mode == ManageLabels.Mode.STUDYPAD) {
                                // A content-search hit carries its own firstMatchEntryId; a plain
                                // name-filtered Item row has none (navigates to the StudyPad start).
                                val entryId = (rows.find { it is ManageLabelsRow.SearchResult && it.labelId == id }
                                    as? ManageLabelsRow.SearchResult)?.firstMatchEntryId
                                controller.selectStudyPad(id, entryId)
                            } else {
                                controller.editLabel(id)
                            }
                        },
                        onRowLongClick = { id -> controller.editLabel(id) },
                        onToggleChecked = controller::toggleChecked,
                        onToggleFavourite = controller::toggleFavourite,
                        onSetPrimary = controller::setPrimary,
                        onToggleAutoAssign = controller::toggleAutoAssign,
                        onUp = { saveAndExit() },
                        iconSlot = { customIcon, colorArgb -> ManageLabelIcon(customIcon, colorArgb) },
                        actions = { ManageLabelsActions(onExportStudyPads = { showExportDialog = true }, onImportStudyPads = ::importStudyPads) },
                    )

                    // Mirrors classic ManageLabels.kt:394-406 (export_studypads menu handler): a
                    // multiselect over every assignable label, then exportStudyPads for the chosen ones.
                    if (showExportDialog) {
                        val exportableLabels = remember(showExportDialog) { bookmarkControl.assignableLabels }
                        AbMultiSelectDialog(
                            title = getString(R.string.export_something, getString(R.string.studypads)),
                            options = exportableLabels,
                            selectedIds = emptyList(),
                            idOf = { it.id.toString() },
                            labelOf = { it.displayName },
                            confirmText = getString(R.string.okay),
                            dismissText = getString(R.string.cancel),
                            onConfirm = { ids ->
                                showExportDialog = false
                                val selected = exportableLabels.filter { ids.contains(it.id.toString()) }
                                if (selected.isNotEmpty()) {
                                    lifecycleScope.launch(Dispatchers.Main) {
                                        exportStudyPads(this@ManageLabelsComposeActivity, *selected.toTypedArray())
                                    }
                                }
                            },
                            onDismiss = { showExportDialog = false },
                            selectAllText = getString(R.string.select_all),
                            selectNoneText = getString(R.string.select_none),
                        )
                    }
                }
            }
        }
    }

    // --- StudyPad import (mirrors classic ManageLabels.kt:407-413 import_studypads menu handler) ---

    private fun importStudyPads() {
        lifecycleScope.launch(Dispatchers.Main) {
            awaitIntent(Intent(this@ManageLabelsComposeActivity, InstallZip::class.java))
            controller.refresh()
        }
    }

    /** Classic `ManageLabels.onBackPressed()` (ManageLabels.kt:148-150) always saves & exits — no
     *  discard confirmation (unlike [LabelEditComposeActivity]'s dirty-check). */
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        saveAndExit()
    }

    @Composable
    private fun RowScope.ManageLabelsActions(onExportStudyPads: () -> Unit, onImportStudyPads: () -> Unit) {
        AbOverflowMenu(contentDescription = null) { close ->
            DropdownMenuItem(text = { Text(getString(R.string.new_item)) }, onClick = { close(); controller.newLabel() })
            DropdownMenuItem(text = { Text(getString(R.string.help)) }, onClick = { close(); help() })
            if (controller.mode.hasReOrderButton) {
                DropdownMenuItem(text = { Text(getString(R.string.reorder)) }, onClick = { close(); controller.reOrder() })
            }
            if (controller.mode.hasResetButton) {
                DropdownMenuItem(text = { Text(getString(R.string.reset_generic)) }, onClick = { close(); controller.reset() })
            }
            // Export/import StudyPads: visible in ALL modes (classic ManageLabels.kt:379-386
            // onCreateOptionsMenu parity — only resetButton/reOrder are mode-conditional there).
            DropdownMenuItem(
                text = { Text(getString(R.string.export_something, getString(R.string.studypads))) },
                onClick = { close(); onExportStudyPads() },
            )
            DropdownMenuItem(
                text = { Text(getString(R.string.import_items, getString(R.string.studypads))) },
                onClick = { close(); onImportStudyPads() },
            )
        }
    }

    // --- editLabel round-trip (mirrors classic ManageLabels.editLabel, ManageLabels.kt:552-659) ---

    private fun onEditLabel(id: String?) {
        val isNew = id == null
        val label: BookmarkEntities.Label = if (id != null) {
            labelsById[id] ?: bookmarkControl.labelById(IdType(id)) ?: return
        } else {
            BookmarkEntities.Label(new = true).apply { color = service.randomColorArgb() }
        }
        val suggestedName = if (isNew) controller.searchText.value.takeIf { it.isNotBlank() } else null

        val workspaceId = windowControl.windowRepository.id
        val workspaceDao = DatabaseContainer.instance.workspaceDb.workspaceDao()
        val existingOverrides = if (!isNew) workspaceDao.labelOverrides(workspaceId) else emptyList()
        val existingOverride = existingOverrides.find { it.labelId == label.id }
        val workspaceOverride = existingOverride ?: WorkspaceEntities.WorkspaceLabelOverride(
            workspaceId = workspaceId,
            labelId = label.id,
        )

        val labelData = LabelEditActivity.LabelData(
            isAssigning = data.mode == ManageLabels.Mode.ASSIGN,
            label = label,
            isAutoAssign = controller.resultAutoAssign().contains(label.id.toString()),
            isAutoAssignPrimary = controller.resultAutoAssignPrimary() == label.id.toString(),
            isThisBookmarkPrimary = controller.resultBookmarkPrimary() == label.id.toString(),
            isThisBookmarkSelected = controller.resultSelected().contains(label.id.toString()),
            suggestedName = suggestedName,
            workspaceOverride = workspaceOverride,
            hasWorkspaceContext = true,
        )
        if (isNew) {
            when (data.mode) {
                ManageLabels.Mode.ASSIGN -> {
                    labelData.isThisBookmarkSelected = true
                    labelData.isThisBookmarkPrimary = true
                }
                ManageLabels.Mode.WORKSPACE -> {
                    labelData.isAutoAssignPrimary = true
                    labelData.isAutoAssign = true
                }
                else -> {}
            }
        }

        val intent = ScreenLauncher.intentFor(this, Screen.LabelEdit)
        intent.putExtra("data", labelData.toJSON())

        lifecycleScope.launch(Dispatchers.Main) {
            Log.i(TAG, "editLabel waiting for results")
            val result = awaitIntent(intent)

            if (result.resultCode == RESULT_CANCELED) {
                Log.i(TAG, "editLabel result CANCELLED")
                return@launch
            }

            val newLabelData = LabelEditActivity.LabelData.fromJSON(result.data?.getStringExtra("data")!!)

            if (newLabelData.label.name.isEmpty() && isNew) {
                Log.i(TAG, "editLabel name not specified or is new")
                return@launch
            }

            if (newLabelData.delete) {
                Log.i(TAG, "editLabel delete specified")
                labelsById.remove(label.id.toString())
                controller.applyLabelDeleted(label.id.toString(), newLabelData.deleteOrphanedBookmarks)
            } else {
                Log.i(TAG, "editLabel delete not specified")
                val updatedLabel = newLabelData.label
                labelsById[updatedLabel.id.toString()] = updatedLabel

                // Classic applies all four unconditionally (ManageLabels.kt:614-634) — isThisBookmarkSelected
                // is the sole one actually mode-gated there (`if(data.mode == Mode.ASSIGN)`), but its
                // checkbox is hidden on every non-ASSIGN LabelEdit screen (thisBookmarkCategory.visibility,
                // LabelEditActivity.kt:284), so the round-tripped value is unchanged from the seed there —
                // passing it unconditionally is behaviorally identical and lets applyLabelChanged apply all
                // four the same way classic does.
                controller.applyLabelChanged(
                    item = updatedLabel.toLabelItem(),
                    selectedFlag = newLabelData.isThisBookmarkSelected,
                    autoAssignFlag = newLabelData.isAutoAssign,
                    bookmarkPrimaryFlag = newLabelData.isThisBookmarkPrimary,
                    autoAssignPrimaryFlag = newLabelData.isAutoAssignPrimary,
                )

                // Save workspace override (classic ManageLabels.kt:637-649).
                val returnedOverride = newLabelData.workspaceOverride
                if (returnedOverride != null) {
                    val dao = DatabaseContainer.instance.workspaceDb.workspaceDao()
                    if (returnedOverride.hasOverride) {
                        dao.insertOrUpdateLabelOverride(returnedOverride)
                    } else {
                        dao.deleteLabelOverride(returnedOverride.workspaceId, returnedOverride.labelId)
                    }
                    ABEventBus.post(LabelAddedOrUpdatedEvent(updatedLabel))
                    controller.refresh() // re-derive the override (Tune icon) indicator immediately
                }
            }
        }
    }

    // --- StudyPad selection (mirrors classic ManageLabels.studyPadSelected, ManageLabels.kt:501-512,
    // and selectStudyPadLabel, ManageLabels.kt:664-671) ---

    private fun onSelectStudyPad(id: String, firstMatchEntryId: String?) {
        val label = labelsById[id] ?: bookmarkControl.labelById(IdType(id)) ?: return
        try {
            windowControl.activeWindowPageManager.setCurrentDocumentAndKey(
                FakeBookFactory.journalDocument,
                StudyPadKey(label, entryId = firstMatchEntryId?.let { IdType(it) }),
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error on attempt to show journal", e)
            Dialogs.showErrorMsg(R.string.error_occurred, e)
        }
        saveAndExit()
    }

    // --- save/exit (mirrors classic ManageLabels.saveAndExit, ManageLabels.kt:673-723) ---

    private fun saveAndExit() {
        // Persist the STUDYPAD content-search mode (classic ManageLabels.kt:141-145 saveFilteringSettings,
        // `labels_list_search_mode` — STUDYPAD only).
        if (data.mode == ManageLabels.Mode.STUDYPAD) {
            CommonUtils.settings.setInt("labels_list_search_mode", controller.searchMode.value.ordinal)
        }

        val deletedIds = controller.resultDeleted()
        val orphanedIds = controller.resultDeletedWithOrphaned()
        val withoutOrphaned = deletedIds.filterNot { orphanedIds.contains(it) }.map { IdType(it) }
        val withOrphaned = orphanedIds.map { IdType(it) }
        if (withoutOrphaned.isNotEmpty()) {
            bookmarkControl.deleteLabels(withoutOrphaned, deleteOrphanedBookmarks = false)
        }
        if (withOrphaned.isNotEmpty()) {
            bookmarkControl.deleteLabels(withOrphaned, deleteOrphanedBookmarks = true)
        }

        val changedIds = controller.resultChanged()
        val toSave = changedIds.filterNot { deletedIds.contains(it) }.mapNotNull { labelsById[it] }

        // The list's quick favourite-toggle (controller.toggleFavourite) only flips the controller's
        // own LabelItem copy — labelsById[id] (built above) is the pre-toggle Label, so re-apply the
        // controller's current favourite onto it here before persisting (fall back to the existing
        // value when the controller has no entry for that id). Classic persists this for free since
        // its adapter mutates the same Label instance later saved from `allLabels`
        // (ManageLabelItemAdapter.kt:179-183).
        val currentFavourites = controller.currentLabelItems().associate { it.id to it.favourite }
        toSave.forEach { label -> currentFavourites[label.id.toString()]?.let { label.favourite = it } }

        val newLabels = toSave.filter { it.new }
        val existingLabels = toSave.filter { !it.new }

        // New-label id remap (classic ManageLabels.kt:695-711): a label created via editLabel only
        // gets a real, DB-assigned id here — every set/primary the controller tracked under its
        // temporary id must follow the label to its final id.
        val idRemap = mutableMapOf<String, String>()
        for (label in newLabels) {
            val oldId = label.id.toString()
            val saved = bookmarkControl.insertOrUpdateLabel(label)
            label.id = saved.id
            label.new = false
            idRemap[oldId] = saved.id.toString()
        }
        for (label in existingLabels) {
            bookmarkControl.insertOrUpdateLabel(label)
        }

        fun remapSet(ids: Set<String>) = ids.map { idRemap[it] ?: it }.toSet()
        fun remapId(id: String?) = id?.let { idRemap[it] ?: it }

        ManageLabelsMapper.applyResult(
            data = data,
            selected = remapSet(controller.resultSelected()),
            autoAssign = remapSet(controller.resultAutoAssign()),
            changed = remapSet(controller.resultChanged()),
            deleted = controller.resultDeleted(),
            deletedWithOrphaned = controller.resultDeletedWithOrphaned(),
            autoAssignPrimary = remapId(controller.resultAutoAssignPrimary()),
            bookmarkPrimary = remapId(controller.resultBookmarkPrimary()),
        )

        setResult(RESULT_OK, Intent().apply { putExtra("data", this@ManageLabelsComposeActivity.data.toJSON()) })
        finish()
    }

    // --- reset (mirrors classic ManageLabels.reset, ManageLabels.kt:745-763) ---

    private fun reset() {
        lifecycleScope.launch(Dispatchers.Main) {
            val msgId = when (data.mode) {
                ManageLabels.Mode.WORKSPACE -> R.string.reset_workspace_labels
                ManageLabels.Mode.HIDELABELS -> R.string.reset_hide_labels
                else -> throw RuntimeException("Illegal value")
            }
            if (askConfirmation(getString(msgId))) {
                ManageLabelsMapper.applyReset(data)
                setResult(RESULT_OK, Intent().apply { putExtra("data", this@ManageLabelsComposeActivity.data.toJSON()) })
                finish()
            }
        }
    }

    private suspend fun askConfirmation(message: String): Boolean = suspendCoroutine { cont ->
        android.app.AlertDialog.Builder(this)
            .setMessage(message)
            .setCancelable(true)
            .setOnCancelListener { cont.resume(false) }
            .setPositiveButton(R.string.yes) { _, _ -> cont.resume(true) }
            .setNegativeButton(R.string.cancel) { _, _ -> cont.resume(false) }
            .show()
    }

    // --- help (mirrors classic ManageLabels.help, ManageLabels.kt:424-499) ---

    private fun help() {
        when (data.mode) {
            ManageLabels.Mode.STUDYPAD -> CommonUtils.showHelp(this, listOf(R.string.studypads))
            ManageLabels.Mode.ASSIGN -> help(HelpMode.ASSIGN)
            ManageLabels.Mode.WORKSPACE -> help(HelpMode.WORKSPACE)
            ManageLabels.Mode.HIDELABELS -> help(HelpMode.HIDE)
        }
    }

    private enum class HelpMode { WORKSPACE, ASSIGN, HIDE }

    private fun getIconString(id: Int, iconId: Int): SpannableString {
        val s = getString(id, "__ICON__")
        val start = s.indexOf("__ICON__")
        val length = 8
        val icon = ImageSpan(getTintedDrawable(iconId))
        val span = SpannableString(s)
        span.setSpan(icon, start, start + length, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE)
        return span
    }

    private fun help(helpMode: HelpMode) {
        val length = 9

        val videoLink = "<i><a href=\"$labelsAndBookmarksPlaylist\">${getString(R.string.watch_tutorial_video)}</a></i><br><br>"
        val v = htmlToSpan(videoLink)

        val h1 = when (helpMode) {
            HelpMode.WORKSPACE -> getString(R.string.auto_assing_labels_help1)
            HelpMode.ASSIGN -> getString(R.string.assing_labels_help1)
            HelpMode.HIDE -> getString(R.string.bookmark_settings_hide_labels_summary)
        }

        val h11 = "\n\n" + getString(
            R.string.setting_scope,
            getString(if (data.isWindow) R.string.setting_scope_window else R.string.setting_scope_workspace),
        )

        val h2 = concat("\n\n", getIconString(R.string.assing_labels_help2, R.drawable.ic_baseline_bookmark_24))
        val text = getString(R.string.assing_labels_help3, "__ICON2__ __ICON3__")

        val start2 = text.indexOf("__ICON2__")
        val start3 = text.indexOf("__ICON3__")
        val h3 = concat("\n\n", SpannableString(text).apply {
            val icon2 = ImageSpan(getTintedDrawable(R.drawable.ic_label_24dp))
            val icon3 = ImageSpan(getTintedDrawable(R.drawable.ic_label_circle))
            setSpan(icon2, start2, start2 + length, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(icon3, start3, start3 + length, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE)
        })

        val h4 = concat("\n\n", getIconString(R.string.assing_labels_help4, R.drawable.ic_baseline_favorite_24))
        val h5 = concat("\n\n", getIconString(R.string.assing_labels_help5, R.drawable.ic_baseline_refresh_24))
        val span = concat(
            v,
            h1,
            if (listOf(HelpMode.HIDE, HelpMode.WORKSPACE).contains(helpMode)) h11 else "",
            *if (helpMode != HelpMode.HIDE) arrayOf(h2, h3, h4) else arrayOf(""),
            h5,
        )

        val title = getString(
            when (helpMode) {
                HelpMode.ASSIGN -> R.string.assign_labels
                HelpMode.WORKSPACE -> R.string.labels
                HelpMode.HIDE -> R.string.bookmark_settings_hide_labels_title
            },
        )

        val d = android.app.AlertDialog.Builder(this)
            .setPositiveButton(R.string.okay, null)
            .setTitle(title)
            .setIcon(R.drawable.ic_logo)
            .setMessage(span)
            .create()

        d.show()
        d.findViewById<TextView>(android.R.id.message)!!.movementMethod = LinkMovementMethod.getInstance()
    }
}

/**
 * Renders the current custom-icon selection: [customIconMap]`[name]` or the default bookmark
 * drawable, tinted `grey_500` when [name] is null (no custom icon), else [colorArgb] (the label
 * color) — parity with classic `ManageLabelItemAdapter`'s custom-icon tinting.
 */
@Composable
private fun ManageLabelIcon(name: String?, colorArgb: Int) {
    val drawableId = customIconMap[name] ?: R.drawable.ic_baseline_bookmark_24
    val tint = if (name == null) colorResource(R.color.grey_500) else ComposeColor(colorArgb)
    Icon(
        painter = painterResource(drawableId),
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(24.dp),
    )
}

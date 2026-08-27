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
import android.util.Log
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
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
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.bible.android.activity.R
import net.bible.android.control.backup.SaveOrShare
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
import net.bible.service.common.CommonUtils
import net.bible.service.common.displayName
import net.bible.service.common.labelsAndBookmarksPlaylist
import net.bible.service.db.DatabaseContainer
import net.bible.service.db.exportStudyPads
import net.bible.service.download.FakeBookFactory
import net.bible.service.sword.StudyPadKey
import net.bible.sharedcore.bookmark.ManageLabelsController
import net.bible.sharedcore.bookmark.ManageLabelsMode
import net.bible.sharedcore.bookmark.ManageLabelsRow
import net.bible.sharedcore.bookmark.ManageLabelsService
import net.bible.sharedcore.bookmark.SearchMode
import net.bible.sharedui.AbAppTheme
import net.bible.sharedui.bookmark.ManageLabelsHelpDialog
import net.bible.sharedui.bookmark.ManageLabelsScreen
import net.bible.sharedui.components.AbActionIconSize
import net.bible.sharedui.components.AbActionSheet
import net.bible.sharedui.components.AbActionSheetRow
import net.bible.sharedui.components.AbMenuItem
import net.bible.sharedui.components.AbMultiSelectDialog
import net.bible.sharedui.components.AbOverflowMenu
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
 * only carries display fields, not the display styles (`displayStyle`, `displayStyleWholeVerse`) that
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

    /**
     * Same "Export to where?" chooser seam as `LabelEditComposeActivity.destinationRequest` —
     * duplicated rather than shared, per the task brief: a third caller would justify factoring
     * this out, two doesn't.
     */
    private var destinationRequest: CompletableDeferred<SaveOrShare?>? by mutableStateOf(null)

    private suspend fun askDestination(): SaveOrShare? {
        val deferred = CompletableDeferred<SaveOrShare?>()
        destinationRequest = deferred
        return try { deferred.await() } finally { destinationRequest = null }
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
            AbAppTheme {
                    val rows by controller.rows.collectAsState()
                    val searchText by controller.searchText.collectAsState()
                    val searchMode by controller.searchMode.collectAsState()
                    val searchModeActive by controller.searchModeActive.collectAsState()
                    val styleTagsVisible by controller.styleTagsVisible.collectAsState()
                    var showExportDialog by remember { mutableStateOf(false) }
                    var showHelp by remember { mutableStateOf(false) }

                    ManageLabelsScreen(
                        title = getString(data.titleId),
                        rows = rows,
                        mode = controller.mode,
                        styleTagsVisible = styleTagsVisible,
                        searchText = searchText,
                        searchMode = searchMode,
                        onSearch = controller::setSearch,
                        onSetSearchMode = controller::setSearchMode,
                        searchModeActive = searchModeActive,
                        onCloseSearch = controller::closeSearch,
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
                        iconSlot = { customIcon, tint ->
                            ManageLabelIcon(customIcon, tint, controller.mode == ManageLabelsMode.STUDYPAD)
                        },
                        actions = {
                            ManageLabelsActions(
                                onOpenSearch = controller::openSearch,
                                onExportStudyPads = { showExportDialog = true },
                                onImportStudyPads = ::importStudyPads,
                                onOpenHelp = { showHelp = true },
                            )
                        },
                        // The New icon lives here too, not just in `actions`: a search that finds
                        // nothing has no other reachable "create it with this name" action once the
                        // normal bar's ⊕ is suppressed for search mode, and onEditLabel's
                        // `suggestedName` (below) is seeded from the live query specifically to serve
                        // this one-tap path.
                        searchActions = { NewLabelIcon(onClick = controller::newLabel) },
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
                                        exportStudyPads(
                                            this@ManageLabelsComposeActivity,
                                            *selected.toTypedArray(),
                                            chooseDestination = ::askDestination,
                                        )
                                    }
                                }
                            },
                            onDismiss = { showExportDialog = false },
                            selectAllText = getString(R.string.select_all),
                            selectNoneText = getString(R.string.select_none),
                        )
                    }

                    if (showHelp) {
                        ManageLabelsHelpDialog(
                            mode = controller.mode,
                            title = getString(data.titleId),
                            // Classic showed this only for WORKSPACE and HIDE (ManageLabels.kt's
                            // h11), because those are the two scoped settings.
                            scopeSentence = if (controller.mode == ManageLabelsMode.WORKSPACE ||
                                controller.mode == ManageLabelsMode.HIDELABELS
                            ) {
                                getString(
                                    R.string.setting_scope,
                                    getString(if (data.isWindow) R.string.setting_scope_window else R.string.setting_scope_workspace),
                                )
                            } else null,
                            readMoreUrl = labelsAndBookmarksPlaylist,
                            onDismiss = { showHelp = false },
                        )
                    }

                    destinationRequest?.let { req ->
                        // Round 14a G2.10: the export-destination chooser is an ACTION list, not a
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

    // --- StudyPad import (mirrors classic ManageLabels.kt:407-413 import_studypads menu handler) ---

    private fun importStudyPads() {
        lifecycleScope.launch(Dispatchers.Main) {
            awaitIntent(ScreenLauncher.intentFor(this@ManageLabelsComposeActivity, Screen.InstallZip))
            controller.refresh()
        }
    }

    /** Search mode is a bar state, not a screen, so hardware back must leave it rather than leave
     *  the screen — same routing as WorkspaceSelectorComposeActivity.kt:126-130. Only once search
     *  is closed does back mean "done", which for this screen is still an unconditional
     *  saveAndExit() (no discard confirmation, unlike LabelEditComposeActivity's dirty check). */
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        if (controller.searchModeActive.value) {
            controller.closeSearch()
            return
        }
        saveAndExit()
    }

    /**
     * The New (⊕) icon, factored out so the identical button can be placed in both the normal
     * bar's [ManageLabelsActions] AND the search bar's `searchActions` slot (wired at the
     * [ManageLabelsScreen] call site) — see the comment there for why the search bar needs its own
     * copy rather than relying on the normal bar's.
     */
    @Composable
    private fun RowScope.NewLabelIcon(onClick: () -> Unit) {
        IconButton(onClick = onClick) {
            Icon(
                painter = painterResource(R.drawable.ic_add_circle_outline_white_24dp),
                contentDescription = getString(R.string.new_item),
                modifier = Modifier.size(AbActionIconSize),
            )
        }
    }

    @Composable
    private fun RowScope.ManageLabelsActions(
        onOpenSearch: () -> Unit,
        onExportStudyPads: () -> Unit,
        onImportStudyPads: () -> Unit,
        onOpenHelp: () -> Unit,
    ) {
        // Search + New as visible icons, matching WorkspaceSelectorScreen.kt:136-142 so the two
        // list screens read as one family. Everything mode-gated or rare stays in the overflow.
        // Drawables, not Material ImageVectors: `material-icons-extended` is deliberately
        // testImplementation-only in :app (app/build.gradle.kts:507, "golden-test only"), and no
        // :app/src/main file imports Material icons — the host renders every bar icon through
        // painterResource, as LabelEditComposeActivity.kt:121-135 does.
        IconButton(onClick = onOpenSearch) {
            Icon(
                painter = painterResource(R.drawable.ic_search_24dp),
                contentDescription = getString(R.string.search),
                modifier = Modifier.size(AbActionIconSize),
            )
        }
        NewLabelIcon(onClick = { controller.newLabel() })
        // Read locally rather than threaded in as a parameter: this composable is defined outside
        // setContent's scope (it's a plain member function, not a lambda nested in it), so the
        // `styleTagsVisible by collectAsState()` collected there isn't in scope here.
        val styleTagsVisible by controller.styleTagsVisible.collectAsState()
        AbOverflowMenu(contentDescription = null) { close ->
            AbMenuItem(
                text = getString(R.string.help),
                onClick = { close(); onOpenHelp() },
                icon = { Icon(painterResource(R.drawable.ic_help_white_24dp), contentDescription = null) },
            )
            if (controller.mode.hasReOrderButton) {
                AbMenuItem(
                    text = getString(R.string.reorder),
                    onClick = { close(); controller.reOrder() },
                    icon = { Icon(painterResource(R.drawable.ic_baseline_refresh_24), contentDescription = null) },
                )
            }
            // Round 15a. NOT a reversal of round 12a's "compact list" deletion: that toggle was
            // removed because both of its row bodies measured identically inside the same
            // heightIn(min = 48.dp), so it moved no pixel. This one removes a whole line from the
            // row. Shown only where the examples can appear at all — elsewhere it would be a
            // control that changes nothing.
            if (controller.mode.styleTagsShown) {
                AbMenuItem(
                    text = getString(R.string.show_style_examples),
                    onClick = { close(); controller.toggleStyleTags() },
                    checkable = true,
                    checked = styleTagsVisible,
                    // AbMenu.kt:107-112 puts a `checkable` row's tick in the TRAILING slot, so this
                    // row has nothing in the leading one while every other row in this menu has an
                    // icon — without a reserved slot its label would sit 24dp left of its
                    // neighbours'. An icon is not an option here: `:app/src/main` has no Material
                    // ImageVectors on purpose (material-icons-extended is testImplementation-only,
                    // app/build.gradle.kts:507), and no drawable in the tree means "style example".
                    reserveIconSlot = true,
                )
            }
            if (controller.mode.hasResetButton) {
                AbMenuItem(
                    text = getString(R.string.reset_generic),
                    onClick = { close(); controller.reset() },
                    icon = { Icon(painterResource(R.drawable.ic_baseline_undo_24), contentDescription = null) },
                )
            }
            // Export/import StudyPads: visible in ALL modes (classic ManageLabels.kt:379-386
            // onCreateOptionsMenu parity — only resetButton/reOrder are mode-conditional there).
            // Icons deliberately do NOT copy classic's `manage_labels_options_menu.xml`, which reused
            // ic_baseline_undo_24 (an undo glyph) for both these rows — a copy-paste artefact there,
            // not ported here.
            AbMenuItem(
                text = getString(R.string.export_something, getString(R.string.studypads)),
                onClick = { close(); onExportStudyPads() },
                icon = { Icon(painterResource(R.drawable.file_export), contentDescription = null) },
            )
            AbMenuItem(
                text = getString(R.string.import_items, getString(R.string.studypads)),
                onClick = { close(); onImportStudyPads() },
                icon = { Icon(painterResource(R.drawable.ic_file_download_24dp), contentDescription = null) },
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
}

/** The label's glyph for a list row. The TINT is the caller's (the screen's) decision — this used
 *  to derive it and grey out every label without a custom icon, which is the colourless list the
 *  round-10a feedback reported. The default drawable mirrors classic `ManageLabelItemAdapter`:
 *  the tag glyph, or the StudyPad glyph in STUDYPAD mode (`ManageLabelItemAdapter.kt:171-173,223`). */
@Composable
private fun ManageLabelIcon(name: String?, tint: Color, studyPadMode: Boolean) {
    val defaultId = if (studyPadMode) R.drawable.ic_baseline_studypads_24 else R.drawable.ic_label_24dp
    Icon(
        painter = painterResource(customIconMap[name] ?: defaultId),
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(24.dp),
    )
}

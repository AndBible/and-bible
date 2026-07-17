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
package net.bible.android.view.activity.workspaces

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import net.bible.android.activity.R
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.settings.TextDisplaySettingsActivity
import net.bible.service.common.CommonUtils
import net.bible.service.device.ScreenSettings
import net.bible.sharedcore.workspaces.WorkspaceSelectorController
import net.bible.sharedcore.workspaces.WorkspaceService
import net.bible.sharedui.ProvideAppLocals
import net.bible.sharedui.theme.AbTheme
import net.bible.sharedui.workspaces.WorkspaceSelectorScreen
import org.koin.android.ext.android.inject

/**
 * Compose host for the workspace selector — the new-path twin of classic [WorkspaceSelectorActivity].
 * Result parity: builds its result Intent with the CLASSIC class + `workspaceId`/`changed` extras so
 * `MainBibleActivity.onActivityResult` (`WORKSPACE_CHANGED`) is untouched.
 *
 * TextDisplaySettings round-trip: classic [TextDisplaySettingsActivity.setResult] does NOT echo back a
 * `workspaceId` extra (only `settingsBundle`/`reset`/`edited`/`dirtyTypes` — the workspace id is buried
 * inside the `settingsBundle` JSON, which classic `WorkspaceSelectorActivity.onActivityResult` parses out
 * via `SettingsBundle.fromJson(...).workspaceId`). So the id is stashed host-side in
 * [pendingSettingsWorkspaceId] before launch and consumed on return, instead of being read off the
 * result `Intent` extras.
 */
class WorkspaceSelectorComposeActivity : ActivityBase() {
    private val service: WorkspaceService by inject()
    private var finished = false

    /** Id of the workspace whose settings are being edited, set right before launching
     *  [TextDisplaySettingsActivity] and consumed in [onActivityResult] (see class doc). */
    private var pendingSettingsWorkspaceId: String? = null

    private val controller by lazy {
        WorkspaceSelectorController(
            service = service, scope = lifecycleScope,
            onResult = { workspaceId, changed ->
                setResult(Activity.RESULT_OK, resultIntent().apply {
                    if (workspaceId != null) putExtra("workspaceId", workspaceId)
                    putExtra("changed", changed)
                })
                finished = true; finish()
            },
            onCancel = {
                setResult(Activity.RESULT_CANCELED, resultIntent()); finished = true; finish()
            },
            onEditSettings = { id ->
                pendingSettingsWorkspaceId = id
                // NOTE: calls service.settingsBundleJson(id) directly, NOT controller.settingsBundleJson(id) —
                // the latter would recursively reference `controller` from inside its own `by lazy` initializer.
                startActivityForResult(
                    Intent(this, TextDisplaySettingsActivity::class.java)
                        .putExtra("settingsBundle", service.settingsBundleJson(id)),
                    WORKSPACE_SETTINGS_CHANGED,
                )
            },
        )
    }

    private fun resultIntent() = Intent(this, WorkspaceSelectorActivity::class.java)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        service.saveCurrentIntoDb()
        controller.load()
        setContent {
            ProvideAppLocals {
                AbTheme(
                    darkTheme = ScreenSettings.nightMode,
                    colorMode = CommonUtils.settings.displayColorMode,
                    disableAnimations = CommonUtils.settings.disableAnimations,
                ) {
                    val workspaces by controller.workspaces.collectAsState()
                    val dirty by controller.dirty.collectAsState()
                    val query by controller.query.collectAsState()
                    val filtering by controller.filtering.collectAsState()
                    val canDelete by controller.canDelete.collectAsState()
                    val copy by controller.copySettingsState.collectAsState()
                    val pending by controller.pendingSelectId.collectAsState()
                    WorkspaceSelectorScreen(
                        title = getString(R.string.workspace_selector_title),
                        workspaces = workspaces, dirty = dirty, canDelete = canDelete,
                        filtering = filtering, query = query, copySettingsState = copy, pendingSelectId = pending,
                        onQueryChange = controller::setQuery,
                        onMove = controller::moveIndex,
                        onSelect = controller::selectWorkspace,
                        onRename = controller::rename,
                        onClone = controller::clone,
                        onDelete = controller::requestDelete,
                        onEditSettings = controller::editSettings,
                        onCopySettings = controller::beginCopySettings,
                        onCopySettingsToGlobal = controller::beginCopySettingsToGlobal,
                        onChooseCopyTypes = controller::chooseCopyTypes,
                        onChooseCopyTargets = controller::chooseCopyTargets,
                        onCancelCopySettings = controller::cancelCopySettings,
                        onCreate = controller::createNew,
                        onSave = controller::save,
                        onCancel = controller::cancel,
                        onConfirmPendingSelect = controller::confirmPendingSelect,
                        onDismissPendingSelect = controller::dismissPendingSelect,
                        onHelp = { CommonUtils.showHelp(this, listOf(R.string.help_workspaces_title)) },
                        onNavigateUp = { controller.cancel() },
                    )
                }
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { controller.cancel(); super.onBackPressed() }

    override fun onDetachedFromWindow() {
        if (!finished) controller.cancel()
        super.onDetachedFromWindow()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == WORKSPACE_SETTINGS_CHANGED && data != null) {
            val extras = data.extras!!
            val id = pendingSettingsWorkspaceId
            if (id != null) {
                controller.applyWorkspaceSettings(
                    id = id,
                    settingsBundleJson = extras.getString("settingsBundle")!!,
                    reset = extras.getBoolean("reset"),
                )
            }
            pendingSettingsWorkspaceId = null
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    companion object { const val WORKSPACE_SETTINGS_CHANGED = 999 }
}

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
package net.bible.android.view.activity.settings

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import net.bible.android.activity.R
import net.bible.android.view.Screen
import net.bible.android.view.ScreenLauncher
import net.bible.android.view.activity.base.ActivityBase
import net.bible.service.common.CommonUtils
import net.bible.service.device.ScreenSettings
import net.bible.sharedcore.settings.SyncSettingsController
import net.bible.sharedcore.settings.SyncSettingsLabels
import net.bible.sharedui.ProvideAppLocals
import net.bible.sharedui.settings.SyncSettingsScreen
import net.bible.sharedui.theme.AbTheme

/**
 * Compose host for the cloud-sync settings screen — the new-path twin of classic
 * [SyncSettingsActivity] (`R.xml.sync_settings`). The service is host-constructed (NOT a Koin
 * singleton) because its OAuth methods are activity-bound. `document_sync_manage` navigates to
 * [Screen.CloudDocuments] via [ScreenLauncher]; a category-enable posts `MainBibleAfterRestore`
 * inside the service impl (classic parity). `onResume` refreshes to reflect a sign-in/out or a
 * DocumentSyncSettings change that happened while backgrounded.
 */
class SyncSettingsComposeActivity : ActivityBase() {

    private val service by lazy {
        SyncSettingsServiceImpl(scope = lifecycleScope, activityProvider = { this })
    }

    private val controller by lazy {
        SyncSettingsController(
            service = service,
            scope = lifecycleScope,
            labels = buildLabels(),
            onOpenCloudDocuments = { ScreenLauncher.open(this, Screen.CloudDocuments) },
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ProvideAppLocals {
                AbTheme(
                    darkTheme = ScreenSettings.nightMode,
                    colorMode = CommonUtils.settings.displayColorMode,
                    disableAnimations = CommonUtils.settings.disableAnimations,
                ) {
                    val uiState by controller.state.collectAsState()
                    SyncSettingsScreen(
                        uiState = uiState,
                        onUp = { finish() },
                        onSwitch = controller::onSwitch,
                        onListChoice = controller::onListChoice,
                        onTextInput = controller::onTextInput,
                        onNavigate = controller::onNavigate,
                        onConfirmReset = controller::confirmReset,
                        onConfirmEnableDocuments = controller::confirmEnableDocuments,
                        onDismissDialog = controller::dismissDialog,
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // A sign-in/out or DocumentSyncSettings change may have happened while backgrounded.
        service.refresh()
    }

    private fun buildLabels() = SyncSettingsLabels(
        screenTitle = getString(R.string.cloud_sync_title),
        generalCat = getString(R.string.sync_general_settings),
        syncCat = getString(R.string.synchronization_categories),
        documentSyncCat = getString(R.string.document_sync_category_title),
        adapterTitle = getString(R.string.sync_adapter),
        resetTitle = getString(R.string.reset_sync),
        resetSummary = getString(R.string.prefs_reset_sync_summary),
        cloudInfoTitle = getString(R.string.cloud_info),
        serverUrlTitle = getString(R.string.auth_server_uri),
        usernameTitle = getString(R.string.auth_username),
        passwordTitle = getString(R.string.auth_password),
        folderPathTitle = getString(R.string.auth_folder_path),
        folderPathSummary = getString(R.string.auth_folder_path_summary),
        bookmarksTitle = getString(R.string.bookmarks),
        workspacesTitle = getString(R.string.help_workspaces_title),
        myDocumentsTitle = getString(R.string.my_documents_title),
        aiSettingsTitle = getString(R.string.ai_settings_sync_title),
        progressTitle = getString(R.string.progress_sync_title),
        documentsTitle = getString(R.string.document_sync_title),
        documentsSummary = getString(R.string.document_sync_contents),
        autoDownloadTitle = getString(R.string.document_sync_auto_download_title),
        autoDownloadSummary = getString(R.string.document_sync_auto_download_summary),
        autoUploadTitle = getString(R.string.document_sync_auto_upload_title),
        autoUploadSummary = getString(R.string.document_sync_auto_upload_summary),
        autoDeleteTitle = getString(R.string.document_sync_auto_delete_title),
        autoDeleteSummary = getString(R.string.document_sync_auto_delete_summary),
        wifiOnlyTitle = getString(R.string.document_sync_wifi_only_title),
        wifiOnlySummary = getString(R.string.document_sync_wifi_only_summary),
        manageTitle = getString(R.string.document_sync_manage_title),
        manageSummary = getString(R.string.document_sync_manage_summary),
        resetConfirmMessage = getString(R.string.sync_confirmation),
        invalidUrlMessage = getString(R.string.invalid_url_message),
    )
}

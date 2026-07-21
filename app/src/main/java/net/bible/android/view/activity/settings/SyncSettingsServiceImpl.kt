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

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.bible.service.cloudsync.CloudAdapters
import net.bible.service.common.CommonUtils
import net.bible.sharedcore.settings.Choice2
import net.bible.sharedcore.settings.SyncSettingsService
import net.bible.sharedcore.settings.SyncSettingsSnapshot

/**
 * Android impl of [SyncSettingsService]. Unlike [AppSettingsServiceImpl], every key here persists
 * to [CommonUtils.settings] uniformly - there is no realSharedPreferences whitelist and no
 * inverse-set bookkeeping (`sync_settings.xml` has no `InverseMultiSelectListPreference`s).
 *
 * NOTE: this intentionally diverges from `SyncSettingsActivity`'s classic `PreferenceDataStore`
 * wiring, which routes the four credential keys (`cloud_sync_server_url/username/password/
 * folder_path`) through `CommonUtils.realSharedPreferences` (see `SharedPrefsDataStore`) and the
 * four `sync_documents_*` toggles through `DocumentSyncSettings` (the `DocumentSyncDatabase`
 * singleton the sync engine actually reads), not the generic `PreferenceStore`. The Compose port
 * is scoped (Batch 10b plan, Task 4) to route everything through `CommonUtils.settings` instead -
 * a deliberate simplification, not a bug. `SyncSettingsComposeActivity` (Task 5) is expected to
 * wire the action rows (`cloud_sync_reset`/`cloud_sync_info`/`document_sync_manage`) to the
 * classic helpers, but the actual document-sync auto-operation toggles' effective backing store
 * is out of scope for this file.
 *
 * Defaults reproduce `sync_settings.xml`:
 *  - `sync_enable_*` (bookmarks/workspaces/readingplans/mydocuments/ai_settings/progress/documents)
 *    default `false`.
 *  - `sync_documents_*` (auto_download/auto_upload/auto_delete/wifi_only) default `true`.
 *  - The four credential/adapter strings default to `""`, except `sync_adapter`, which mirrors
 *    `CloudAdapters.current`'s fallback (`allEnabled.first()`) when unset.
 */
class SyncSettingsServiceImpl : SyncSettingsService {

    private val _snapshot = MutableStateFlow(buildSnapshot())
    override val snapshot: StateFlow<SyncSettingsSnapshot> = _snapshot.asStateFlow()

    private fun syncAdapterChoices(): List<Choice2> =
        CloudAdapters.allEnabled.map { Choice2(it.name, it.displayName) }

    private fun buildSnapshot(): SyncSettingsSnapshot {
        val adapterChoices = syncAdapterChoices()
        return SyncSettingsSnapshot(
            syncAdapter = CommonUtils.settings.getString("sync_adapter", null)
                ?: adapterChoices.firstOrNull()?.value.orEmpty(),
            syncAdapterChoices = adapterChoices,
            serverUrl = CommonUtils.settings.getString("cloud_sync_server_url", "") ?: "",
            username = CommonUtils.settings.getString("cloud_sync_username", "") ?: "",
            password = CommonUtils.settings.getString("cloud_sync_password", "") ?: "",
            folderPath = CommonUtils.settings.getString("cloud_sync_folder_path", "") ?: "",
            enableBookmarks = CommonUtils.settings.getBoolean("sync_enable_bookmarks", false),
            enableWorkspaces = CommonUtils.settings.getBoolean("sync_enable_workspaces", false),
            enableReadingPlans = CommonUtils.settings.getBoolean("sync_enable_readingplans", false),
            enableMyDocuments = CommonUtils.settings.getBoolean("sync_enable_mydocuments", false),
            enableAiSettings = CommonUtils.settings.getBoolean("sync_enable_ai_settings", false),
            enableProgress = CommonUtils.settings.getBoolean("sync_enable_progress", false),
            enableDocuments = CommonUtils.settings.getBoolean("sync_enable_documents", false),
            autoDownload = CommonUtils.settings.getBoolean("sync_documents_auto_download", true),
            autoUpload = CommonUtils.settings.getBoolean("sync_documents_auto_upload", true),
            autoDelete = CommonUtils.settings.getBoolean("sync_documents_auto_delete", true),
            wifiOnly = CommonUtils.settings.getBoolean("sync_documents_wifi_only", true),
        )
    }

    override fun setBool(key: String, value: Boolean) {
        CommonUtils.settings.setBoolean(key, value)
        refresh()
    }

    override fun setString(key: String, value: String) {
        CommonUtils.settings.setString(key, value)
        refresh()
    }

    override fun refresh() {
        _snapshot.value = buildSnapshot()
    }
}

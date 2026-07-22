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

import android.text.format.Formatter
import android.webkit.URLUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.bible.android.BibleApplication.Companion.application
import net.bible.android.activity.R
import net.bible.android.control.event.ABEventBus
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.page.MainBibleActivity
import net.bible.service.cloudsync.CloudAdapters
import net.bible.service.cloudsync.CloudSync
import net.bible.service.cloudsync.SyncableDatabaseDefinition
import net.bible.service.cloudsync.documents.DocumentSync
import net.bible.service.cloudsync.documents.DocumentSyncService
import net.bible.service.cloudsync.documents.DocumentSyncSettings
import net.bible.service.cloudsync.documents.DocumentSyncSummary
import net.bible.service.cloudsync.documents.computeDocumentSyncSummary
import net.bible.service.common.CommonUtils
import net.bible.sharedcore.settings.Choice2
import net.bible.sharedcore.settings.DocSyncSummaryData
import net.bible.sharedcore.settings.SyncCategoryKeys
import net.bible.sharedcore.settings.SyncSettingsService
import net.bible.sharedcore.settings.SyncSettingsSnapshot
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Android impl of [SyncSettingsService]. Reproduces classic `SyncSettingsFragment`
 * (`SyncSettings.kt`) key-for-key across its FOUR backing stores:
 *  - `cloud_sync_*` creds → `CommonUtils.realSharedPreferences` (classic `SharedPrefsDataStore`).
 *  - `sync_documents_*` → [DocumentSyncSettings] (classic `DocumentSyncPrefsDataStore`).
 *  - `sync_enable_*` → [SyncableDatabaseDefinition] `.syncEnabled` (backed by `CommonUtils.settings`).
 *  - `sync_adapter` → [CloudAdapters.current] (default `PreferenceStore`).
 *
 * The interactive OAuth methods need the current Activity, supplied lazily via [activityProvider]
 * (so this is NOT a global Koin singleton — the host constructs it with `this`). Async work (the
 * cloud bytes-used read, and the post-enable `CloudSync.start()` sequence) runs on [scope].
 */
class SyncSettingsServiceImpl(
    private val scope: CoroutineScope,
    private val activityProvider: () -> ActivityBase,
) : SyncSettingsService {

    private val _snapshot = MutableStateFlow(build())
    override val snapshot: StateFlow<SyncSettingsSnapshot> = _snapshot.asStateFlow()

    private val prefs get() = CommonUtils.realSharedPreferences

    private fun adapterSummary(): String {
        val current = CloudAdapters.current
        val isGoogleDrive = current == CloudAdapters.GOOGLE_DRIVE
        var result = application.getString(R.string.prefs_sync_introduction_summary1)
        if (isGoogleDrive) {
            result += " " + application.getString(
                R.string.prefs_sync_introduction_summary2,
                application.getString(R.string.app_name_medium),
            )
        }
        result += " " + application.getString(R.string.sync_adapter_summary, current.displayName)
        return result
    }

    private fun categorySummary(cat: SyncableDatabaseDefinition): String {
        val lastSyncStr = cat.lastSynchronized?.let {
            val sdf = SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault())
            ".\n\n" + application.getString(R.string.last_updated, sdf.format(Date(it)))
        } ?: ""
        return "${application.getString(cat.contentDescription)}$lastSyncStr"
    }

    private fun build(cloudInfoSummary: String? = null): SyncSettingsSnapshot {
        val signedIn = CloudSync.signedIn
        val isCloudSyncEnabled = CommonUtils.isCloudSyncEnabled
        val isGoogleDrive = CloudAdapters.current == CloudAdapters.GOOGLE_DRIVE
        val documentsEnabled = DocumentSyncSettings.enabled
        return SyncSettingsSnapshot(
            adapter = CloudAdapters.current.name,
            adapterChoices = CloudAdapters.allEnabled.map { Choice2(it.name, it.displayName) },
            adapterSummary = adapterSummary(),
            adapterEnabled = !signedIn,
            cloudInfoSummary = cloudInfoSummary,
            serverUrl = prefs.getString("cloud_sync_server_url", "") ?: "",
            username = prefs.getString("cloud_sync_username", "") ?: "",
            password = prefs.getString("cloud_sync_password", "") ?: "",
            folderPath = prefs.getString("cloud_sync_folder_path", "") ?: "",
            credsVisible = !isGoogleDrive,
            credsEnabled = !signedIn,
            resetVisible = isCloudSyncEnabled && signedIn,
            cloudInfoVisible = isCloudSyncEnabled && signedIn,
            categoryEnabled = SyncCategoryKeys.DISPLAY.associateWith { key ->
                SyncableDatabaseDefinition.nameToCategory[key.removePrefix("sync_enable_").uppercase()]!!.syncEnabled
            },
            categorySummary = SyncCategoryKeys.DISPLAY.associateWith { key ->
                categorySummary(SyncableDatabaseDefinition.nameToCategory[key.removePrefix("sync_enable_").uppercase()]!!)
            },
            documentsEnabled = documentsEnabled,
            documentCategoryVisible = signedIn,
            autoDownload = DocumentSyncSettings.autoDownload,
            autoUpload = DocumentSyncSettings.autoUpload,
            autoDelete = DocumentSyncSettings.autoDelete,
            wifiOnly = DocumentSyncSettings.wifiOnly,
            autoTogglesVisible = documentsEnabled,
            wifiOnlyVisible = documentsEnabled,
        )
    }

    override fun refresh() {
        _snapshot.value = build()
        if (CommonUtils.isCloudSyncEnabled && CloudSync.signedIn) {
            scope.launch {
                val mb = CloudSync.bytesUsed() / (1024.0 * 1024)
                _snapshot.value = build(
                    cloudInfoSummary = application.getString(R.string.cloud_info_summary, String.format("%.2f", mb)),
                )
            }
        }
    }

    override suspend fun signIn(): Boolean {
        if (CloudSync.signedIn) return true
        return CloudSync.signIn(activityProvider()) == true
    }

    override suspend fun scanDocuments(): DocSyncSummaryData {
        val items = withContext(Dispatchers.IO) { DocumentSync.scan() }
        val summary = computeDocumentSyncSummary(items, DocumentSyncSettings.blockList.all())
        return DocSyncSummaryData(
            uploadInitials = summary.uploadInitials,
            downloadInitials = summary.downloadInitials,
            uploadBytes = summary.uploadBytes,
            downloadBytes = summary.downloadBytes,
        )
    }

    override fun setCategoryEnabled(key: String, enabled: Boolean) {
        val cat = SyncableDatabaseDefinition.nameToCategory[key.removePrefix("sync_enable_").uppercase()]!!
        cat.syncEnabled = enabled
        if (enabled) {
            // Classic setupDrivePref ran the sync-start sequence + posted MainBibleAfterRestore after
            // sign-in, then recreate()d. We refresh the snapshot instead; the sequence is fire-and-forget.
            scope.launch {
                CloudSync.waitUntilFinished()
                CloudSync.start()
                CloudSync.waitUntilFinished()
                ABEventBus.post(MainBibleActivity.MainBibleAfterRestore())
                refresh()
            }
        }
    }

    override fun setDocumentsEnabled(summary: DocSyncSummaryData) {
        DocumentSyncSettings.enabled = true
        DocumentSyncService.start(application, summary.uploadInitials, summary.downloadInitials)
    }

    override fun disableDocuments() {
        DocumentSyncSettings.enabled = false
    }

    override fun setDocumentSyncToggle(key: String, value: Boolean) {
        when (key) {
            "sync_documents_auto_download" -> DocumentSyncSettings.autoDownload = value
            "sync_documents_auto_upload" -> DocumentSyncSettings.autoUpload = value
            "sync_documents_auto_delete" -> DocumentSyncSettings.autoDelete = value
            "sync_documents_wifi_only" -> DocumentSyncSettings.wifiOnly = value
        }
    }

    override fun setText(key: String, value: String): Boolean {
        if (key == "cloud_sync_server_url") {
            val isHttpOrHttps = value.startsWith("http://") || value.startsWith("https://")
            val valid = URLUtil.isValidUrl(value) && isHttpOrHttps && !value.endsWith("/login") && !value.contains(" ")
            if (!valid) return false
        }
        prefs.edit().putString(key, value).apply()
        return true
    }

    override fun setAdapter(value: String) {
        CloudAdapters.current = CloudAdapters.valueOf(value)
    }

    override suspend fun resetSync() {
        CloudSync.signOut()
    }

    override fun formatEnableDocumentsMessage(summary: DocSyncSummaryData): String {
        if (summary.isEmpty) return application.getString(R.string.document_sync_enable_dialog_nothing)
        val parts = buildList {
            if (summary.uploadCount > 0) add(
                if (summary.uploadBytes > 0)
                    application.getString(R.string.cloud_doc_summary_upload_size, summary.uploadCount,
                        Formatter.formatShortFileSize(application, summary.uploadBytes))
                else application.getString(R.string.cloud_doc_summary_upload, summary.uploadCount)
            )
            if (summary.downloadCount > 0) add(
                if (summary.downloadBytes > 0)
                    application.getString(R.string.cloud_doc_summary_download_size, summary.downloadCount,
                        Formatter.formatShortFileSize(application, summary.downloadBytes))
                else application.getString(R.string.cloud_doc_summary_download, summary.downloadCount)
            )
        }
        var m = parts.joinToString("\n")
        if (DocumentSyncSettings.wifiOnly && CommonUtils.isMeteredNetwork) {
            m += "\n" + application.getString(R.string.cloud_doc_wifi_waiting)
        }
        return m
    }
}

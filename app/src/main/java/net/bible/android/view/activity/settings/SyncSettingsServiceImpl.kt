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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.bible.service.cloudsync.CloudSync
import net.bible.service.cloudsync.CloudAdapterFactory
import net.bible.service.cloudsync.SyncableDatabaseDefinition
import net.bible.service.cloudsync.SyncSettingsCategories
import net.bible.service.cloudsync.SyncSettingsPlatform
import net.bible.service.cloudsync.DocumentSyncProvider
import net.bible.service.cloudsync.documents.DocumentSyncSettings
import net.bible.service.cloudsync.documents.computeDocumentSyncSummary
import net.bible.service.cloudsync.webdav.WebDavConfig
import net.bible.service.cloudsync.webdav.Propagation
import net.bible.service.cloudsync.webdav.formatFingerprint
import net.bible.sharedcore.cloud.DocumentSyncStarter
import net.bible.sharedcore.platform.AppEnvironment
import net.bible.sharedcore.platform.CoreStrings
import net.bible.sharedcore.webdav.WebDavUrl
import net.bible.sharedcore.settings.Choice2
import net.bible.sharedcore.settings.DocSyncSummaryData
import net.bible.sharedcore.settings.SyncCategoryKeys
import net.bible.sharedcore.settings.SyncSettingsService
import net.bible.sharedcore.settings.SyncSettingsSnapshot

/** Snapshot and settings routing; the host supplies its observation and durable-write scopes. */
class SyncSettingsServiceImpl(
    private val scope: CoroutineScope,
    private val writeScope: CoroutineScope,
    private val documentSettings: DocumentSyncSettings,
    private val cloudSync: CloudSync,
    private val adapters: CloudAdapterFactory,
    private val categories: SyncSettingsCategories,
    private val documents: DocumentSyncProvider,
    private val starter: DocumentSyncStarter,
    private val platform: SyncSettingsPlatform,
    private val strings: CoreStrings,
    private val environment: AppEnvironment,
    private val notifyDatabaseRestored: () -> Unit,
) : SyncSettingsService {
    private val _snapshot = MutableStateFlow(SyncSettingsSnapshot(
        adapter = "", adapterChoices = emptyList(), adapterSummary = "", adapterEnabled = false,
        cloudInfoSummary = null, serverUrl = "", username = "", password = "", folderPath = "",
        credsVisible = false, credsEnabled = false, resetVisible = false, cloudInfoVisible = false,
        categoryEnabled = emptyMap(), categorySummary = emptyMap(), documentsEnabled = false,
        documentCategoryVisible = false, autoDownload = true, autoUpload = true, autoDelete = true,
        wifiOnly = true, autoTogglesVisible = false, wifiOnlyVisible = false,
    ))
    override val snapshot: StateFlow<SyncSettingsSnapshot> = _snapshot.asStateFlow()

    init { refresh() }

    private val isWebDav get() = cloudSync.adapterId == "WEBDAV"
    private val webDavState get() = platform.webDavState

    /** Row keys stay `cloud_sync_*`; WebDAV stores its own copy under `webdav_sync_*`. */
    private fun storageKey(rowKey: String) =
        if (isWebDav && rowKey.startsWith("cloud_sync_")) rowKey.replaceFirst("cloud_sync_", "webdav_sync_") else rowKey

    private fun adapterSummary(): String {
        val current = cloudSync.adapterId
        var result = strings.syncIntroduction
        if (current == "GOOGLE_DRIVE") result += " " + strings.syncDriveIntroduction(environment.isDiscrete)
        return result + " " + strings.syncAdapterSummary(adapters.displayName(current))
    }

    private fun categorySummary(cat: SyncableDatabaseDefinition, lastSynchronized: Long?): String {
        val lastSyncStr = lastSynchronized?.let {
            ".\n\n" + strings.syncLastUpdated(platform.formatSyncTime(it))
        } ?: ""
        return "${strings.syncCategoryContents(cat.name)}$lastSyncStr"
    }

    private suspend fun build(cloudInfoSummary: String? = null): SyncSettingsSnapshot {
        val signedIn = cloudSync.signedIn
        val isCloudSyncEnabled = categories.syncEnabled
        val isGoogleDrive = cloudSync.adapterId == "GOOGLE_DRIVE"
        val documentsEnabled = documentSettings.enabled
        val displayed = SyncCategoryKeys.DISPLAY.map { SyncableDatabaseDefinition.nameToCategory[it.removePrefix("sync_enable_").uppercase()]!! }
        val lastSynchronized = displayed.associateWith { categories.lastSynchronized(it) }
        val certPin = if (isWebDav) webDavState.certPin else null
        return SyncSettingsSnapshot(
            adapter = cloudSync.adapterId,
            adapterChoices = cloudSync.adapterChoices.map { Choice2(it.value, it.label) },
            adapterSummary = adapterSummary(),
            adapterEnabled = !signedIn,
            cloudInfoSummary = cloudInfoSummary,
            serverUrl = platform.credential(storageKey("cloud_sync_server_url")),
            username = platform.credential(storageKey("cloud_sync_username")),
            password = platform.credential(storageKey("cloud_sync_password")),
            folderPath = platform.credential(storageKey("cloud_sync_folder_path")),
            credsVisible = !isGoogleDrive,
            credsEnabled = !signedIn,
            resetVisible = isCloudSyncEnabled && signedIn,
            cloudInfoVisible = isCloudSyncEnabled && signedIn,
            categoryEnabled = SyncCategoryKeys.DISPLAY.associateWith { key ->
                categories.enabled(SyncableDatabaseDefinition.nameToCategory[key.removePrefix("sync_enable_").uppercase()]!!)
            },
            categorySummary = SyncCategoryKeys.DISPLAY.associateWith { key ->
                val cat = SyncableDatabaseDefinition.nameToCategory[key.removePrefix("sync_enable_").uppercase()]!!
                categorySummary(cat, lastSynchronized[cat])
            },
            documentsEnabled = documentsEnabled,
            documentCategoryVisible = signedIn,
            autoDownload = documentSettings.autoDownload,
            autoUpload = documentSettings.autoUpload,
            autoDelete = documentSettings.autoDelete,
            wifiOnly = documentSettings.wifiOnly,
            autoTogglesVisible = documentsEnabled,
            wifiOnlyVisible = documentsEnabled,
            serverUrlHint = if (isWebDav) platform.webDavServerUrlHint else null,
            httpsOnly = isWebDav,
            certificateVisible = certPin != null,
            certificateEnabled = !signedIn,
            certificateSummary = certPin?.let { formatFingerprint(it.sha256).take(23) + "…" } ?: "",
        )
    }

    override fun refresh() {
        scope.launch {
            documentSettings.ensureLoaded()
            _snapshot.value = build()
            if (categories.syncEnabled && cloudSync.signedIn) {
                val mb = cloudSync.bytesUsed() / (1024.0 * 1024)
                _snapshot.value = build(
                    cloudInfoSummary = strings.syncCloudInfo(String.format("%.2f", mb)),
                )
            }
        }
    }

    override suspend fun signIn(): Boolean {
        if (cloudSync.signedIn) return true
        return cloudSync.signIn() == true
    }

    override suspend fun scanDocuments(): DocSyncSummaryData {
        documentSettings.ensureLoaded()
        val items = withContext(Dispatchers.IO) { documents.get().scan() }
        val summary = computeDocumentSyncSummary(items, documentSettings.blockList.all())
        return DocSyncSummaryData(
            uploadInitials = summary.uploadInitials,
            downloadInitials = summary.downloadInitials,
            uploadBytes = summary.uploadBytes,
            downloadBytes = summary.downloadBytes,
        )
    }

    override fun setCategoryEnabled(key: String, enabled: Boolean) {
        val cat = SyncableDatabaseDefinition.nameToCategory[key.removePrefix("sync_enable_").uppercase()]!!
        categories.setEnabled(cat, enabled)
        if (enabled) {
            // Classic setupDrivePref ran the sync-start sequence + broadcast the restore (now `notifyDatabaseRestored`) after
            // sign-in, then recreate()d. We refresh the snapshot instead; the sequence is fire-and-forget.
            writeScope.launch {
                cloudSync.waitUntilFinished()
                cloudSync.start()
                cloudSync.waitUntilFinished()
                notifyDatabaseRestored()
                refresh()
            }
        }
    }

    private val documentMutationMutex = Mutex()

    /** Loaded snapshots mutate before the caller refreshes; cold mutations queue across the load. */
    private fun mutateDocuments(mutation: () -> Unit) {
        writeScope.launch(start = CoroutineStart.UNDISPATCHED) {
            documentMutationMutex.withLock {
                documentSettings.ensureLoaded()
                mutation()
                // A cold load may have let the caller's refresh publish before this mutation.
                refresh()
            }
        }
    }

    override fun setDocumentsEnabled(summary: DocSyncSummaryData) = mutateDocuments {
        documentSettings.enabled = true
        starter.start(summary.uploadInitials, summary.downloadInitials)
    }

    override fun disableDocuments() = mutateDocuments {
        documentSettings.enabled = false
    }

    override fun setDocumentSyncToggle(key: String, value: Boolean) = mutateDocuments {
        when (key) {
            "sync_documents_auto_download" -> documentSettings.autoDownload = value
            "sync_documents_auto_upload" -> documentSettings.autoUpload = value
            "sync_documents_auto_delete" -> documentSettings.autoDelete = value
            "sync_documents_wifi_only" -> documentSettings.wifiOnly = value
        }
    }

    override fun setText(key: String, rawValue: String): Boolean {
        // WebDAV addresses are often pasted with a trailing space/newline; trim before validating and storing.
        val value = if (isWebDav && key == "cloud_sync_server_url") rawValue.trim() else rawValue
        if (key == "cloud_sync_server_url") {
            val valid = if (isWebDav) WebDavUrl.validate(value) else {
                val isHttpOrHttps = value.startsWith("http://") || value.startsWith("https://")
                platform.isValidUrl(value) && isHttpOrHttps && !value.endsWith("/login") && !value.contains(" ")
            }
            if (!valid) return false
        }
        platform.setCredential(storageKey(key), value)
        // A different server/folder invalidates the collection-mtime calibration.
        if (isWebDav && (key == "cloud_sync_server_url" || key == "cloud_sync_folder_path")) {
            webDavState.propagation = Propagation.UNKNOWN
        }
        // A pin is bound to its host: after a host change it can never match, so drop it.
        if (isWebDav && key == "cloud_sync_server_url") {
            val pin = webDavState.certPin
            if (pin != null && WebDavConfig(value, "", "", "").host != pin.host) webDavState.certPin = null
        }
        return true
    }

    override fun forgetCertificate() {
        webDavState.certPin = null
    }

    override fun setAdapter(value: String) {
        cloudSync.adapterId = value
    }

    override suspend fun resetSync() {
        cloudSync.signOut()
    }

    override fun formatEnableDocumentsMessage(summary: DocSyncSummaryData): String {
        if (summary.isEmpty) return strings.documentSyncNothing
        val parts = buildList {
            if (summary.uploadCount > 0) add(
                if (summary.uploadBytes > 0)
                    strings.documentSyncUploadSize(summary.uploadCount, platform.formatFileSize(summary.uploadBytes))
                else strings.documentSyncUpload(summary.uploadCount)
            )
            if (summary.downloadCount > 0) add(
                if (summary.downloadBytes > 0)
                    strings.documentSyncDownloadSize(summary.downloadCount, platform.formatFileSize(summary.downloadBytes))
                else strings.documentSyncDownload(summary.downloadCount)
            )
        }
        var m = parts.joinToString("\n")
        if (documentSettings.wifiOnly && environment.isMeteredNetwork) {
            m += "\n" + strings.documentSyncWifiWaiting
        }
        return m
    }
}

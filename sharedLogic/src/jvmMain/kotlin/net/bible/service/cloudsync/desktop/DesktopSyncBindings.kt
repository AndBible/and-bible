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
package net.bible.service.cloudsync.desktop

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import net.bible.android.control.backup.BackupHost
import net.bible.service.cloudsync.CloudAdapterFactory
import net.bible.service.cloudsync.SyncSettingsPlatform
import net.bible.service.cloudsync.CloudAdapter
import net.bible.service.cloudsync.host.IosBackupHost
import net.bible.service.cloudsync.webdav.CertPin
import net.bible.service.cloudsync.webdav.Propagation
import net.bible.service.cloudsync.webdav.WebDavStateStore
import net.bible.sharedcore.cloud.DocumentSyncStarter
import net.bible.sharedcore.log.Log
import net.bible.sharedcore.platform.CoreStrings
import net.bible.sharedcore.platform.DateTimeFormats
import net.bible.sharedcore.platform.files.FileDelivery
import net.bible.sharedcore.platform.files.FilePicker
import net.bible.sharedcore.platform.secrets.SecretStore
import net.bible.sharedcore.platform.sync.SyncScheduler
import net.bible.sharedcore.ui.dialog.AppDialogController
import org.koin.core.module.Module
import org.koin.dsl.module
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

// OWNER: plan D3c (desktop cloud sync). D3c replaces the bindings in this file only and adds its substitutes next to it.
// Everything below is a D2b stub: it lets the graph assemble and answers "nothing" (no sync, no cloud, no secrets kept).

private const val TAG = "DesktopSyncBindings"
private val reported = ConcurrentHashMap.newKeySet<String>()

/** Logs the first use of a stub member once, so a pending sync button costs one log line and never a crash. */
private fun pending(member: String) {
    if (reported.add(member)) Log.w(TAG, "desktop sync stub (D3c pending): $member")
}

/** The in-memory stand-in for the desktop secret store: nothing survives a restart. D3c replaces it. */
class DesktopSecretStoreStub : SecretStore {
    private val values = ConcurrentHashMap<String, String>()
    override fun get(key: String): String? = values[key]
    override fun put(key: String, value: String) { values[key] = value }
    override fun remove(key: String) { values.remove(key) }
}

/** No sync work is done; the answer is "did not sync". */
class DesktopSyncSchedulerStub : SyncScheduler {
    override fun startDatabaseSync(): Deferred<Boolean> { pending("SyncScheduler.startDatabaseSync"); return CompletableDeferred(false) }
}

/** A cloud with no adapters: the settings screen shows no provider. */
class DesktopCloudAdapterFactoryStub : CloudAdapterFactory {
    override val available: List<String> get() = emptyList()
    override fun displayName(id: String): String = id
    override fun create(id: String): CloudAdapter =
        throw UnsupportedOperationException("Cloud sync adapters are not available on desktop yet ($id)")
}

/** In-memory WebDAV state. */
private class MemoryWebDavState : WebDavStateStore {
    override var propagation: Propagation = Propagation.UNKNOWN
    override var certPin: CertPin? = null
}

/** Credentials in memory, URL check by scheme, formatting through the desktop date formats. */
class DesktopSyncSettingsPlatformStub(private val dates: () -> DateTimeFormats) : SyncSettingsPlatform {
    private val credentials = ConcurrentHashMap<String, String>()
    override fun credential(key: String): String = credentials[key].orEmpty()
    override fun setCredential(key: String, value: String) { credentials[key] = value }
    override fun isValidUrl(value: String): Boolean = value.startsWith("http://") || value.startsWith("https://")
    override fun formatFileSize(bytes: Long): String = when {
        bytes < 1000 -> "$bytes B"
        bytes < 1_000_000 -> String.format(Locale.getDefault(), "%.1f KB", bytes / 1000.0)
        else -> String.format(Locale.getDefault(), "%.1f MB", bytes / 1_000_000.0)
    }
    override fun formatSyncTime(timestamp: Long): String = dates().shortDate(timestamp) + " " + dates().shortTime(timestamp)
    override fun formatDecimal2(value: Double): String = String.format(Locale.getDefault(), "%.2f", value)
    override val webDavState: WebDavStateStore = MemoryWebDavState()
    override val webDavServerUrlHint: String get() = "https://"
}

/** Document sync does nothing yet. */
class DesktopDocumentSyncStarterStub : DocumentSyncStarter {
    override fun pushDocuments(initials: List<String>) = pending("DocumentSyncStarter.pushDocuments")
    override fun start(push: List<String>, download: List<String>, remove: List<String>, purge: List<String>, uninstall: List<String>) =
        pending("DocumentSyncStarter.start")
    override fun stop() = Unit
}

/**
 * The sync stack of the desktop graph. `BackupHost` is the hoisted [IosBackupHost] over the `FilePicker` and
 * `FileDelivery` ports that the document stream (D3b) binds in `DesktopDocumentBindings`.
 */
fun desktopSyncModule(): Module = module {
    single<SecretStore> { DesktopSecretStoreStub() }
    single<SyncScheduler> { DesktopSyncSchedulerStub() }
    single<CloudAdapterFactory> { DesktopCloudAdapterFactoryStub() }
    single<SyncSettingsPlatform> { DesktopSyncSettingsPlatformStub { get<DateTimeFormats>() } }
    single<DocumentSyncStarter> { DesktopDocumentSyncStarterStub() }
    single<BackupHost> {
        IosBackupHost(
            get<FilePicker>(), get<FileDelivery>(), { get<AppDialogController>() },
            { get<CoreStrings>().okay },
            { get<CoreStrings>().errorReport }, { get<CoreStrings>().errorBackup }, { get<CoreStrings>().errorSkip },
        )
    }
}

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

import net.bible.android.control.backup.BackupHost
import net.bible.service.cloudsync.BackgroundSyncPolicy
import net.bible.service.cloudsync.CloudAdapterFactory
import net.bible.service.cloudsync.CloudSync
import net.bible.service.cloudsync.CloudSyncProvider
import net.bible.service.cloudsync.DEFAULT_CLOUD_SYNC_INTERVAL_SECONDS
import net.bible.service.cloudsync.DocumentSyncProvider
import net.bible.service.cloudsync.SyncSettingsPlatform
import net.bible.service.cloudsync.SyncableDatabaseDefinition
import net.bible.service.cloudsync.host.IosBackupHost
import net.bible.service.cloudsync.host.IosCloudAdapterFactory
import net.bible.service.cloudsync.host.IosDocumentSyncStarter
import net.bible.service.cloudsync.host.IosWebDavCertificatePrompt
import net.bible.service.cloudsync.runDatabaseSyncJob
import net.bible.service.db.DatabaseContainer
import net.bible.sharedcore.cloud.DocumentSyncStarter
import net.bible.sharedcore.platform.AppCoroutineScope
import net.bible.sharedcore.platform.AppEnvironment
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.platform.CoreStrings
import net.bible.sharedcore.platform.UserNotifier
import net.bible.sharedcore.platform.desktop.DesktopExitTasks
import net.bible.sharedcore.platform.desktop.DesktopLifecycleEvents
import net.bible.sharedcore.platform.desktop.secrets.DesktopSecretStore
import net.bible.sharedcore.platform.desktop.sync.DesktopBackgroundRefresh
import net.bible.sharedcore.platform.desktop.sync.DesktopSyncScheduler
import net.bible.sharedcore.platform.files.FileDelivery
import net.bible.sharedcore.platform.files.FilePicker
import net.bible.sharedcore.platform.secrets.SecretStore
import net.bible.sharedcore.platform.sync.BackgroundRefresh
import net.bible.sharedcore.platform.sync.SyncScheduler
import net.bible.sharedcore.settings.DeviceLocalPrefs
import net.bible.sharedcore.ui.dialog.AppDialogController
import okio.Path.Companion.toPath
import kotlinx.coroutines.delay
import org.koin.core.module.Module
import org.koin.dsl.module
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private const val READY_POLL_MS = 500L

/** A medium date ("30 Oct 2026") in the default locale, for the certificate prompt (like iOS `certificateDate`). */
internal fun desktopCertificateDate(epochMs: Long): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(epochMs))

/**
 * The sync stack of the desktop graph (plan D3c): the OS secret store, the hoisted sync runner and adapter factory over the
 * WebDAV client with certificate pinning, the settings platform, and the [DesktopSyncDriver] that runs the periodic and the
 * on-close passes. `BackupHost` is the hoisted [IosBackupHost] over the `FilePicker` and `FileDelivery` ports that the
 * document stream (D3b) binds. A NEW module is built per call. Everything that needs the open databases is resolved lazily.
 */
fun desktopSyncModule(): Module = module {
    single<SecretStore> { DesktopSecretStore.create(get<DeviceLocalPrefs>()) }
    single { DesktopSyncTexts(reflectiveStringLookup(getKoin())) }
    single<SyncSettingsPlatform> {
        DesktopSyncSettingsPlatform(get<DeviceLocalPrefs>(), get<SecretStore>()) { get<DesktopSyncTexts>().serverUrlHint() }
    }
    single<CloudAdapterFactory> {
        val clientFactory = DesktopWebDavClientFactory()
        DesktopCloudAdapterFactory(
            IosCloudAdapterFactory(
                platform = get<SyncSettingsPlatform>(),
                deviceId = get<AppEnvironment>().deviceIdentifier,
                displayName = { get<DesktopSyncTexts>().adapterDisplayName(it) },
                iCloudStore = { error("iCloud Drive is not available on desktop") },
                signInUi = IosWebDavCertificatePrompt({ get<AppDialogController>() }, get<CoreStrings>(), ::desktopCertificateDate),
                tempDir = get<AppEnvironment>().tmpDir.toPath(),
                clientFactory = clientFactory::create,
            ),
        )
    }
    single<DocumentSyncStarter> {
        IosDocumentSyncStarter(
            scope = get<AppCoroutineScope>(),
            documents = DocumentSyncProvider { get() },
            cloudSync = CloudSyncProvider { get() },
            notifier = get<UserNotifier>(),
            syncErrorMessage = { get<CoreStrings>().syncError },
            begin = { 0uL },
            end = {},
            invalidTask = ULong.MAX_VALUE,
        )
    }
    single<SyncScheduler> {
        DesktopSyncScheduler(get<AppCoroutineScope>()) { runDatabaseSyncJob(get<CloudSync>(), get<AppSettings>()) }
    }
    single<BackgroundRefresh> { DesktopBackgroundRefresh() }
    single(createdAtStart = true) {
        val scheduler = get<SyncScheduler>() as DesktopSyncScheduler
        fun syncEnabled() = SyncableDatabaseDefinition.ALL.any { it.isSyncEnabled(get<AppSettings>()) }
        DesktopSyncDriver(
            policy = BackgroundSyncPolicy(
                syncEnabled = ::syncEnabled,
                replacing = { DatabaseContainer.replacing },
                intervalSeconds = { get<AppSettings>().getLong("cloud_sync_interval", DEFAULT_CLOUD_SYNC_INTERVAL_SECONDS) },
                lastSynchronizedMs = { get<AppSettings>().getLong("globalLastSynchronized", 0L) },
                nowMs = System::currentTimeMillis,
                signedIn = { get<CloudSync>().signedIn },
                signIn = { get<CloudSync>().signIn(background = true) },
                startSync = {
                    scheduler.startBackgroundDatabaseSync { runDatabaseSyncJob(get<CloudSync>(), get<AppSettings>(), allowInteraction = false) }
                },
                stopSync = { result -> scheduler.stop(result) },
            ),
            closePass = {
                // Push local changes only: no interactive sign-in at exit, and nothing unless sync is on and already signed in.
                val cloud = if (DatabaseContainer.ready && syncEnabled()) get<CloudSync>() else null
                if (cloud == null || !cloud.signedIn || !cloud.hasChanges()) null
                else scheduler.startBackgroundDatabaseSync {
                    runDatabaseSyncJob(cloud, get<AppSettings>(), allowInteraction = false)
                }.await()
            },
            exitTasks = get<DesktopExitTasks>(),
            events = get<DesktopLifecycleEvents>(),
            scope = get<AppCoroutineScope>(),
            // The databases open after the graph is built; "ready" is the flag the sync engine itself checks.
            awaitReady = { while (!DatabaseContainer.ready) delay(READY_POLL_MS) },
            databasesReady = { DatabaseContainer.ready },
        )
    }
    single<BackupHost> {
        IosBackupHost(
            get<FilePicker>(), get<FileDelivery>(), { get<AppDialogController>() },
            { get<CoreStrings>().okay },
            { get<CoreStrings>().errorReport }, { get<CoreStrings>().errorBackup }, { get<CoreStrings>().errorSkip },
        )
    }
}

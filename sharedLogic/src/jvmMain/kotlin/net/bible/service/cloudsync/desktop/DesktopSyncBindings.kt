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
import net.bible.service.cloudsync.CloudSyncNamespace
import net.bible.service.cloudsync.CloudSyncProvider
import net.bible.service.cloudsync.SyncNamespace
import net.bible.service.cloudsync.DEFAULT_CLOUD_SYNC_INTERVAL_SECONDS
import net.bible.service.cloudsync.DocumentSyncProvider
import net.bible.service.cloudsync.SyncSettingsPlatform
import net.bible.service.cloudsync.SyncableDatabaseDefinition
import net.bible.service.cloudsync.host.IosBackupHost
import net.bible.service.cloudsync.host.IosCloudAdapterFactory
import net.bible.service.cloudsync.host.IosDocumentSyncStarter
import net.bible.service.cloudsync.host.IosSyncStrings
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import org.koin.core.module.Module
import org.koin.dsl.module
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private const val READY_POLL_MS = 500L

/**
 * The sync namespace (the folder name on the shared backend) of the desktop app. The desktop's own app id
 * (`net.bible.desktop`) would give it a folder of its own, so it would never meet the phone's data; the common namespace of
 * the Android and iOS release builds ([SyncNamespace.SHARED]) is what lets the desktop sync with them.
 *
 * - A packaged build ([versionName] set and not "dev") uses the shared namespace, like a release build.
 * - An unpackaged development run uses the shared namespace plus `.debug`, the folder of Android debug builds, so a developer's
 *   experiments never touch real user data.
 * - [suffixOverride] (system property `andbible.syncNamespaceSuffix`; may be empty) replaces the suffix: ".debug" on a packaged build
 *   for testing, "" to sync with release data from a development run.
 */
fun desktopSyncNamespace(versionName: String?, suffixOverride: String?): String {
    val version = versionName?.trim()
    val suffix = suffixOverride ?: if (!version.isNullOrEmpty() && version != "dev") "" else ".debug"
    return SyncNamespace.SHARED + suffix
}

/**
 * Suspends until sync can really work. `DatabaseContainer.ready` is set BEFORE the container is built and migrated, so it is
 * not enough: [open] (reading `DatabaseContainer.instance`) must also succeed, and [isReady] must still hold after it. The
 * binding's [isReady] also requires the app to be initialised, because the desktop `AppSettings` booleans answer their
 * DEFAULT until then (`DatabaseAppSettings` guard), so a pass before that sees "sync is off" and does nothing until the next
 * interval. A failure is retried every [pollMs]; the caller's cancellation ends the wait.
 */
internal suspend fun awaitDatabasesOpen(isReady: () -> Boolean, open: () -> Unit, pollMs: Long = READY_POLL_MS) {
    while (true) {
        val opened = isReady() && try { open(); true } catch (e: CancellationException) { throw e } catch (_: Exception) { false }
        if (opened && isReady()) return
        delay(pollMs)
    }
}

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
    single {
        CloudSyncNamespace(desktopSyncNamespace(System.getProperty("andbible.version"), System.getProperty("andbible.syncNamespaceSuffix")))
    }
    single<SecretStore> { DesktopSecretStore.create(get<DeviceLocalPrefs>()) }
    single<SyncSettingsPlatform> {
        DesktopSyncSettingsPlatform(get<DeviceLocalPrefs>(), get<SecretStore>(), get<IosSyncStrings>().webDavServerUrlHint)
    }
    single<CloudAdapterFactory> {
        val clientFactory = DesktopWebDavClientFactory()
        DesktopCloudAdapterFactory(
            IosCloudAdapterFactory(
                platform = get<SyncSettingsPlatform>(),
                deviceId = get<AppEnvironment>().deviceIdentifier,
                displayName = get<IosSyncStrings>().adapterDisplayName,
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
            syncErrorMessage = get<IosSyncStrings>().syncError,
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
        // Databases open AND the app initialised: before that the settings booleans answer their defaults (sync looks off).
        fun appReady() = DatabaseContainer.ready && get<AppEnvironment>().isInitialized
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
                val cloud = if (appReady() && syncEnabled()) get<CloudSync>() else null
                if (cloud == null || !cloud.signedIn || !cloud.hasChanges()) null
                else scheduler.startBackgroundDatabaseSync {
                    runDatabaseSyncJob(cloud, get<AppSettings>(), allowInteraction = false)
                }.await()
            },
            exitTasks = get<DesktopExitTasks>(),
            events = get<DesktopLifecycleEvents>(),
            scope = get<AppCoroutineScope>(),
            awaitReady = { awaitDatabasesOpen(::appReady, { DatabaseContainer.instance }) },
            databasesReady = ::appReady,
        )
    }
    single<BackupHost> {
        IosBackupHost(
            get<FilePicker>(), get<FileDelivery>(), { get<AppDialogController>() },
            get<IosSyncStrings>().ok,
            { get<CoreStrings>().errorReport }, { get<CoreStrings>().errorBackup }, { get<CoreStrings>().errorSkip },
        )
    }
}

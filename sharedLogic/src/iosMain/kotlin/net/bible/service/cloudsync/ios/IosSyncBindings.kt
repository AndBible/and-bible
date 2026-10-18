package net.bible.service.cloudsync.ios

import kotlinx.cinterop.ExperimentalForeignApi
import net.bible.android.control.backup.BackupHost
import net.bible.service.cloudsync.BackgroundSyncPolicy
import net.bible.service.cloudsync.CloudAdapterFactory
import net.bible.service.cloudsync.CloudSync
import net.bible.service.cloudsync.CloudSyncProvider
import net.bible.service.cloudsync.DEFAULT_CLOUD_SYNC_INTERVAL_SECONDS
import net.bible.service.cloudsync.DocumentSyncProvider
import net.bible.service.cloudsync.SyncSettingsPlatform
import net.bible.service.cloudsync.SyncableDatabaseDefinition
import net.bible.service.cloudsync.runDatabaseSyncJob
import net.bible.service.cloudsync.webdav.WebDavMessage
import net.bible.service.db.DatabaseContainer
import net.bible.sharedcore.cloud.DocumentSyncStarter
import net.bible.sharedcore.platform.AppCoroutineScope
import net.bible.sharedcore.platform.AppEnvironment
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.platform.UserNotifier
import net.bible.sharedcore.platform.cloud.iCloudDriveStore
import net.bible.sharedcore.platform.files.IosFileDelivery
import net.bible.sharedcore.platform.files.IosFilePicker
import net.bible.sharedcore.platform.secrets.SecretStore
import net.bible.sharedcore.platform.sync.BackgroundRefresh
import net.bible.sharedcore.platform.sync.IosSyncScheduler
import net.bible.sharedcore.platform.sync.SyncScheduler
import net.bible.sharedcore.ui.dialog.AppDialogController
import okio.FileSystem
import org.koin.core.module.Module
import org.koin.dsl.module
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

/** Localized texts the iOS sync classes need; supplied by the host (`:sharedLogic` cannot see `Strings`). */
data class IosSyncStrings(
    val adapterDisplayName: (String) -> String,
    val webDavServerUrlHint: () -> String,
    val syncError: () -> String,
    val webDavMessage: (WebDavMessage) -> String,
    val ok: () -> String,
)

private fun nowMs(): Long = (NSDate().timeIntervalSince1970 * 1000).toLong()

/**
 * Koin bindings of the iOS sync stack (Stream D). Builds a NEW module per call (a shared top-level
 * module would leak singletons between Koin applications). Needs `AppSettings`, `AppEnvironment`,
 * `SecretStore`, `UserNotifier`, `AppDialogController`, `AppCoroutineScope`, `CloudSync` and
 * `DocumentSync` from the host/shared bindings; all are resolved lazily.
 *
 * The starter and scheduler use their default UIKit background-task calls directly, with no main-queue
 * hop: Apple documents `beginBackgroundTask`/`endBackgroundTask` as callable from any thread, and a
 * blocking `dispatch_sync` to main from inside the scheduler lock could deadlock against a main-thread
 * caller waiting for that lock.
 */
@OptIn(ExperimentalForeignApi::class)
fun buildIosSyncBindings(strings: IosSyncStrings, backgroundRefresh: BackgroundRefresh): Module = module {
    single { IosDevicePrefs() }
    single<SyncSettingsPlatform> {
        IosSyncSettingsPlatform(get(), get<SecretStore>(), strings.webDavServerUrlHint)
    }
    single<CloudAdapterFactory> {
        IosCloudAdapterFactory(
            platform = get<SyncSettingsPlatform>() as IosSyncSettingsPlatform,
            deviceId = get<AppEnvironment>().deviceIdentifier,
            displayName = strings.adapterDisplayName,
            iCloudStore = { iCloudDriveStore() },
            signInUi = PendingWebDavSignInUi(strings.webDavMessage),
            tempDir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY,
        )
    }
    single<DocumentSyncStarter> {
        IosDocumentSyncStarter(
            scope = get<AppCoroutineScope>(),
            documents = DocumentSyncProvider { get() },
            cloudSync = CloudSyncProvider { get() },
            notifier = get<UserNotifier>(),
            syncErrorMessage = strings.syncError,
        )
    }
    single<SyncScheduler> {
        IosSyncScheduler(
            scope = get<AppCoroutineScope>(),
            sync = { runDatabaseSyncJob(get<CloudSync>(), get<AppSettings>()) },
        )
    }
    single<BackupHost> {
        IosBackupHost(IosFilePicker(), IosFileDelivery(), { get<AppDialogController>() }, strings.ok)
    }
    single {
        IosBackgroundSync(
            BackgroundSyncPolicy(
                syncEnabled = { SyncableDatabaseDefinition.ALL.any { it.isSyncEnabled(get<AppSettings>()) } },
                replacing = { DatabaseContainer.replacing },
                intervalSeconds = { get<AppSettings>().getLong("cloud_sync_interval", DEFAULT_CLOUD_SYNC_INTERVAL_SECONDS) },
                lastSynchronizedMs = { get<AppSettings>().getLong("globalLastSynchronized", 0L) },
                nowMs = ::nowMs,
                signedIn = { get<CloudSync>().signedIn },
                signIn = { get<CloudSync>().signIn() },
                startSync = { get<SyncScheduler>().startDatabaseSync() },
            ),
            backgroundRefresh,
        )
    }
}

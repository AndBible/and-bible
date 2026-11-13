package net.bible.service.cloudsync.ios

import kotlinx.cinterop.ExperimentalForeignApi
import net.bible.android.control.backup.BackupHost
import net.bible.service.cloudsync.BackgroundSyncPolicy
import net.bible.service.cloudsync.CloudAdapterFactory
import net.bible.service.cloudsync.CloudSync
import net.bible.service.cloudsync.SyncableDatabaseDefinition
import net.bible.service.cloudsync.isAvailable
import net.bible.service.cloudsync.CloudSyncProvider
import net.bible.service.cloudsync.DEFAULT_CLOUD_SYNC_INTERVAL_SECONDS
import net.bible.service.cloudsync.DocumentSyncProvider
import net.bible.service.cloudsync.SyncSettingsPlatform
import net.bible.service.cloudsync.NetworkRestoreEdge
import net.bible.service.cloudsync.SyncNowWorkspaceSaver
import net.bible.service.cloudsync.SyncTriggerDriver
import org.koin.core.scope.Scope
import net.bible.service.cloudsync.runDatabaseSyncJob
import net.bible.service.cloudsync.webdav.WebDavMessage
import net.bible.service.db.DatabaseContainer
import net.bible.sharedcore.cloud.DocumentSyncStarter
import net.bible.sharedcore.platform.AppCoroutineScope
import net.bible.sharedcore.platform.AppEnvironment
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.platform.cloud.IosUbiquityContainer
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
import platform.Foundation.dateWithTimeIntervalSince1970
import io.ktor.client.HttpClient
import org.koin.dsl.onClose
import net.bible.service.cloudsync.drive.GOOGLE_DRIVE_HTTP
import io.ktor.client.engine.darwin.Darwin
import net.bible.service.cloudsync.drive.GoogleDriveRestAdapter
import net.bible.service.cloudsync.drive.createGoogleDriveHttpClient
import net.bible.service.cloudsync.drive.oauth.GoogleOAuthClient
import net.bible.service.cloudsync.drive.oauth.GoogleOAuthClients
import net.bible.service.cloudsync.drive.oauth.PkceGoogleAuth
import net.bible.service.cloudsync.ios.drive.IosWebAuthOAuthBrowser
import net.bible.service.cloudsync.host.HostBackupHost
import net.bible.service.cloudsync.host.HostCloudAdapterFactory
import net.bible.service.cloudsync.host.HostDocumentSyncStarter
import net.bible.service.cloudsync.host.HostSyncStrings

private fun nowMs(): Long = (NSDate().timeIntervalSince1970 * 1000).toLong()

internal fun certificateDate(epochMs: Long): String = platform.Foundation.NSDateFormatter().apply {
    dateStyle = platform.Foundation.NSDateFormatterMediumStyle
    timeStyle = platform.Foundation.NSDateFormatterNoStyle
}.stringFromDate(NSDate.dateWithTimeIntervalSince1970(epochMs / 1000.0))

/**
 * Koin bindings of the iOS sync stack (Stream D). Builds a NEW module per call (a shared top-level
 * module would leak singletons between Koin applications). Needs `AppSettings`, `AppEnvironment`,
 * `SecretStore`, `UserNotifier`, `AppDialogController`, `AppCoroutineScope`, `CloudSync` and
 * `DocumentSync` from the host/shared bindings; all are resolved lazily.
 *
 * The starter and scheduler get explicit `UiKitBackgroundTask` begin/end lambdas that call UIKit directly, with no
 * main-queue hop: Apple documents `beginBackgroundTask`/`endBackgroundTask` as callable from any thread, and a
 * blocking `dispatch_sync` to main from inside the scheduler lock could deadlock against a main-thread
 * caller waiting for that lock.
 */
@OptIn(ExperimentalForeignApi::class)
fun buildIosSyncBindings(
    strings: HostSyncStrings,
    backgroundRefresh: BackgroundRefresh,
    googleClient: GoogleOAuthClient? = GoogleOAuthClients.ios,
): Module = module {
    single { IosDevicePrefs() }
    // ONE container for the whole module: its lookup runs once and signIn() awaits that same lookup.
    single { IosUbiquityContainer().also { it.prefetch() } }
    single<SyncSettingsPlatform> {
        IosSyncSettingsPlatform(get(), get<SecretStore>(), strings.webDavServerUrlHint)
    }
    single(GOOGLE_DRIVE_HTTP) { createGoogleDriveHttpClient(Darwin.create()) } onClose { it?.close() }
    single<CloudAdapterFactory> {
        val googleDrive = googleClient?.let { c ->
            { background: Boolean ->
                val http = get<HttpClient>(GOOGLE_DRIVE_HTTP)
                GoogleDriveRestAdapter(
                    PkceGoogleAuth(c, IosWebAuthOAuthBrowser(c), get<SecretStore>(), http, ::nowMs, interactive = !background),
                    http,
                    signInRequiredMessage = strings.googleDriveSignInRequired,
                )
            }
        }
        HostCloudAdapterFactory(
            platform = get<SyncSettingsPlatform>(),
            deviceId = get<AppEnvironment>().deviceIdentifier,
            displayName = strings.adapterDisplayName,
            iCloudStore = { iCloudDriveStore(get<IosUbiquityContainer>()) },
            signInUi = IosWebDavSignInUi({ get<AppDialogController>() }, get()),
            tempDir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY,
            clientFactory = { c, s -> createIosWebDavClient(c, s, ::nowMs) },
            awaitReady = { get<IosUbiquityContainer>().awaitReady() },
            googleDrive = googleDrive,
        )
    }
    single<DocumentSyncStarter> {
        HostDocumentSyncStarter(
            scope = get<AppCoroutineScope>(),
            documents = DocumentSyncProvider { get() },
            cloudSync = CloudSyncProvider { get() },
            syncErrorMessage = strings.syncError,
            begin = UiKitBackgroundTask.begin("document-sync"),
            end = UiKitBackgroundTask.end,
            invalidTask = UiKitBackgroundTask.invalid,
            tracker = get(),
        )
    }
    single<SyncScheduler> {
        IosSyncScheduler(
            scope = get<AppCoroutineScope>(),
            sync = { runDatabaseSyncJob(get<CloudSync>(), get<AppSettings>()) },
        )
    }
    single {
        val saver = get<net.bible.service.cloudsync.SyncNowWorkspaceSaver>()
        net.bible.service.cloudsync.SyncNowPorts({ saver.save() }, { true }, {})
    }
    single<BackupHost> {
        HostBackupHost(IosFilePicker(), IosFileDelivery(), { get<AppDialogController>() }, strings.ok,
            { get<net.bible.sharedcore.platform.CoreStrings>().errorReport },
            { get<net.bible.sharedcore.platform.CoreStrings>().errorBackup },
            { get<net.bible.sharedcore.platform.CoreStrings>().errorSkip })
    }
    single {
        IosBackgroundSync(iosSyncPolicy(), backgroundRefresh)
    }
    single { NetworkRestoreEdge() }
    single {
        SyncTriggerDriver(
            policy = iosSyncPolicy(),
            scope = get<AppCoroutineScope>(),
            prepare = { getOrNull<SyncNowWorkspaceSaver>()?.save() },
            networkAvailable = { get<NetworkRestoreEdge>().available },
            backgroundAllowance = uiKitLeaveAllowance,
        )
    }
    single { IosSyncTriggers(get(), get()) }
}

/** The sync policy of every iOS pass (the BGTask refresh and the [SyncTriggerDriver]): non-interactive, never prompts. */
private fun Scope.iosSyncPolicy() = BackgroundSyncPolicy(
    syncEnabled = {
        get<CloudAdapterFactory>().isAvailable(get<AppSettings>()) &&
            SyncableDatabaseDefinition.ALL.any { it.isSyncEnabled(get<AppSettings>()) }
    },
    replacing = { DatabaseContainer.replacing },
    intervalSeconds = { get<AppSettings>().getLong("cloud_sync_interval", DEFAULT_CLOUD_SYNC_INTERVAL_SECONDS) },
    lastSynchronizedMs = { get<AppSettings>().getLong("globalLastSynchronized", 0L) },
    nowMs = ::nowMs,
    signedIn = { get<CloudSync>().signedIn },
    signIn = { get<CloudSync>().signIn(background = true) },
    startSync = {
        (get<SyncScheduler>() as IosSyncScheduler).startBackgroundDatabaseSync {
            runDatabaseSyncJob(get<CloudSync>(), get<AppSettings>(), allowInteraction = false)
        }
    },
    stopSync = { result -> (get<SyncScheduler>() as IosSyncScheduler).stop(result) },
)

package net.bible.sharedui.app

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import kotlinx.serialization.Serializable
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.control.document.DocumentControl
import net.bible.android.control.download.DownloadControl
import net.bible.android.control.link.LinkControl
import net.bible.android.control.page.PageControl
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.progress.ReadingProgressServiceImpl
import net.bible.android.control.readingplan.ReadingPlanControl
import net.bible.android.control.search.SearchControl
import net.bible.android.control.speak.SpeakControl
import net.bible.android.view.activity.bookmark.BookmarkRecreationState
import net.bible.android.view.activity.bookmark.BookmarksServiceImpl
import net.bible.android.control.versification.BookInstallWatcher
import net.bible.service.cloudsync.CloudSync
import net.bible.service.cloudsync.documents.DocumentSync
import net.bible.service.cloudsync.documents.DocumentSyncSettings
import net.bible.service.download.FakeBookFactory
import net.bible.service.history.HistoryManager
import net.bible.android.view.activity.settings.SyncSettingsServiceImpl
import net.bible.sharedcore.log.Log
import net.bible.sharedcore.cloud.DocumentSyncStarter
import net.bible.sharedcore.download.CustomRepositoryService
import net.bible.sharedcore.http.DownloadHttp
import net.bible.sharedcore.platform.AppCoroutineScope
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.bookmark.LabelEditService
import net.bible.sharedcore.bookmark.ManageLabelsService
import net.bible.sharedcore.platform.CoreStrings
import net.bible.sharedcore.platform.OrderedLauncher
import net.bible.sharedcore.platform.UserNotifier
import net.bible.sharedcore.search.BibleSearchService
import net.bible.sharedcore.search.EpubSearchService
import net.bible.sharedcore.search.SearchIndexService
import net.bible.sharedcore.search.SearchResultsCache
import net.bible.sharedcore.settings.AppSettingsService
import net.bible.sharedcore.workspaces.WorkspaceService
import net.bible.android.control.backup.BackupControl
import net.bible.sharedcore.platform.AppDirectories
import net.bible.sharedcore.platform.AppEnvironment
import net.bible.sharedcore.platform.DateTimeFormats
import net.bible.service.llm.AiSettings
import net.bible.sharedcore.ai.AiSettingsService
import net.bible.sharedcore.ai.DocumentFilterService
import net.bible.sharedcore.ai.LlmModelService
import net.bible.sharedcore.ai.LlmProviderService
import net.bible.sharedcore.ai.PromptService
import net.bible.sharedcore.ai.RawLogService
import net.bible.sharedcore.ai.ToolPermissionService
import net.bible.sharedui.app.ai.AiHost
import net.bible.sharedui.app.backup.BackupFeatureHost
import net.bible.sharedui.app.bookmark.BookmarkHost
import net.bible.sharedui.app.chooser.ChooserHost
import net.bible.sharedui.app.download.CloudDocumentsHost
import net.bible.sharedui.app.download.DownloadHost
import net.bible.sharedui.app.installzip.InstallZipHost
import net.bible.sharedui.app.installzip.InstallZipOpener
import net.bible.sharedui.app.ports.ClipboardText
import net.bible.sharedui.app.ports.DocumentInstallStarter
import net.bible.sharedui.app.ports.DebugActions
import net.bible.sharedui.app.ports.ExternalLinks
import net.bible.sharedui.app.ports.HostPorts
import net.bible.sharedui.app.ports.PlatformText
import net.bible.sharedui.app.ports.SettingsPlatform
import net.bible.sharedui.app.ports.TextDisplayPlatform
import net.bible.sharedui.app.mydocuments.MyDocumentsHost
import net.bible.sharedui.app.mydocuments.MyDocumentsRecreationState
import net.bible.sharedui.app.readingplan.ReadingPlanHost
import net.bible.sharedui.app.readingplan.ReadingPlanRecreationState
import net.bible.sharedui.app.search.SearchHost
import net.bible.sharedui.app.settings.SettingsHost
import net.bible.sharedui.app.welcome.WelcomeHost
import net.bible.sharedui.app.workspace.WorkspaceHost
import net.bible.sharedui.strings.Strings
import org.koin.core.Koin
import org.koin.core.parameter.parametersOf

private const val TAG = "AppHosts"

/**
 * Serializable snapshot of the host-held state that must survive `recreate()` and process death.
 * One nullable field per feature that has such state; the owning feature task adds it. [version] is the
 * schema version of the snapshot (a data class needs at least one property).
 */
@Serializable
data class AppHostsState(
    val version: Int = 1,
    /** The bookmark list, label manager and label editor state; null when nothing is worth restoring. */
    val bookmarks: BookmarkRecreationState? = null,
    /** The plans reset since they were last chosen; null when there are none. */
    val readingPlan: ReadingPlanRecreationState? = null,
    /** The My Documents export waiting for its folder picker; null when there is none. */
    val myDocuments: MyDocumentsRecreationState? = null,
)

/**
 * Owns every feature host for one platform host instance (one Activity lifetime on Android). [graphs] assembles the
 * deps of every non-reading graph, [Overlays] composes the host-level collectors and sheets, and [saveState] /
 * [restoreState] carry the host-held state across recreation. The reading graph and the Android-only overlay pieces
 * stay with the platform host. Constructing it must not touch the database; hosts resolve their collaborators lazily.
 */
class AppHosts(
    private val koin: Koin,
    private val shell: HostShell,
    private val ports: HostPorts,
    private val strings: Strings,
) {
    /** The first-run welcome's deps builder; built on first use, no database access. */
    val welcome: WelcomeHost by lazy { WelcomeHost(shell, ports.welcomePlatform()) }

    /**
     * The settings cluster's deps builder. Built on first use; every service and controller behind it resolves
     * lazily, so this touches no database.
     */
    val settings: SettingsHost by lazy {
        SettingsHost(
            shell = shell,
            strings = strings,
            appSettingsProvider = { koin.get<AppSettings>() },
            platform = koin.get<SettingsPlatform>(),
            links = koin.get<ExternalLinks>(),
            debug = koin.get<DebugActions>(),
            systemScreens = ports.systemScreens,
            helpDialogs = ports.helpDialogs,
            appSettingsServiceProvider = { koin.get<AppSettingsService>() },
            // Observe snapshots in the host scope; durable sync writes use the injected app scope.
            syncSettingsServiceProvider = { koin.get<SyncSettingsServiceImpl> { parametersOf(shell.hostScope) } },
            readingProgressSettingsServiceProvider = { koin.get() },
            readingProgressServiceProvider = { koin.get<ReadingProgressServiceImpl>() },
        )
    }

    /**
     * The search cluster's deps builder. Built on first use; every controller and service resolves lazily from
     * Koin, so this touches no database.
     */
    val search: SearchHost by lazy {
        SearchHost(
            shell = shell,
            strings = strings,
            appSettingsProvider = { koin.get<AppSettings>() },
            userNotifierProvider = { koin.get<UserNotifier>() },
            notificationPermission = ports.notificationPermission,
            links = koin.get<ExternalLinks>(),
            searchControlProvider = { koin.get<SearchControl>() },
            bibleSearchServiceProvider = { koin.get<BibleSearchService>() },
            searchIndexServiceProvider = { koin.get<SearchIndexService>() },
            windowControlProvider = { koin.get<WindowControl>() },
            pageControlProvider = { koin.get<PageControl>() },
            searchResultsCacheProvider = { koin.get<SearchResultsCache>() },
            epubSearchServiceProvider = { koin.get<EpubSearchService>() },
            linkControlProvider = { koin.get<LinkControl>() },
            fakeBookFactoryProvider = { koin.get<FakeBookFactory>() },
            // The activity's history traversal wraps this same singleton.
            historyManagerProvider = { koin.get<HistoryManager>() },
        )
    }

    private val workspaceLazy = lazy {
        WorkspaceHost(
            shell = shell,
            strings = strings,
            imagePicker = ports.imagePicker,
            helpDialogs = ports.helpDialogs,
            // A factory binding: one platform (and thumbnail cache) per host.
            platform = koin.get<TextDisplayPlatform>(),
            workspaceServiceProvider = { koin.get<WorkspaceService>() },
            appScopeProvider = { koin.get<AppCoroutineScope>() },
            activeWorkspaceId = { koin.get<WindowControl>().windowRepository.id.toString() },
        )
    }

    /**
     * The workspace cluster's deps builder (workspace selector, text display settings). Built on first use; the
     * services behind it resolve lazily, so this touches no database.
     */
    val workspace: WorkspaceHost by workspaceLazy

    /**
     * The chooser cluster's deps builder (general book, map, dictionary, grid passage, choose document). Built on
     * first use; every service behind it resolves lazily, so this touches no database.
     */
    val chooser: ChooserHost by lazy {
        ChooserHost(
            shell = shell,
            strings = strings,
            appSettingsProvider = { koin.get<AppSettings>() },
            documentDialogs = ports.documentDialogs,
            platform = ports.chooserPlatform(),
            openInstallZip = installZipOpener,
            windowControlProvider = { koin.get<WindowControl>() },
            documentControlProvider = { koin.get<DocumentControl>() },
            downloadControlProvider = { koin.get<DownloadControl>() },
            fakeBookFactoryProvider = { koin.get<FakeBookFactory>() },
            coreStringsProvider = { koin.get<CoreStrings>() },
        )
    }

    private val bookmarkLazy = lazy {
        BookmarkHost(
            shell = shell,
            strings = strings,
            icons = ports.labelIcons,
            studyPadExport = ports.studyPadExport,
            platform = ports.bookmarkPlatform(),
            openInstallZip = installZipOpener,
            appSettingsProvider = { koin.get<AppSettings>() },
            bookmarksServiceProvider = { koin.get<BookmarksServiceImpl>() },
            bookmarkControlProvider = { koin.get<BookmarkControl>() },
            speakControlProvider = { koin.get<SpeakControl>() },
            windowControlProvider = { koin.get<WindowControl>() },
            manageLabelsServiceProvider = { koin.get<ManageLabelsService>() },
            labelEditServiceProvider = { koin.get<LabelEditService>() },
            fakeBookFactoryProvider = { koin.get<FakeBookFactory>() },
            appScopeProvider = { koin.get<AppCoroutineScope>() },
            orderedLauncherProvider = { koin.get<OrderedLauncher>() },
            userNotifierProvider = { koin.get<UserNotifier>() },
            restored = current.bookmarks,
        )
    }

    /**
     * The bookmark cluster's deps builder (bookmark list, label manager, label editor) with its sessions and host
     * overlays. Built on first use; every service behind it resolves lazily, so this touches no database.
     */
    val bookmark: BookmarkHost by bookmarkLazy

    private val readingPlanLazy = lazy {
        ReadingPlanHost(
            shell = shell,
            strings = strings,
            platform = ports.readingPlanPlatform(),
            openInstallZip = installZipOpener,
            documentPicker = ports.documentPicker,
            readingPlanControlProvider = { koin.get<ReadingPlanControl>() },
            speakControlProvider = { koin.get<SpeakControl>() },
            appScopeProvider = { koin.get<AppCoroutineScope>() },
            restored = current.readingPlan,
        )
    }

    /**
     * The reading plan cluster's deps builder (daily reading, day list, plan selector) with its loaded-day state.
     * Built on first use; every control behind it resolves lazily, so this touches no database.
     */
    val readingPlan: ReadingPlanHost by readingPlanLazy

    private val myDocumentsLazy = lazy {
        MyDocumentsHost(
            shell = shell,
            strings = strings,
            documentPicker = ports.documentPicker,
            files = ports.myDocumentFiles,
            userNotifierProvider = { koin.get<UserNotifier>() },
            restoredDocumentsSearch = { bookmark.restoredState?.documentsSearch },
            restoredPages = { bookmark.restoredState?.pages },
            consumeRestoredPages = { bookmark.updateRestored { it?.copy(pages = null) } },
            restored = current.myDocuments,
        )
    }

    /**
     * The My Documents cluster's deps builder (document list, pages editor) with its import/export state. Built on
     * first use; the database is read only when a screen loads or an export runs, so this touches no database.
     */
    val myDocuments: MyDocumentsHost by myDocumentsLazy

    /**
     * The Backup & restore deps builder. Built on first use; every collaborator resolves lazily from Koin (the
     * Backup start is uninitialised), so this touches no database.
     */
    val backup: BackupFeatureHost by lazy {
        BackupFeatureHost(
            shell = shell,
            strings = strings,
            backupHost = ports.backupHost,
            backupControlProvider = { koin.get<BackupControl>() },
            settingsProvider = { koin.get<AppSettings>() },
            environmentProvider = { koin.get<AppEnvironment>() },
            coreStringsProvider = { koin.get<CoreStrings>() },
            directoriesProvider = { koin.get<AppDirectories>() },
            dateTimeFormatsProvider = { koin.get<DateTimeFormats>() },
        )
    }

    /**
     * The InstallZip destination's deps builder, its opener and its host-level answer collector. Built on first use;
     * InstallZip starts uninitialised from Backup, so this touches no database.
     */
    private val installZipLazy = lazy {
        InstallZipHost(
            shell = shell,
            strings = strings,
            settings = koin.get<SettingsPlatform>(),
            platform = ports.installZipPlatform(),
            starter = koin.get<DocumentInstallStarter>(),
            documentPicker = ports.documentPicker,
            orientation = ports.orientationLock,
        )
    }

    val installZip: InstallZipHost by installZipLazy

    /** The other hosts' way into InstallZip; resolves [installZip] only when one of them opens it. */
    private val installZipOpener: InstallZipOpener = { action, uris, onResult -> installZip.open(action, uris, onResult) }

    /**
     * The download cluster's deps builder (download, custom repositories, progress status). Built on first use; every
     * service behind it resolves lazily, so this touches no database.
     */
    val download: DownloadHost by lazy {
        DownloadHost(
            shell = shell,
            strings = strings,
            platform = ports.downloadPlatform(),
            notificationPermission = ports.notificationPermission,
            clipboard = koin.get<ClipboardText>(),
            openInstallZip = installZipOpener,
            // The search host owns the index jobs Progress status lists.
            observeProgressJobs = { onJobs -> search.observeProgressStatusJobs(onJobs) },
            appSettingsProvider = { koin.get<AppSettings>() },
            userNotifierProvider = { koin.get<UserNotifier>() },
            directoriesProvider = { koin.get<AppDirectories>() },
            environmentProvider = { koin.get<AppEnvironment>() },
            coreStringsProvider = { koin.get<CoreStrings>() },
            downloadHttpProvider = { koin.get<DownloadHttp>() },
            bookInstallWatcherProvider = { koin.get<BookInstallWatcher>() },
            downloadControlProvider = { koin.get<DownloadControl>() },
            documentControlProvider = { koin.get<DocumentControl>() },
            fakeBookFactoryProvider = { koin.get<FakeBookFactory>() },
            customRepositoryServiceProvider = { koin.get<CustomRepositoryService>() },
            documentSyncSettingsProvider = { koin.get<DocumentSyncSettings>() },
        )
    }

    /**
     * The cloud documents deps builder, which [download] shares its graph with. Built on first use; every service
     * behind it resolves lazily, so this touches no database.
     */
    val cloudDocuments: CloudDocumentsHost by lazy {
        CloudDocumentsHost(
            shell = shell,
            strings = strings,
            platformText = koin.get<PlatformText>(),
            platform = ports.cloudDocumentsPlatform(),
            helpDialogs = ports.helpDialogs,
            syncStarter = koin.get<DocumentSyncStarter>(),
            appSettingsProvider = { koin.get<AppSettings>() },
            documentSyncSettingsProvider = { koin.get<DocumentSyncSettings>() },
            documentSyncProvider = { koin.get<DocumentSync>() },
            cloudSyncProvider = { koin.get<CloudSync>() },
            // The bookmark host's saved state carries the screen's filters across a recreation.
            restoredFilters = { bookmark.restoredState?.cloudFilters },
            consumeRestoredFilters = { bookmark.updateRestored { state -> state?.copy(cloudFilters = null) } },
        )
    }

    /**
     * The AI cluster's deps builder. Built on first use; every service and controller behind it resolves lazily, so
     * this touches no database.
     */
    val ai: AiHost by lazy {
        AiHost(
            shell = shell,
            strings = strings,
            textShare = ports.textShare,
            clipboard = koin.get<ClipboardText>(),
            helpDialogs = ports.helpDialogs,
            documentPicker = ports.documentPicker,
            promptFiles = ports.promptFiles(),
            bugReport = ports.bugReport(),
            platform = ports.aiPlatform(),
            userNotifierProvider = { koin.get<UserNotifier>() },
            appScopeProvider = { koin.get<AppCoroutineScope>() },
            aiSettingsProvider = { koin.get<AiSettings>() },
            aiSettingsServiceProvider = { koin.get<AiSettingsService>() },
            llmModelServiceProvider = { koin.get<LlmModelService>() },
            llmProviderServiceProvider = { koin.get<LlmProviderService>() },
            promptServiceProvider = { koin.get<PromptService>() },
            rawLogServiceProvider = { koin.get<RawLogService>() },
            documentFilterServiceProvider = { koin.get<DocumentFilterService>() },
            toolPermissionServiceProvider = { koin.get<ToolPermissionService>() },
        )
    }

    /** The host instance is going away: lets hosts drop what only lives as long as it (no-op for hosts never built). */
    fun onHostDestroy() {
        if (workspaceLazy.isInitialized()) workspace.onHostDestroy()
        if (installZipLazy.isInitialized()) installZip.onHostDestroy()
    }

    /**
     * The deps of every non-reading feature graph, built from the 13 hosts (incl. cloudDocuments). The platform host composes this once per
     * composition (inside `remember`), so each host's `navDeps` runs once per navController. Everything behind the
     * deps is lazy (see the hosts), so this touches no database. [channels] are the result channels the platform host
     * still declares.
     */
    fun graphs(navController: NavHostController, channels: AppResultChannels): AppGraphs {
        // Built in the order the activity always built them (a few hosts' navDeps record state the later ones read).
        val ai = ai.navDeps(navController)
        val readingPlan = readingPlan.navDeps(navController)
        val search = search.navDeps(navController)
        val settings = settings.navDeps(navController, channels.readingProgress)
        val bookmark = bookmark.navDeps(navController, channels.bookmark, channels.manageLabels, channels.labelEdit)
        val download = download.navDeps(navController, channels.repositoryEditor, cloudDocuments.deps())
        val myDocuments = myDocuments.navDeps(
            navController, channels.myDocumentPages, channels.myDocuments,
            beforeSwitchDocument = channels.beforeSwitchMyDocument,
        )
        val chooser = chooser.navDeps(navController, channels.keyChooser, channels.passage, channels.document)
        val backup = backup.navDeps(navController)
        val installZip = installZip.navDeps(navController, channels.installZip)
        val welcome = welcome.navDeps(navController)
        val workspace = workspace.navDeps(navController, channels.workspace, channels.textSettings, channels.manageLabels)
        return AppGraphs(
            ai = ai, bookmark = bookmark, readingPlan = readingPlan, search = search, settings = settings,
            download = download, myDocuments = myDocuments, chooser = chooser, workspace = workspace,
            backup = backup, installZip = installZip, welcome = welcome,
        )
    }

    /** Every feature host's collectors and sheets, outside the NavHost. The order below is load-bearing. */
    @Composable
    fun Overlays() {
        // The InstallZip answer collector comes first, as it did in the activity, ahead of the bookmark cluster's.
        installZip.Overlays()
        bookmark.Overlays()
    }

    private var current = AppHostsState()

    /**
     * The snapshot to persist; feature hosts contribute their fields as they are lifted in. A host that was never
     * built carries what was restored. The default bookmark snapshot is stored as absent.
     */
    fun saveState(): AppHostsState {
        if (bookmarkLazy.isInitialized()) {
            // Per host: one throwing snapshot must not drop the state of the other hosts.
            val bookmarks = runCatching {
                bookmark.recreationSnapshot().takeUnless { it == BookmarkRecreationState() }
            }.onFailure { Log.e(TAG, "Saving the bookmark host state failed", it) }
                .getOrDefault(current.bookmarks)
            current = current.copy(bookmarks = bookmarks)
        }
        if (readingPlanLazy.isInitialized()) {
            val plan = runCatching {
                readingPlan.recreationSnapshot().takeUnless { it == ReadingPlanRecreationState() }
            }.onFailure { Log.e(TAG, "Saving the reading plan host state failed", it) }
                .getOrDefault(current.readingPlan)
            current = current.copy(readingPlan = plan)
        }
        if (myDocumentsLazy.isInitialized()) {
            val exports = runCatching {
                myDocuments.recreationSnapshot().takeUnless { it == MyDocumentsRecreationState() }
            }.onFailure { Log.e(TAG, "Saving the My Documents host state failed", it) }
                .getOrDefault(current.myDocuments)
            current = current.copy(myDocuments = exports)
        }
        return current
    }

    fun restoreState(state: AppHostsState?) {
        current = state ?: AppHostsState()
        if (bookmarkLazy.isInitialized()) bookmark.updateRestored { current.bookmarks }
        if (readingPlanLazy.isInitialized()) readingPlan.restore(current.readingPlan)
        if (myDocumentsLazy.isInitialized()) myDocuments.restore(current.myDocuments)
    }
}

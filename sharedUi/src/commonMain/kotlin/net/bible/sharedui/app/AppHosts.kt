package net.bible.sharedui.app

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import kotlinx.serialization.Serializable
import net.bible.android.control.document.DocumentControl
import net.bible.android.control.download.DownloadControl
import net.bible.android.control.link.LinkControl
import net.bible.android.control.page.PageControl
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.progress.ReadingProgressServiceImpl
import net.bible.android.control.search.SearchControl
import net.bible.service.download.FakeBookFactory
import net.bible.service.history.HistoryManager
import net.bible.android.view.activity.settings.SyncSettingsServiceImpl
import net.bible.sharedcore.platform.AppCoroutineScope
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.platform.CoreStrings
import net.bible.sharedcore.platform.UserNotifier
import net.bible.sharedcore.search.BibleSearchService
import net.bible.sharedcore.search.EpubSearchService
import net.bible.sharedcore.search.SearchIndexService
import net.bible.sharedcore.search.SearchResultsCache
import net.bible.sharedcore.settings.AppSettingsService
import net.bible.sharedcore.workspaces.WorkspaceService
import net.bible.sharedui.app.chooser.ChooserHost
import net.bible.sharedui.app.ports.DebugActions
import net.bible.sharedui.app.ports.ExternalLinks
import net.bible.sharedui.app.ports.HostPorts
import net.bible.sharedui.app.ports.SettingsPlatform
import net.bible.sharedui.app.ports.TextDisplayPlatform
import net.bible.sharedui.app.search.SearchHost
import net.bible.sharedui.app.settings.SettingsHost
import net.bible.sharedui.app.welcome.WelcomeHost
import net.bible.sharedui.app.workspace.WorkspaceHost
import net.bible.sharedui.strings.Strings
import org.koin.core.Koin
import org.koin.core.parameter.parametersOf

/**
 * Serializable snapshot of the host-held state that must survive `recreate()` and process death.
 * One nullable field per feature that has such state; the owning feature task adds it. [version] is the
 * schema version of the snapshot (a data class needs at least one property).
 */
@Serializable
data class AppHostsState(val version: Int = 1)

/**
 * Owns every feature host for one platform host instance (one Activity lifetime on Android).
 * Constructing it must not touch the database; hosts resolve their collaborators lazily.
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
            windowControlProvider = { koin.get<WindowControl>() },
            documentControlProvider = { koin.get<DocumentControl>() },
            downloadControlProvider = { koin.get<DownloadControl>() },
            fakeBookFactoryProvider = { koin.get<FakeBookFactory>() },
            coreStringsProvider = { koin.get<CoreStrings>() },
        )
    }

    /** The host instance is going away: lets hosts drop what only lives as long as it (no-op for hosts never built). */
    fun onHostDestroy() {
        if (workspaceLazy.isInitialized()) workspace.onHostDestroy()
    }

    /**
     * Until the activity's call site moves here (completed in Task 16), the activity builds `AppGraphs` itself
     * from each host's `navDeps(...)`, so this throws.
     */
    @Suppress("UNUSED_PARAMETER")
    fun graphs(navController: NavHostController): Nothing =
        throw NotImplementedError("AppHosts.graphs is completed in I2 Stream A Task 16")

    /** Host-level collectors and sheets, outside the NavHost; grows task by task. */
    @Composable
    fun Overlays() {
    }

    private var current = AppHostsState()

    /** The snapshot to persist; feature hosts contribute their fields as they are lifted in. */
    fun saveState(): AppHostsState = current

    fun restoreState(state: AppHostsState?) {
        current = state ?: AppHostsState()
    }
}

package net.bible.android.control

import kotlinx.coroutines.flow.map

import net.bible.android.common.resource.AndroidResourceProvider
import net.bible.android.common.resource.ResourceProvider
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.control.bookmark.BookmarkJsActions
import net.bible.android.control.document.DocumentControl
import net.bible.android.control.download.DownloadControl
import net.bible.android.control.download.DownloadQueue
import net.bible.service.download.CustomRepositoryServiceImpl
import net.bible.sharedcore.download.CustomRepositoryService
import net.bible.android.control.link.LinkControl
import net.bible.android.control.navigation.DocumentBibleBooksFactory
import net.bible.android.control.navigation.NavigationControl
import net.bible.android.control.page.CurrentPageManager
import net.bible.android.control.page.PageControl
import net.bible.android.control.page.window.WindowCommandsImpl
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.page.window.WindowStateServiceImpl
import net.bible.android.control.page.toolbar.ToolbarStateServiceImpl
import net.bible.sharedcore.reading.ToolbarStateService
import net.bible.android.control.progress.ProgressJsActions
import net.bible.android.control.progress.ReadingProgressServiceImpl
import net.bible.android.control.readingplan.ReadingPlanControl
import net.bible.android.control.search.SearchHighlighter
import net.bible.android.control.search.SearchTextHighlighter
import net.bible.android.control.search.BibleSearchServiceImpl
import net.bible.android.control.search.SearchControl
import net.bible.android.control.search.SearchIndexServiceImpl
import net.bible.android.view.activity.search.AndroidEpubSearchService
import net.bible.sharedcore.progress.ReadingProgressService
import net.bible.sharedcore.search.BibleSearchService
import net.bible.sharedcore.search.SearchResultsCache
import net.bible.sharedcore.search.EpubSearchService
import net.bible.sharedcore.search.SearchIndexService
import net.bible.android.control.speak.SpeakControl
import net.bible.android.control.speak.SpeakSettingsServiceImpl
import net.bible.android.control.speak.SpeakTransportServiceImpl
import net.bible.sharedcore.speak.SpeakSettingsService
import net.bible.sharedcore.speak.SpeakTransportService
import net.bible.android.view.activity.bookmark.LabelEditServiceImpl
import net.bible.sharedcore.bookmark.LabelEditService
import net.bible.android.view.activity.bookmark.ManageLabelsServiceImpl
import net.bible.sharedcore.bookmark.ManageLabelsService
import net.bible.android.view.activity.bookmark.BookmarksServiceImpl
import net.bible.sharedcore.bookmark.BookmarksService
import net.bible.android.view.activity.ai.AgentSessionServiceImpl
import net.bible.android.view.activity.ai.AiSettingsServiceImpl
import net.bible.android.view.activity.ai.DocumentFilterServiceImpl
import net.bible.android.view.activity.ai.LlmModelServiceImpl
import net.bible.android.view.activity.ai.LlmProviderServiceImpl
import net.bible.android.view.activity.ai.PromptServiceImpl
import net.bible.android.view.activity.ai.RawLogServiceImpl
import net.bible.android.view.activity.ai.ReadingLlmServiceImpl
import net.bible.android.view.activity.ai.ToolPermissionServiceImpl
import net.bible.sharedcore.ai.AgentPermissionController
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.sharedcore.ai.AiSettingsService
import net.bible.sharedcore.ai.DocumentFilterService
import net.bible.sharedcore.ai.LlmModelService
import net.bible.sharedcore.ai.LlmProviderService
import net.bible.sharedcore.ai.PromptService
import net.bible.sharedcore.ai.RawLogService
import net.bible.sharedcore.ai.ToolPermissionService
import net.bible.sharedcore.ai.reading.AgentSessionService
import net.bible.sharedcore.ai.reading.ReadingLlmService
import net.bible.android.view.activity.workspaces.WorkspaceServiceImpl
import net.bible.sharedcore.workspaces.WorkspaceService
import net.bible.android.view.activity.settings.ReadingProgressSettingsServiceImpl
import net.bible.sharedcore.settings.ReadingProgressSettingsService
import net.bible.android.view.activity.settings.TextDisplaySettingsServiceImpl
import net.bible.sharedcore.settings.TextDisplaySettingsService
import net.bible.sharedcore.window.WindowCommands
import net.bible.sharedcore.window.WindowStateService
import net.bible.android.control.versification.BibleTraverser
import net.bible.android.control.versification.BookInstallWatcher
import net.bible.android.platform.AndroidDocumentSyncStarter
import net.bible.sharedcore.cloud.DocumentSyncStarter
import net.bible.android.view.activity.speak.actionbarbuttons.SpeakActionBarButton
import net.bible.android.view.activity.speak.actionbarbuttons.SpeakStopActionBarButton
import net.bible.service.db.readingplan.ReadingPlanRepository
import net.bible.android.platform.AndroidReadingPlanSource
import net.bible.android.platform.AndroidHistoryPlatform
import net.bible.android.platform.AndroidLinkPlatform
import net.bible.android.control.link.LinkPlatform
import net.bible.android.view.activity.readingplan.ReadingPlanCatalog
import net.bible.service.readingplan.DistributedPlanDetails
import net.bible.service.readingplan.ReadingPlanTextFileDao
import net.bible.sharedcore.readingplan.ReadingPlanSource
import net.bible.service.device.speak.TextToSpeechServiceManager
import net.bible.service.common.CommonUtils
import net.bible.service.common.AndBibleAddons
import net.bible.service.db.DatabaseContainer
import net.bible.android.SharedConstants
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.platform.AppCoroutineScope
import net.bible.service.sword.SwordDocumentFacade
import net.bible.android.control.document.DocumentFileDeleter
import net.bible.sharedcore.platform.OrderedLauncher
import net.bible.sharedcore.platform.CoreStrings
import net.bible.sharedcore.platform.DateTimeFormats
import net.bible.sharedcore.platform.UserNotifier
import net.bible.sharedcore.platform.MarkdownRenderer
import net.bible.android.platform.AndroidCoreStrings
import net.bible.android.platform.AndroidDateTimeFormats
import net.bible.android.platform.AndroidUserNotifier
import org.koin.android.ext.koin.androidContext
import net.bible.service.history.HistoryManager
import net.bible.service.history.HistoryPlatform
import net.bible.service.history.HistoryTraversalFactory
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

/** The application's Koin module; the one instance every `startKoin` loads. */
val coreModule = buildCoreModule()

/**
 * Builds a NEW instance of the application's module. A Koin `Module` holds its definitions' instance factories, and
 * with them the cached singletons, so two Koin applications loading the same `Module` object share (and, on
 * `close()`, drop) each other's singletons. A test that builds the graph in an isolated application must load its own
 * instance from here, never [coreModule].
 */
fun buildCoreModule() = module {
    // ResourceProvider interface binding (was ApplicationModule.provideResourceProvider)
    singleOf(::AndroidResourceProvider) { bind<ResourceProvider>() }
    // DownloadControl (was ApplicationModule.provideDownloadControl)
    single { net.bible.service.download.FakeBookFactory(get()) }
    single { DownloadControl(get(), get(), get()) }
    single<net.bible.sharedcore.http.DownloadHttp> {
        net.bible.sharedcore.http.KtorDownloadHttp(get<net.bible.android.platform.AndroidHttpClients>().downloadClient) {
            org.crosswire.common.util.WebResource.getTimeout()
        }
    }
    single<net.bible.service.download.ManifestFetcher> { net.bible.service.download.KtorManifestFetcher(get()) }
    singleOf(::CustomRepositoryServiceImpl) { bind<CustomRepositoryService>() }

    single { net.bible.android.control.speak.AdvancedSpeakSettings(get()) }
    single<net.bible.android.control.speak.SpeakSettingsStore> { net.bible.android.platform.AndroidSpeakSettingsStore() }

    single<AppSettings> { CommonUtils.settings }
    single<net.bible.sharedcore.platform.secrets.SecretStore> { net.bible.android.platform.AndroidSecretStore(CommonUtils.realSharedPreferences) }
    single { net.bible.service.common.RecentDocumentsStore(get()) }
    single { AppCoroutineScope() }
    single { OrderedLauncher(get<AppCoroutineScope>()) }
    single {
        net.bible.service.common.GlobalTextDisplaySettingsStore(
            get(),
            { net.bible.service.db.DatabaseContainer.instance.workspaceDb.globalTextDisplaySettingsDao() },
            DatabaseContainer.databaseRestored,
            get<net.bible.service.cloudsync.CloudSync>().workspaceRefreshRequired,
            get<AppCoroutineScope>(),
            { net.bible.service.db.DatabaseContainer.instance.workspaceDb.invalidationTracker.createFlow("GlobalTextDisplaySettings").map { Unit } },
        )
    }
    single {
        net.bible.service.common.ReadingProgressSettingsStore(
            get(),
            { net.bible.service.db.DatabaseContainer.instance.progressDb.globalReadingProgressSettingsDao() },
            DatabaseContainer.databaseRestored,
            get<net.bible.service.cloudsync.CloudSync>().workspaceRefreshRequired,
            get<AppCoroutineScope>(),
            { net.bible.service.db.DatabaseContainer.instance.progressDb.invalidationTracker.createFlow("GlobalReadingProgressSettings").map { Unit } },
        )
    }
    single {
        net.bible.android.control.page.window.WorkspacePersistence(get()) {
            // The DAO is resolved per write: DatabaseContainer.reset()/restore replaces workspaceDb.
            DatabaseContainer.instance.workspaceDb.workspaceDao()
        }
    }
    single<net.bible.service.llm.AiSettings> {
        net.bible.service.llm.AiSettingsStore(
            { DatabaseContainer.instance.aiSettingsDb.globalAiSettingsDao() }, get(), get(), get(),
            DatabaseContainer.databaseRestored, get<net.bible.service.cloudsync.CloudSync>().workspaceRefreshRequired,
            DatabaseContainer.aiLifetime,
            { DatabaseContainer.instance.aiSettingsDb.invalidationTracker.createFlow("GlobalAiSettings").map { Unit } },
        )
    }
    single<net.bible.service.llm.agent.AgentPlatform> { net.bible.android.platform.AndroidAgentPlatform() }
    single { ProgressJsActions(get()) }
    single { BookmarkJsActions(get(), get()) }
    single<net.bible.service.cloudsync.SyncSettingsPlatform> { net.bible.android.platform.AndroidSyncSettingsPlatform(androidContext()) }
    single { net.bible.service.cloudsync.SyncSettingsCategories(get()) }
    factory { (scope: kotlinx.coroutines.CoroutineScope) ->
        net.bible.android.view.activity.settings.SyncSettingsServiceImpl(
            scope, get<AppCoroutineScope>(), get(), get(), get(), get(), get(), get(), get(), get(), get(),
            { net.bible.service.db.DatabaseContainer.notifyDatabaseRestored() },
        )
    }
    single<net.bible.service.cloudsync.CloudAdapterFactory> { net.bible.android.platform.AndroidCloudAdapterFactory(androidContext()) }
    single { net.bible.android.platform.AndroidSyncScheduler(androidContext()) }
    single<net.bible.sharedcore.platform.sync.SyncScheduler> { get<net.bible.android.platform.AndroidSyncScheduler>() }
    single<net.bible.service.cloudsync.CloudSyncProvider> { net.bible.service.cloudsync.CloudSyncProvider { get() } }
    single<net.bible.service.cloudsync.DocumentSyncProvider> { net.bible.service.cloudsync.DocumentSyncProvider { get() } }
    single {
        net.bible.service.cloudsync.documents.DocumentSyncSettings(
            { net.bible.service.db.DatabaseContainer.instance.documentSyncDb.documentSyncPreferencesDao() },
            { net.bible.service.db.DatabaseContainer.instance.documentSyncDb.cloudListingStateDao() },
            { net.bible.service.db.DatabaseContainer.instance.documentSyncDb.cloudDocumentSyncTimestampDao() },
            get(), get(),
        )
    }
    single { net.bible.service.cloudsync.CloudSync(get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    single { net.bible.service.cloudsync.documents.DocumentSync(get(), get(), get(), get(), get(), { get() }) }
    single<DocumentSyncStarter> { AndroidDocumentSyncStarter(androidContext()) }
    single { BookInstallWatcher(get(), get(), get(), get(), get()) }
    single<UserNotifier> { AndroidUserNotifier() }
    single<MarkdownRenderer> { net.bible.service.sword.mydocument.MarkdownToXhtml }
    single<net.bible.sharedcore.platform.AppDirectories> { net.bible.android.AndroidAppDirectories() }
    single<net.bible.sharedlogic.platform.AddonCatalog> { net.bible.android.platform.AndroidAddonCatalog() }
    single<net.bible.sharedlogic.platform.PlatformLocales> { net.bible.android.platform.AndroidPlatformLocales() }
    single<net.bible.sharedcore.platform.AppEnvironment> { net.bible.android.platform.AndroidAppEnvironment(androidContext()) }
    single<CoreStrings> { AndroidCoreStrings(androidContext()) }
    single<net.bible.android.control.page.GlobalTextDisplaySettingsSource> {
        net.bible.android.platform.AndroidGlobalTextDisplaySettingsSource { get() }
    }
    single<net.bible.android.control.page.window.WindowSettingsPicker> {
        net.bible.android.platform.AndroidWindowSettingsPicker(get())
    }
    single<net.bible.android.control.page.window.SettingLabels> { net.bible.android.platform.AndroidSettingLabels() }
    single<net.bible.service.llm.LlmStrings> { net.bible.android.platform.AndroidLlmStrings(androidContext()) }
    single<net.bible.service.sword.HtmlToPlainText> {
        net.bible.service.sword.HtmlToPlainText { net.bible.service.common.htmlToSpan(it).toString() }
    }
    single<net.bible.service.sword.ContentSource> { net.bible.android.platform.AndroidContentSource(androidContext().contentResolver) }
    single { net.bible.service.sword.SwordContentFacade(get(), get(), get(), get(), get(), net.bible.android.platform.androidSaxTextPolicy()) }
    single<DateTimeFormats> { AndroidDateTimeFormats(androidContext()) }

    // @ApplicationScope singletons
    singleOf(::BibleTraverser)
    singleOf(::NavigationControl)
    single { net.bible.android.control.backup.BackupControl(get(), get(), get(), get(), get(), get(), get()) }
    singleOf(::WindowControl)
    singleOf(::WindowCommandsImpl) { bind<WindowCommands>() }
    singleOf(::WindowStateServiceImpl) { bind<WindowStateService>() }
    single { ToolbarStateServiceImpl(get(), get(), get(), get(), get(), get<net.bible.service.cloudsync.CloudSync>().runningChanged) } bind ToolbarStateService::class
    singleOf(::LinkControl)
    single<net.bible.android.control.page.KeyChooserLauncher> {
        net.bible.android.platform.AndroidKeyChooserLauncher { net.bible.android.view.activity.base.CurrentActivityHolder.currentActivity }
    }
    single<HistoryPlatform> { AndroidHistoryPlatform() }
    single<LinkPlatform> { AndroidLinkPlatform() }
    singleOf(::HistoryManager)
    singleOf(::HistoryTraversalFactory)
    single {
        DocumentControl(get(), get(), get(), get(), get(), get<AppCoroutineScope>(),
            deleteFiles = DocumentFileDeleter { SwordDocumentFacade.deleteDocument(it) },
            backupDaoProvider = { DatabaseContainer.instance.repoDb.swordDocumentInfoDao() })
    }
    single { net.bible.android.platform.androidSaxTextPolicy() }
    singleOf(::BookmarkControl)
    singleOf(::LabelEditServiceImpl) { bind<LabelEditService>() }
    singleOf(::ManageLabelsServiceImpl) { bind<ManageLabelsService>() }
    singleOf(::BookmarksServiceImpl) { bind<BookmarksService>() }
    singleOf(::PageControl)
    singleOf(::ReadingPlanControl)
    single { ReadingPlanRepository(daoProvider = { DatabaseContainer.instance.readingPlanDb.readingPlanDao() }, appScope = get()) }
    single<ReadingPlanSource> { AndroidReadingPlanSource() }
    single {
        ReadingPlanTextFileDao(
            source = get(), repository = get(), coreStrings = get(),
            userPlanFolder = { SharedConstants.manualReadingPlanDir },
            providedPlans = { AndBibleAddons.providedReadingPlans },
            // evaluated per lookup: ReadingPlanCatalog resolves its localized strings when first touched
            distributedPlans = { ReadingPlanCatalog.ABDistributedPlanDetailArray.map { DistributedPlanDetails(it.planCode, it.planName, it.planDescription) } },
        )
    }
    singleOf(::SearchControl)
    single<SearchTextHighlighter> { SearchHighlighter }
    singleOf(::BibleSearchServiceImpl) { bind<BibleSearchService>() }
    // F26: shared across SearchResultsComposeActivity recreations (history-revert Back) so returning
    // to results doesn't re-run the Lucene search.
    single { SearchResultsCache() }
    singleOf(::SearchIndexServiceImpl) { bind<SearchIndexService>() }
    singleOf(::AndroidEpubSearchService) { bind<EpubSearchService>() }
    singleOf(::SpeakSettingsServiceImpl) { bind<SpeakSettingsService>() }
    singleOf(::SpeakTransportServiceImpl) { bind<SpeakTransportService>() }
    singleOf(::WorkspaceServiceImpl) { bind<WorkspaceService>() }
    singleOf(::AiSettingsServiceImpl) { bind<AiSettingsService>() }
    single { net.bible.android.platform.AndroidHttpClients() }
    single<net.bible.sharedcore.llm.http.LlmHttpTransport> {
        net.bible.sharedcore.llm.http.KtorLlmHttpTransport(get<net.bible.android.platform.AndroidHttpClients>()::clientFor)
    }
    singleOf(::LlmProviderServiceImpl) { bind<LlmProviderService>() }
    singleOf(::LlmModelServiceImpl) { bind<LlmModelService>() }
    singleOf(::PromptServiceImpl) { bind<PromptService>() }
    singleOf(::ToolPermissionServiceImpl) { bind<ToolPermissionService>() }
    // Z-early B4: the runtime agent tool-permission prompt bridge. App-wide single (NOT
    // activity-scoped): AgentExecutor asks for permission from a foreground service's coroutine, so
    // the controller must outlive any single Activity — the awaiting request survives an activity
    // recreation, and whichever ComposeReadingViewHost is currently installed renders it.
    single { AgentPermissionController() }
    // Platform-dialog removal (spec D6): the app-wide queue of owner-less dialogs. App-scoped for the
    // same reason as AgentPermissionController above: a request must survive a host swap
    // (StartupActivity -> NavHostComposeActivity) and a service must be able to raise one.
    single { AppDialogController() }
    singleOf(::RawLogServiceImpl) { bind<RawLogService>() }
    singleOf(::ReadingLlmServiceImpl) { bind<ReadingLlmService>() }
    singleOf(::AgentSessionServiceImpl) { bind<AgentSessionService>() }
    singleOf(::DocumentFilterServiceImpl) { bind<DocumentFilterService>() }
    singleOf(::ReadingProgressServiceImpl) { bind<ReadingProgressService>() }
    singleOf(::ReadingProgressSettingsServiceImpl) { bind<ReadingProgressSettingsService>() }
    // Explicit (not singleOf): TextDisplaySettingsServiceImpl now takes an optional
    // DetachedWorkspaceEdit? constructor param, which singleOf would try (and fail) to resolve as
    // a Koin dependency. The shared singleton always gets the plain, non-detached instance; a
    // detached instance (workspace-selector settings edit) is constructed separately, never via Koin.
    // `single { ... } bind ...::class` (not `single<TextDisplaySettingsService> { ... }`) keeps the
    // CONCRETE class as the definition's primary type, with the interface as a secondary one --
    // TextDisplaySettingsComposeActivity injects the concrete TextDisplaySettingsServiceImpl (for
    // the HIDELABELS bridge helper, outside the portable interface), same reason as the
    // singleOf(::BookmarksServiceImpl) { bind<BookmarksService>() } pattern above.
    single { TextDisplaySettingsServiceImpl() } bind TextDisplaySettingsService::class
    // SpeakControl's constructor takes a kotlin.Lazy<TextToSpeechServiceManager>, which Koin
    // cannot resolve on its own (singleOf/verify special-case Lazy, but a real get() throws
    // NoDefinitionFoundException). Supply the Lazy wrapper explicitly. It resolves on every access rather
    // than memoizing: TextToSpeechServiceManager is a single, so production sees the same instance either
    // way, but a test that rebinds the manager (SpeakIntegrationTestBase) must reach the SpeakControl an
    // earlier test already created -- a memoized Lazy kept the old manager and hung those tests.
    single {
        val koin = getKoin()
        SpeakControl(object : Lazy<TextToSpeechServiceManager> {
            override val value: TextToSpeechServiceManager get() = koin.get()
            override fun isInitialized() = true
        }, get(), get(), get(), get(), get(), get(), get(), get(), get())
    }
    singleOf(::DocumentBibleBooksFactory)
    singleOf(::SpeakActionBarButton)
    singleOf(::SpeakStopActionBarButton)
    single<net.bible.android.control.speak.SpeakPlatform> { net.bible.android.platform.AndroidSpeakPlatform(androidContext()) }
    single { TextToSpeechServiceManager(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), { net.bible.service.db.DatabaseContainer.instance.myDocumentDb.myDocumentDao() }, get()) }

    // Unscoped @Inject-constructor classes
    factoryOf(::CurrentPageManager)
}

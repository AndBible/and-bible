package net.bible.android.control

import kotlinx.coroutines.flow.map
import net.bible.android.control.backup.BackupControl
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.control.bookmark.BookmarkJsActions
import net.bible.android.control.download.DownloadControl
import net.bible.android.control.link.LinkControl
import net.bible.android.control.navigation.DocumentBibleBooksFactory
import net.bible.android.control.navigation.NavigationControl
import net.bible.android.control.page.CurrentPageManager
import net.bible.android.control.page.PageControl
import net.bible.android.control.page.toolbar.ToolbarStateServiceImpl
import net.bible.android.control.page.window.WindowCommandsImpl
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.page.window.WindowStateServiceImpl
import net.bible.android.control.page.window.WorkspacePersistence
import net.bible.android.control.progress.ProgressJsActions
import net.bible.android.control.progress.ReadingProgressServiceImpl
import net.bible.android.control.readingplan.ReadingPlanControl
import net.bible.android.control.search.BibleSearchServiceImpl
import net.bible.android.control.search.SearchControl
import net.bible.android.control.search.SearchIndexServiceImpl
import net.bible.android.control.speak.AdvancedSpeakSettings
import net.bible.android.control.speak.SpeakControl
import net.bible.android.control.speak.SpeakSettingsServiceImpl
import net.bible.android.control.speak.SpeakTransportServiceImpl
import net.bible.android.control.versification.BibleTraverser
import net.bible.android.control.versification.BookInstallWatcher
import net.bible.android.view.activity.ai.AgentSessionServiceImpl
import net.bible.android.view.activity.ai.AiSettingsServiceImpl
import net.bible.android.view.activity.ai.DocumentFilterServiceImpl
import net.bible.android.view.activity.ai.LlmModelServiceImpl
import net.bible.android.view.activity.ai.LlmProviderServiceImpl
import net.bible.android.view.activity.ai.PromptServiceImpl
import net.bible.android.view.activity.ai.RawLogServiceImpl
import net.bible.android.view.activity.ai.ReadingLlmServiceImpl
import net.bible.android.view.activity.ai.ToolPermissionServiceImpl
import net.bible.android.view.activity.bookmark.BookmarksServiceImpl
import net.bible.android.view.activity.bookmark.LabelEditServiceImpl
import net.bible.android.view.activity.bookmark.ManageLabelsServiceImpl
import net.bible.android.view.activity.settings.ReadingProgressSettingsServiceImpl
import net.bible.android.view.activity.settings.SyncSettingsServiceImpl
import net.bible.sharedcore.settings.ReadingProgressSettingsService
import net.bible.android.view.activity.workspaces.WorkspaceServiceImpl
import net.bible.service.cloudsync.CloudSync
import net.bible.service.cloudsync.CloudSyncProvider
import net.bible.service.cloudsync.DocumentSyncProvider
import net.bible.service.cloudsync.SyncSettingsCategories
import net.bible.service.cloudsync.documents.DocumentSync
import net.bible.service.cloudsync.documents.DocumentSyncSettings
import net.bible.service.common.GlobalTextDisplaySettingsStore
import net.bible.service.common.ReadingProgressSettingsStore
import net.bible.service.common.RecentDocumentsStore
import net.bible.service.db.DatabaseContainer
import net.bible.service.db.readingplan.ReadingPlanRepository
import net.bible.service.device.speak.TextToSpeechServiceManager
import net.bible.service.download.CustomRepositoryServiceImpl
import net.bible.service.download.FakeBookFactory
import net.bible.service.download.KtorManifestFetcher
import net.bible.service.download.ManifestFetcher
import net.bible.service.history.HistoryManager
import net.bible.service.history.HistoryTraversalFactory
import net.bible.service.llm.AiSettings
import net.bible.service.llm.AiSettingsStore
import net.bible.sharedcore.ai.AgentPermissionController
import net.bible.sharedcore.ai.AiSettingsService
import net.bible.sharedcore.ai.DocumentFilterService
import net.bible.sharedcore.ai.LlmModelService
import net.bible.sharedcore.ai.LlmProviderService
import net.bible.sharedcore.ai.PromptService
import net.bible.sharedcore.ai.RawLogService
import net.bible.sharedcore.ai.ToolPermissionService
import net.bible.sharedcore.ai.reading.AgentSessionService
import net.bible.sharedcore.ai.reading.ReadingLlmService
import net.bible.sharedcore.bookmark.BookmarksService
import net.bible.sharedcore.bookmark.LabelEditService
import net.bible.sharedcore.bookmark.ManageLabelsService
import net.bible.sharedcore.download.CustomRepositoryService
import net.bible.sharedcore.platform.AppCoroutineScope
import net.bible.sharedcore.platform.OrderedLauncher
import net.bible.sharedcore.progress.ReadingProgressService
import net.bible.sharedcore.reading.ToolbarStateService
import net.bible.sharedcore.search.BibleSearchService
import net.bible.sharedcore.search.EpubSearchService
import net.bible.sharedcore.search.SearchIndexService
import net.bible.sharedcore.search.SearchResultsCache
import net.bible.sharedcore.speak.SpeakSettingsService
import net.bible.sharedcore.speak.SpeakTransportService
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.sharedcore.window.WindowCommands
import net.bible.sharedcore.window.WindowStateService
import net.bible.sharedcore.workspaces.WorkspaceService
import net.bible.android.control.document.DocumentControl
import net.bible.android.control.document.DocumentFileDeleter
import net.bible.android.view.activity.search.AndroidEpubSearchService
import net.bible.service.format.SaxTextPolicy
import net.bible.service.readingplan.ReadingPlanTextFileDao
import net.bible.service.sword.SwordContentFacade
import net.bible.service.sword.SwordDocumentFacade
import net.bible.sharedcore.platform.AppDirectories
import net.bible.sharedlogic.platform.AddonCatalog
import net.bible.sharedlogic.platform.DistributedPlanCatalog
import net.bible.sharedlogic.platform.ManualInstallFolderEnabler
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * The bindings whose every class lives in a move-readiness domain or a shared module; phase 2 moves this file to
 * `:sharedLogic`. Builds a NEW Koin `Module` on every call (see [buildCoreModule]).
 */
fun buildSharedBindings() = module {
    // DownloadControl (was ApplicationModule.provideDownloadControl)
    single { FakeBookFactory(get()) }
    single { DownloadControl(get(), get(), get()) }
    single { SwordContentFacade(get(), get(), get(), get(), get(), get<SaxTextPolicy>()) }
    single {
        ReadingPlanTextFileDao(
            source = get(), repository = get(), coreStrings = get(), calendar = get(),
            userPlanFolder = { get<AppDirectories>().manualReadingPlanDir },
            providedPlans = { get<AddonCatalog>().readingPlans },
            // evaluated per lookup: the platform catalog resolves its localized strings when first touched
            distributedPlans = { get<DistributedPlanCatalog>().plans() },
        )
    }
    single {
        DocumentControl(get(), get(), get(), get(), get(), get<AppCoroutineScope>(),
            deleteFiles = DocumentFileDeleter { SwordDocumentFacade.deleteDocument(it) },
            backupDaoProvider = { DatabaseContainer.instance.repoDb.swordDocumentInfoDao() },
            enableManualInstallFolder = { get<ManualInstallFolderEnabler>().enable() })
    }
    single { AndroidEpubSearchService() } bind EpubSearchService::class
    single<ManifestFetcher> { KtorManifestFetcher(get()) }
    singleOf(::CustomRepositoryServiceImpl) { bind<CustomRepositoryService>() }

    single { AdvancedSpeakSettings(get()) }
    single { RecentDocumentsStore(get()) }
    single { AppCoroutineScope() }
    single { OrderedLauncher(get<AppCoroutineScope>()) }
    single {
        GlobalTextDisplaySettingsStore(
            get(),
            { DatabaseContainer.instance.workspaceDb.globalTextDisplaySettingsDao() },
            DatabaseContainer.databaseRestored,
            get<CloudSync>().workspaceRefreshRequired,
            get<AppCoroutineScope>(),
            { DatabaseContainer.instance.workspaceDb.invalidationTracker.createFlow("GlobalTextDisplaySettings").map { Unit } },
        )
    }
    single {
        ReadingProgressSettingsStore(
            get(),
            { DatabaseContainer.instance.progressDb.globalReadingProgressSettingsDao() },
            DatabaseContainer.databaseRestored,
            get<CloudSync>().workspaceRefreshRequired,
            get<AppCoroutineScope>(),
            { DatabaseContainer.instance.progressDb.invalidationTracker.createFlow("GlobalReadingProgressSettings").map { Unit } },
        )
    }
    single {
        WorkspacePersistence(get()) {
            // The DAO is resolved per write: DatabaseContainer.reset()/restore replaces workspaceDb.
            DatabaseContainer.instance.workspaceDb.workspaceDao()
        }
    }
    single<AiSettings> {
        AiSettingsStore(
            { DatabaseContainer.instance.aiSettingsDb.globalAiSettingsDao() }, get(), get(), get(),
            DatabaseContainer.databaseRestored, get<CloudSync>().workspaceRefreshRequired,
            DatabaseContainer.aiLifetime,
            { DatabaseContainer.instance.aiSettingsDb.invalidationTracker.createFlow("GlobalAiSettings").map { Unit } },
        )
    }
    single { ProgressJsActions(get()) }
    single { BookmarkJsActions(get(), get()) }
    single { SyncSettingsCategories(get()) }
    singleOf(::ReadingProgressSettingsServiceImpl) { bind<ReadingProgressSettingsService>() }
    factory { (scope: kotlinx.coroutines.CoroutineScope) ->
        SyncSettingsServiceImpl(
            scope, get<AppCoroutineScope>(), get(), get(), get(), get(), get(), get(), get(), get(), get(),
            { DatabaseContainer.notifyDatabaseRestored() },
        )
    }
    single<CloudSyncProvider> { CloudSyncProvider { get() } }
    single<DocumentSyncProvider> { DocumentSyncProvider { get() } }
    single {
        DocumentSyncSettings(
            { DatabaseContainer.instance.documentSyncDb.documentSyncPreferencesDao() },
            { DatabaseContainer.instance.documentSyncDb.cloudListingStateDao() },
            { DatabaseContainer.instance.documentSyncDb.cloudDocumentSyncTimestampDao() },
            get(), get(),
        )
    }
    single { CloudSync(get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    single { DocumentSync(get(), get(), get(), get(), get(), { get() }) }
    single { BookInstallWatcher(get(), get(), get(), get(), get()) }

    // @ApplicationScope singletons
    singleOf(::BibleTraverser)
    singleOf(::NavigationControl)
    single { BackupControl(get(), get(), get(), get(), get(), get(), get()) }
    singleOf(::WindowControl)
    singleOf(::WindowCommandsImpl) { bind<WindowCommands>() }
    singleOf(::WindowStateServiceImpl) { bind<WindowStateService>() }
    single { ToolbarStateServiceImpl(get(), get(), get(), get(), get(), get<CloudSync>().runningChanged) } bind ToolbarStateService::class
    singleOf(::LinkControl)
    singleOf(::HistoryManager)
    singleOf(::HistoryTraversalFactory)
    singleOf(::BookmarkControl)
    singleOf(::LabelEditServiceImpl) { bind<LabelEditService>() }
    singleOf(::ManageLabelsServiceImpl) { bind<ManageLabelsService>() }
    singleOf(::BookmarksServiceImpl) { bind<BookmarksService>() }
    singleOf(::PageControl)
    singleOf(::ReadingPlanControl)
    single { ReadingPlanRepository(daoProvider = { DatabaseContainer.instance.readingPlanDb.readingPlanDao() }, appScope = get(), calendar = get()) }
    singleOf(::SearchControl)
    singleOf(::BibleSearchServiceImpl) { bind<BibleSearchService>() }
    // F26: shared across SearchResultsComposeActivity recreations (history-revert Back) so returning
    // to results doesn't re-run the Lucene search.
    single { SearchResultsCache() }
    singleOf(::SearchIndexServiceImpl) { bind<SearchIndexService>() }
    singleOf(::SpeakSettingsServiceImpl) { bind<SpeakSettingsService>() }
    singleOf(::SpeakTransportServiceImpl) { bind<SpeakTransportService>() }
    singleOf(::WorkspaceServiceImpl) { bind<WorkspaceService>() }
    singleOf(::AiSettingsServiceImpl) { bind<AiSettingsService>() }
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
    single { TextToSpeechServiceManager(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), { DatabaseContainer.instance.myDocumentDb.myDocumentDao() }, get()) }

    // Unscoped @Inject-constructor classes
    factoryOf(::CurrentPageManager)
}

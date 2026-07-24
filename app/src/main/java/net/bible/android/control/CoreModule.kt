package net.bible.android.control

import net.bible.android.common.resource.AndroidResourceProvider
import net.bible.android.common.resource.ResourceProvider
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.control.document.DocumentControl
import net.bible.android.control.download.DownloadControl
import net.bible.android.control.download.DownloadQueue
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
import net.bible.android.control.progress.ReadingProgressServiceImpl
import net.bible.android.control.readingplan.ReadingPlanControl
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
import net.bible.android.view.activity.readingplan.actionbar.ReadingPlanActionBarManager
import net.bible.android.view.activity.readingplan.actionbar.ReadingPlanBibleActionBarButton
import net.bible.android.view.activity.readingplan.actionbar.ReadingPlanCommentaryActionBarButton
import net.bible.android.view.activity.readingplan.actionbar.ReadingPlanDictionaryActionBarButton
import net.bible.android.view.activity.readingplan.actionbar.ReadingPlanPauseActionBarButton
import net.bible.android.view.activity.readingplan.actionbar.ReadingPlanStopActionBarButton
import net.bible.android.view.activity.readingplan.actionbar.ReadingPlanTitle
import net.bible.android.view.activity.search.searchresultsactionbar.ScriptureToggleActionBarButton
import net.bible.android.view.activity.search.searchresultsactionbar.SearchResultsActionBarManager
import net.bible.android.view.activity.speak.actionbarbuttons.SpeakActionBarButton
import net.bible.android.view.activity.speak.actionbarbuttons.SpeakStopActionBarButton
import net.bible.service.db.readingplan.ReadingPlanRepository
import net.bible.service.device.speak.TextToSpeechServiceManager
import net.bible.service.history.HistoryManager
import net.bible.service.history.HistoryTraversalFactory
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val coreModule = module {
    // ResourceProvider interface binding (was ApplicationModule.provideResourceProvider)
    singleOf(::AndroidResourceProvider) { bind<ResourceProvider>() }
    // DownloadControl (was ApplicationModule.provideDownloadControl)
    single { DownloadControl(DownloadQueue()) }

    // @ApplicationScope singletons
    singleOf(::BibleTraverser)
    singleOf(::NavigationControl)
    singleOf(::WindowControl)
    singleOf(::WindowCommandsImpl) { bind<WindowCommands>() }
    singleOf(::WindowStateServiceImpl) { bind<WindowStateService>() }
    singleOf(::ToolbarStateServiceImpl) { bind<ToolbarStateService>() }
    singleOf(::LinkControl)
    singleOf(::HistoryManager)
    singleOf(::HistoryTraversalFactory)
    singleOf(::DocumentControl)
    singleOf(::BookmarkControl)
    singleOf(::LabelEditServiceImpl) { bind<LabelEditService>() }
    singleOf(::ManageLabelsServiceImpl) { bind<ManageLabelsService>() }
    singleOf(::BookmarksServiceImpl) { bind<BookmarksService>() }
    singleOf(::PageControl)
    singleOf(::ReadingPlanControl)
    singleOf(::ReadingPlanRepository)
    singleOf(::SearchControl)
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
    singleOf(::LlmProviderServiceImpl) { bind<LlmProviderService>() }
    singleOf(::LlmModelServiceImpl) { bind<LlmModelService>() }
    singleOf(::PromptServiceImpl) { bind<PromptService>() }
    singleOf(::ToolPermissionServiceImpl) { bind<ToolPermissionService>() }
    singleOf(::RawLogServiceImpl) { bind<RawLogService>() }
    singleOf(::ReadingLlmServiceImpl) { bind<ReadingLlmService>() }
    singleOf(::AgentSessionServiceImpl) { bind<AgentSessionService>() }
    singleOf(::DocumentFilterServiceImpl) { bind<DocumentFilterService>() }
    singleOf(::ReadingProgressServiceImpl) { bind<ReadingProgressService>() }
    singleOf(::ReadingProgressSettingsServiceImpl) { bind<ReadingProgressSettingsService>() }
    singleOf(::TextDisplaySettingsServiceImpl) { bind<TextDisplaySettingsService>() }
    // SpeakControl's constructor takes a kotlin.Lazy<TextToSpeechServiceManager>, which Koin
    // cannot resolve on its own (singleOf/verify special-case Lazy, but a real get() throws
    // NoDefinitionFoundException). Supply the Lazy wrapper explicitly.
    single { SpeakControl(lazy { get<TextToSpeechServiceManager>() }, get()) }
    singleOf(::DocumentBibleBooksFactory)
    singleOf(::ReadingPlanActionBarManager)
    singleOf(::ReadingPlanBibleActionBarButton)
    singleOf(::ReadingPlanCommentaryActionBarButton)
    singleOf(::ReadingPlanDictionaryActionBarButton)
    singleOf(::ReadingPlanPauseActionBarButton)
    singleOf(::ReadingPlanStopActionBarButton)
    singleOf(::ReadingPlanTitle)
    singleOf(::ScriptureToggleActionBarButton)
    singleOf(::SearchResultsActionBarManager)
    singleOf(::SpeakActionBarButton)
    singleOf(::SpeakStopActionBarButton)
    singleOf(::TextToSpeechServiceManager)

    // Unscoped @Inject-constructor classes
    factoryOf(::CurrentPageManager)
}

package net.bible.android.control

import net.bible.android.common.resource.AndroidResourceProvider
import net.bible.android.common.resource.ResourceProvider
import net.bible.android.control.search.SearchHighlighter
import net.bible.android.control.search.SearchTextHighlighter
import net.bible.android.view.activity.search.AndroidEpubSearchService
import net.bible.sharedcore.search.EpubSearchService
import net.bible.android.view.activity.settings.ReadingProgressSettingsServiceImpl
import net.bible.sharedcore.settings.ReadingProgressSettingsService
import net.bible.android.view.activity.settings.TextDisplaySettingsServiceImpl
import net.bible.sharedcore.settings.TextDisplaySettingsService
import net.bible.android.platform.AndroidDocumentSyncStarter
import net.bible.sharedcore.cloud.DocumentSyncStarter
import net.bible.android.view.activity.speak.actionbarbuttons.SpeakActionBarButton
import net.bible.android.view.activity.speak.actionbarbuttons.SpeakStopActionBarButton
import net.bible.android.platform.AndroidReadingPlanSource
import net.bible.android.platform.AndroidHistoryPlatform
import net.bible.android.platform.AndroidLinkPlatform
import net.bible.android.control.link.LinkPlatform
import net.bible.android.view.activity.readingplan.ReadingPlanCatalog
import net.bible.service.readingplan.DistributedPlanDetails
import net.bible.service.readingplan.ReadingPlanTextFileDao
import net.bible.sharedcore.readingplan.ReadingPlanSource
import net.bible.service.common.CommonUtils
import okio.Path.Companion.toOkioPath
import net.bible.android.SharedConstants
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.platform.CoreStrings
import net.bible.service.sword.mydocument.MarkdownToXhtml
import net.bible.sharedcore.platform.DateTimeFormats
import net.bible.sharedcore.platform.MarkdownRenderer
import net.bible.sharedcore.platform.UserNotifier
import net.bible.android.platform.AndroidCoreStrings
import net.bible.android.platform.AndroidDateTimeFormats
import net.bible.android.platform.AndroidUserNotifier
import org.koin.android.ext.koin.androidContext
import net.bible.service.history.HistoryPlatform
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * The bindings that stay in `:app`: everything that needs an Android context, an Android class or a non-domain view class.
 * Builds a NEW Koin `Module` on every call (see [buildCoreModule]).
 */
fun buildPlatformBindings() = module {
    // ResourceProvider interface binding (was ApplicationModule.provideResourceProvider)
    singleOf(::AndroidResourceProvider) { bind<ResourceProvider>() }
    single<net.bible.sharedcore.http.DownloadHttp> {
        net.bible.sharedcore.http.KtorDownloadHttp(get<net.bible.android.platform.AndroidHttpClients>().downloadClient) {
            org.crosswire.common.util.WebResource.getTimeout()
        }
    }
    single<net.bible.android.control.speak.SpeakSettingsStore> { net.bible.android.platform.AndroidSpeakSettingsStore() }

    single<AppSettings> { CommonUtils.settings }
    single<MarkdownRenderer> { MarkdownToXhtml }
    single<net.bible.sharedcore.platform.secrets.SecretStore> { net.bible.android.platform.AndroidSecretStore(CommonUtils.realSharedPreferences) }
    single<net.bible.service.llm.agent.AgentPlatform> { net.bible.android.platform.AndroidAgentPlatform() }
    single<net.bible.service.cloudsync.SyncSettingsPlatform> { net.bible.android.platform.AndroidSyncSettingsPlatform(androidContext()) }
    single<net.bible.service.cloudsync.CloudAdapterFactory> { net.bible.android.platform.AndroidCloudAdapterFactory(androidContext()) }
    single { net.bible.android.platform.AndroidSyncScheduler(androidContext()) }
    single<net.bible.sharedcore.platform.sync.SyncScheduler> { get<net.bible.android.platform.AndroidSyncScheduler>() }
    single<DocumentSyncStarter> { AndroidDocumentSyncStarter(androidContext()) }
    single<UserNotifier> { AndroidUserNotifier() }
    single<net.bible.sharedcore.platform.AppDirectories> { net.bible.android.AndroidAppDirectories() }
    single<net.bible.sharedlogic.platform.AddonCatalog> { net.bible.android.platform.AndroidAddonCatalog() }
    single<net.bible.sharedlogic.platform.PlatformLocales> { net.bible.android.platform.AndroidPlatformLocales() }
    single<net.bible.sharedlogic.platform.ReadOnlyModuleDbOpener> { net.bible.android.platform.AndroidReadOnlyModuleDbOpener() }
    single<net.bible.sharedlogic.platform.SearchIndexScheduler> { net.bible.android.platform.AndroidSearchIndexScheduler(androidContext()) }
    single<net.bible.sharedlogic.platform.LocalCalendar> { net.bible.android.platform.AndroidLocalCalendar() }
    // G3 ports (LocalCalendar is bound above, G1 + G3 in one class)
    single<net.bible.sharedlogic.platform.FirstLoadEdge> { net.bible.android.platform.AndroidFirstLoadEdge }
    single<net.bible.sharedlogic.platform.EpubStoreEdge> { net.bible.android.platform.AndroidEpubStoreEdge }
    single<net.bible.sharedlogic.platform.MyDocumentStoreEdge> { net.bible.android.platform.AndroidMyDocumentStoreEdge }
    single<net.bible.sharedlogic.platform.PromptPackSource> { net.bible.android.platform.AndroidPromptPackSource }
    single<net.bible.sharedlogic.platform.LocaleSupport> { net.bible.android.platform.AndroidLocaleSupport }
    single<net.bible.sharedlogic.platform.SentenceBreaker> { net.bible.android.platform.AndroidSentenceBreaker }
    single<net.bible.sharedlogic.platform.NightModeState> { net.bible.android.platform.AndroidNightModeState }
    single<net.bible.sharedlogic.platform.LabelNameStrings> { net.bible.android.platform.AndroidLabelNameStrings(androidContext()) }
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
    single<net.bible.android.control.page.KeyChooserLauncher> {
        net.bible.android.platform.AndroidKeyChooserLauncher { net.bible.android.view.activity.base.CurrentActivityHolder.currentActivity }
    }
    single<HistoryPlatform> { AndroidHistoryPlatform() }
    single<LinkPlatform> { AndroidLinkPlatform() }
    single { net.bible.android.platform.androidSaxTextPolicy() }
    single<ReadingPlanSource> { AndroidReadingPlanSource() }
    single {
        ReadingPlanTextFileDao(
            source = get(), repository = get(), coreStrings = get(), calendar = get(),
            userPlanFolder = { SharedConstants.manualReadingPlanDir.toOkioPath() },
            providedPlans = { get<net.bible.sharedlogic.platform.AddonCatalog>().readingPlans },
            // evaluated per lookup: ReadingPlanCatalog resolves its localized strings when first touched
            distributedPlans = { ReadingPlanCatalog.ABDistributedPlanDetailArray.map { DistributedPlanDetails(it.planCode, it.planName, it.planDescription) } },
        )
    }
    single<SearchTextHighlighter> { SearchHighlighter }
    singleOf(::AndroidEpubSearchService) { bind<EpubSearchService>() }
    single { net.bible.android.platform.AndroidHttpClients() }
    single<net.bible.sharedcore.llm.http.LlmHttpTransport> {
        net.bible.sharedcore.llm.http.KtorLlmHttpTransport(get<net.bible.android.platform.AndroidHttpClients>()::clientFor)
    }
    singleOf(::ReadingProgressSettingsServiceImpl) { bind<ReadingProgressSettingsService>() }
    // Explicit (not singleOf): TextDisplaySettingsServiceImpl now takes an optional
    // DetachedWorkspaceEdit? constructor param, which singleOf would try (and fail) to resolve as
    // a Koin dependency. The shared singleton always gets the plain, non-detached instance; a
    // detached instance (workspace-selector settings edit) is constructed separately, never via Koin.
    // `single { ... } bind ...::class` (not `single<TextDisplaySettingsService> { ... }`) keeps the
    // CONCRETE class as the definition's primary type, with the interface as a secondary one --
    // TextDisplaySettingsComposeActivity injects the concrete TextDisplaySettingsServiceImpl (for
    // the HIDELABELS bridge helper, outside the portable interface), same reason as the
    // singleOf(::BookmarksServiceImpl) { bind<BookmarksService>() } pattern (that binding lives in SharedBindings.kt).
    single { TextDisplaySettingsServiceImpl() } bind TextDisplaySettingsService::class
    singleOf(::SpeakActionBarButton)
    singleOf(::SpeakStopActionBarButton)
    single<net.bible.android.control.speak.SpeakPlatform> { net.bible.android.platform.AndroidSpeakPlatform(androidContext()) }
}

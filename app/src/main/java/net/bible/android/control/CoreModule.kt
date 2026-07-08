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
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.readingplan.ReadingPlanControl
import net.bible.android.control.search.SearchControl
import net.bible.android.control.speak.SpeakControl
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
    singleOf(::LinkControl)
    singleOf(::HistoryManager)
    singleOf(::HistoryTraversalFactory)
    singleOf(::DocumentControl)
    singleOf(::BookmarkControl)
    singleOf(::PageControl)
    singleOf(::ReadingPlanControl)
    singleOf(::ReadingPlanRepository)
    singleOf(::SearchControl)
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

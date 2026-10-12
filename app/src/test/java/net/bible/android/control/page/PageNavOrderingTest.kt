package net.bible.android.control.page

import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import java.lang.ref.WeakReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.bible.android.TestBibleApplication
import net.bible.android.control.page.window.WindowRepository
import net.bible.android.database.BookmarkDatabase
import net.bible.android.database.bookmarks.BookmarkDao
import net.bible.android.database.bookmarks.BookmarkEntities.Label
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.page.*
import net.bible.android.view.activity.page.screen.DocumentViewManager
import net.bible.android.view.activity.page.screen.PageTiltScroller
import net.bible.service.common.CommonUtils
import net.bible.service.db.DatabaseContainer
import net.bible.service.download.FakeBookFactory
import net.bible.service.sword.StudyPadKey
import net.bible.sharedcore.platform.OrderedLauncher
import net.bible.test.DatabaseResetter
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.versification.system.Versifications
import org.junit.Assert.assertEquals
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.dsl.module
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Removing the ordered launch in either real caller loses a StudyPad step while its DAO is held. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [35])
class PageNavOrderingTest {
    class NavigationTestActivity : ActivityBase()

    private val testDispatcher = StandardTestDispatcher()

    @Before fun setUpMain() {
        Dispatchers.setMain(testDispatcher)
    }

    @After fun tearDownMain() {
        // runTest must finish its children before Main returns to the blocked Robolectric looper.
        Dispatchers.resetMain()
        DatabaseResetter.resetDatabase()
    }

    @Test fun twoRapidNextsInAStudyPadMoveTwoLabelsInOrder() = navigation { commands, _, page, labels ->
        commands.next()
        commands.next()
        labels[2].id to { (page.currentGeneralBook.key as StudyPadKey).label.id }
    }

    @Test fun swipeAndJavascriptShareTheWindowQueue() = navigation { commands, bridge, page, labels ->
        commands.next()
        bridge.goToNextChapter()
        labels[2].id to { (page.currentGeneralBook.key as StudyPadKey).label.id }
    }

    @Test fun twoRapidPrevioussMoveTwoLabelsInOrder() = navigation(startAtEnd = true) { commands, bridge, page, labels ->
        commands.previous()
        bridge.goToPreviousChapter()
        labels[0].id to { (page.currentGeneralBook.key as StudyPadKey).label.id }
    }

    @Test fun contentIsAwaitableAtThePageInterface() = navigation(commentary = true) { _, _, page, _ ->
        val content = page.currentPage.currentPageContent()
        MultiFragmentDocument::class.java to { content.javaClass }
    }

    @Test fun commentaryNextTwiceAdvancesTwoVerses() = navigation(commentary = true) { commands, bridge, page, _ ->
        val start = (page.currentCommentary.key as Verse).ordinal
        commands.next()
        bridge.goToNextChapter()
        start + 2 to { (page.currentCommentary.key as Verse).ordinal }
    }

    @Test fun queuedSwipeDoesNotNavigateTheNewlySelectedPage() = navigation(selectionChanged = true) { commands, _, page, _ ->
        commands.next()
        selectCommentary(page)
        val selected = (page.currentCommentary.key as Verse).ordinal
        selected to { (page.currentCommentary.key as Verse).ordinal }
    }

    @Test fun queuedJavascriptDoesNotNavigateTheNewlySelectedPage() = navigation(selectionChanged = true) { _, bridge, page, _ ->
        bridge.goToNextChapter()
        selectCommentary(page)
        val selected = (page.currentCommentary.key as Verse).ordinal
        selected to { (page.currentCommentary.key as Verse).ordinal }
    }

    @Test fun heldNextDoesNotOverwriteAManuallySelectedLabel() = navigation(selectionChanged = true) { commands, _, page, labels ->
        commands.next()
        runCurrent()
        page.setCurrentDocumentAndKey(GlobalContext.get().get<FakeBookFactory>().journalDocument, StudyPadKey(labels[2]))
        labels[2].id to { (page.currentGeneralBook.key as StudyPadKey).label.id }
    }

    @Test fun heldPreviousDoesNotOverwriteAManuallySelectedLabel() = navigation(startAtEnd = true, selectionChanged = true) { _, bridge, page, labels ->
        bridge.goToPreviousChapter()
        runCurrent()
        page.setCurrentDocumentAndKey(GlobalContext.get().get<FakeBookFactory>().journalDocument, StudyPadKey(labels[0]))
        labels[0].id to { (page.currentGeneralBook.key as StudyPadKey).label.id }
    }

    @Test fun heldNextDoesNotOverwriteASwitchedDocument() = navigation(selectionChanged = true) { commands, _, page, labels ->
        commands.next()
        runCurrent()
        selectCommentary(page)
        labels[0].id to { (page.currentGeneralBook.key as StudyPadKey).label.id }
    }

    @Test fun heldNextDoesNotOverwriteANewDocumentOnTheSamePage() = navigation(selectionChanged = true) { commands, _, page, _ ->
        commands.next()
        runCurrent()
        val selected = net.bible.service.sword.BookAndKeyList()
        page.setCurrentDocumentAndKey(GlobalContext.get().get<FakeBookFactory>().multiDocument, selected)
        selected to { page.currentGeneralBook.key!! }
    }

    private fun selectCommentary(page: CurrentPageManager) {
        page.setCurrentDocumentAndKey(GlobalContext.get().get<FakeBookFactory>().compareDocument,
            Verse(Versifications.instance().getVersification("KJV"), org.crosswire.jsword.versification.BibleBook.GEN, 1, 1))
    }

    private fun navigation(
        startAtEnd: Boolean = false,
        commentary: Boolean = false,
        selectionChanged: Boolean = false,
        enqueue: suspend kotlinx.coroutines.test.TestScope.(ReadingCommands, BibleJavascriptInterface, CurrentPageManager, List<Label>) -> Pair<Any, () -> Any>,
    ) = runTest(testDispatcher, timeout = 30.seconds) {
        val koin = GlobalContext.get()
        val originalLauncher = koin.get<OrderedLauncher>()
        val control = CommonUtils.windowControl
        val originalRepo = control.windowRepository
        var originalDb: BookmarkDatabase? = null
        val actor = OrderedLauncher(this)
        val activity = Robolectric.buildActivity(NavigationTestActivity::class.java)
        val repoJob = SupervisorJob(coroutineContext[Job])
        val repo = WindowRepository(CoroutineScope(coroutineContext + repoJob))
        var view: BibleView? = null
        val gate = CompletableDeferred<Unit>()
        try {
            koin.loadModules(listOf(module { single<OrderedLauncher> { actor } }))
            activity.create()
            control.windowRepository = repo
            repo.initialize()
            val window = repo.activeWindow
            val page = window.pageManager
            val labels = listOf("A", "B", "C").map { page.bookmarkControl.insertOrUpdateLabel(Label(name = it, new = true)) }
            val factory = koin.get<FakeBookFactory>()
            if (commentary) {
                page.setCurrentDocumentAndKey(factory.compareDocument, Verse(Versifications.instance().getVersification("KJV"), org.crosswire.jsword.versification.BibleBook.GEN, 1, 1))
            } else {
                page.setCurrentDocumentAndKey(factory.journalDocument, StudyPadKey(if (startAtEnd) labels[2] else labels[0]))
            }
            val host = mock<ReadingHostActivity>()
            whenever(host.hostContext).thenReturn(RuntimeEnvironment.getApplication())
            val callbacks = BibleViewHostCallbacks(
                hostActivity = activity.get(), onNext = {}, onPrevious = {}, showLlmPromptSelector = { _, _ -> },
                composeSearchIfHosted = { _, _ -> false }, composeOpenDrawerIfHosted = { false },
                openDrawerAndFocusIt = {}, composeReadingViewHost = { null }, showRegenerate = { _, _ -> },
                crashAllBibleViews = {}, currentNightMode = { false }, imeHeight = { 0 },
                topOffset2 = { 0 }, bottomOffsetForWebView = { 0 }, insetsChanges = { net.bible.sharedcore.event.EventSource<OffsetsChange>() })
            view = BibleView(host, callbacks, WeakReference(window), control,
                koin.get(), PageTiltScrollControl(), koin.get(), koin.get(), koin.get(), koin.get())
            BibleView::class.java.getDeclaredField("pageTiltScroller").apply {
                isAccessible = true
                set(view, PageTiltScroller(view, PageTiltScrollControl()))
            }
            val factoryForSwipe = BibleViewFactory(host, callbacks)
            BibleViewFactory::class.java.getDeclaredField("windowBibleViewMap").apply {
                isAccessible = true
                @Suppress("UNCHECKED_CAST")
                (get(factoryForSwipe) as MutableMap<net.bible.android.database.IdType, BibleView>)[window.id] = view
            }
            val manager = DocumentViewManager(factoryForSwipe) {}
            val commandCallbacks = ReadingCommandsHostCallbacks(
                hostActivity = activity.get(), composeReadingViewHost = { null }, windowRepository = { repo },
                readingInsets = { error("unused") }, documentViewManager = { manager }, currentNightMode = { false },
                transportBarVisible = { false }, setTransportBarVisible = {}, updateBottomBars = {}, updateTitle = {},
                onToolbarStateMayHaveChanged = {}, requestSdcardPermission = {}, updateStrongsButton = {},
                toggleNativeDrawer = {}, openNativeDrawerAndFocusIt = {})
            val commands = ReadingCommands(host, commandCallbacks)
            val bridge = BibleJavascriptInterface(view)
            originalDb = DatabaseContainer.instance.bookmarkDb
            val realDao = originalDb.bookmarkDao()
            val sortedLabels = realDao.allLabelsSortedByName()
            assertEquals(labels.map { it.id }, sortedLabels.filter { !it.isSpecialLabel }.map { it.id })
            val heldDao = object : BookmarkDao by realDao {
                override suspend fun allLabelsSortedByName(): List<Label> {
                    gate.await()
                    return sortedLabels
                }
            }
            val heldDb = mock<BookmarkDatabase>()
            whenever(heldDb.bookmarkDao()).thenReturn(heldDao)
            DatabaseContainer.instance.bookmarkDb = heldDb
            val (expected, finalKey) = enqueue(commands, bridge, page, labels)
            runCurrent()
            if (!commentary && !selectionChanged) {
                assertEquals(if (startAtEnd) labels[2].id else labels[0].id, (page.currentGeneralBook.key as StudyPadKey).label.id)
            }
            gate.complete(Unit)
            actor.join("page-nav-${window.id}")
            advanceUntilIdle()
            if (!commentary && expected in labels.map { it.id }) {
                assertEquals(labels.single { it.id == expected }.name, (page.currentGeneralBook.key as StudyPadKey).label.name)
            }
            assertEquals(expected, finalKey())
        } finally {
            gate.complete(Unit)
            try {
                // Window loads run on IO and may still be returning to Main after navigation finishes.
                repoJob.cancelAndJoin()
                repo.windowList.forEach { actor.join("page-nav-${it.id}") }
                advanceUntilIdle()
                repo.windowList.forEach { it.destroy(destroyView = false) }
                view?.listenEvents = false
                view?.destroy()
                org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
                activity.close()
            } finally {
                originalDb?.let { DatabaseContainer.instance.bookmarkDb = it }
                control.windowRepository = originalRepo
                koin.loadModules(listOf(module { single<OrderedLauncher> { originalLauncher } }))
            }
        }
    }
}

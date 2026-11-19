package net.bible.service.history

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.control.page.CurrentPageManager
import net.bible.android.control.page.GlobalTextDisplaySettingsSource
import net.bible.android.control.page.window.Window
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.page.window.WindowRepository
import net.bible.android.control.page.window.WindowStateServiceImpl
import net.bible.android.control.versification.BibleTraverser
import net.bible.android.database.IdType
import net.bible.android.database.WorkspaceEntities
import net.bible.service.common.RecentDocumentsStore
import net.bible.service.download.FakeBookFactory
import net.bible.sharedcore.platform.AppSettings
import org.crosswire.jsword.book.Books
import net.bible.sharedcore.platform.CoreStrings
import net.bible.android.database.mydocument.MyDocumentDao
import net.bible.android.database.mydocument.MyDocumentPage
import net.bible.android.database.mydocument.MyDocumentPageWithContent
import net.bible.service.sword.mydocument.MyDocumentBackend
import net.bible.sharedlogic.platform.MyDocumentStoreEdge
import org.crosswire.jsword.book.sword.SwordBookMetaData
import org.crosswire.jsword.book.sword.SwordGenBook
import org.crosswire.jsword.passage.DefaultLeafKeyList
import org.crosswire.common.activate.Activator
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.lang.reflect.Proxy
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Uses real page navigation and history, with an in-memory page index instead of the document database. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DeletedMyDocumentHistoryTest {
    private val job = SupervisorJob()
    private val pages = mutableSetOf("source", "old-result", "new-result", "other")
    private lateinit var window: Window
    private lateinit var control: WindowControl
    private lateinit var pageManager: CurrentPageManager
    private lateinit var history: HistoryManager
    private lateinit var book: SwordGenBook

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val strings = Proxy.newProxyInstance(CoreStrings::class.java.classLoader, arrayOf(CoreStrings::class.java)) { _, method, _ ->
            method.name
        } as CoreStrings
        val values = mutableMapOf<String, String?>()
        val settings = Proxy.newProxyInstance(AppSettings::class.java.classLoader, arrayOf(AppSettings::class.java)) { _, method, args ->
            when (method.name) {
                "getString" -> values[args[0]] ?: args[1]
                "setString" -> { values[args[0] as String] = args[1] as String?; Unit }
                else -> error("Unexpected settings call: ${method.name}")
            }
        } as AppSettings
        startKoin { modules(module {
            single { settings }
            single { RecentDocumentsStore(settings) }
            single { FakeBookFactory(strings) }
            single { WindowStateServiceImpl() }
            single<MyDocumentStoreEdge> { object : MyDocumentStoreEdge {
                override fun pages(dao: MyDocumentDao, documentId: IdType) = pages.mapIndexed { index, page ->
                    MyDocumentPage(documentId = documentId, title = page, pageKey = page, orderNumber = index)
                }
                override fun pageWithContent(dao: MyDocumentDao, documentId: IdType, pageKey: String): MyDocumentPageWithContent? =
                    error("History navigation must not read page content")
            } }
            single<GlobalTextDisplaySettingsSource> { object : GlobalTextDisplaySettingsSource {
                override val current = WorkspaceEntities.TextDisplaySettings.default
            } }
        }) }
        val metadata = SwordBookMetaData(
            "[AIDocuments]\nModDrv=RawGenBook\nDescription=AI Documents\nCategory=Generic Books\nAndBibleMyDocument=1\n".encodeToByteArray(),
            "AIDocuments.conf",
        )
        val dao = Proxy.newProxyInstance(MyDocumentDao::class.java.classLoader, arrayOf(MyDocumentDao::class.java)) { _, _, _ ->
            error("The page store owns the database boundary")
        } as MyDocumentDao
        book = SwordGenBook(metadata, MyDocumentBackend(IdType(), metadata) { dao })
        Books.installed().addBook(book)
        control = WindowControl()
        val repository = WindowRepository(CoroutineScope(Dispatchers.Unconfined + job))
        pageManager = CurrentPageManager(blank<BibleTraverser>(), blank<BookmarkControl>(), control)
        window = Window(WorkspaceEntities.Window(IdType(), false, false,
            windowLayout = WorkspaceEntities.WindowLayout("MINIMISED")), pageManager, repository)
        control.windowRepository = repository
        repository.activeWindow = window
        history = HistoryManager(control, object : HistoryPlatform {
            override fun screenHistoryItem(window: Window, screenToken: Any?): HistoryItem? = null
            override fun isOnReadingScreen() = true
            override fun leaveCurrentScreen() = Unit
        })
        open("source")
        history.clear()
    }

    @AfterTest
    fun tearDown() {
        if (::window.isInitialized) window.destroy()
        runBlocking { job.cancelAndJoin() }
        HistoryManager.resetInstanceForTest()
        if (::book.isInitialized) {
            Activator.deactivate(book)
            Books.installed().removeBook(book)
        }
        stopKoin()
        Dispatchers.resetMain()
    }

    @Test
    fun backAfterRegenerateSkipsTheDeletedResult() {
        open("old-result")
        // Regenerate deletes in onStarted, then opens the new result with normal history recording.
        delete("old-result")
        open("new-result")

        history.goBack()

        assertEquals("source", pageManager.currentPage.singleKey?.getOsisRef())
        assertFalse(history.canGoBack())
    }

    @Test
    fun backSkipsOlderDeletedEntriesAfterLeavingTheResult() {
        open("old-result")
        open("other")
        delete("old-result")

        history.goBack()

        assertEquals("source", pageManager.currentPage.singleKey?.getOsisRef())
    }

    @Test
    fun keepingThePreviousResultPreservesBackNavigation() {
        open("old-result")
        open("new-result")

        history.goBack()

        assertEquals("old-result", pageManager.currentPage.singleKey?.getOsisRef())
    }

    @Test
    fun deletedPagesAreNotOfferedButRemainPersistedInHistory() {
        open("old-result")
        open("other")
        delete("old-result")

        assertEquals(listOf("source"), history.getHistory(window.id).map { (it as KeyHistoryItem).key.getOsisRef() })
        assertEquals(listOf("source", "old-result"), history.getEntities(window.id).map { it.key })
    }

    @Test
    fun backWithOnlyDeletedEntriesLeavesTheCurrentPageUnchanged() {
        open("old-result")
        history.clear()
        open("new-result")
        delete("old-result")

        assertFalse(history.canGoBack())
        history.goBack()
        assertEquals("new-result", pageManager.currentPage.singleKey?.getOsisRef())
    }

    @Test
    fun savingDuringAnEmptyPageIndexPreservesHistoryForWhenTheStoreReturns() {
        open("old-result")
        open("other")
        val savedPages = pages.toList()
        pages.clear()
        Activator.deactivate(book)
        Activator.activate(book)

        val entities = history.getEntities(window.id)
        assertEquals(listOf("source", "old-result"), entities.map { it.key })

        pages.addAll(savedPages)
        Activator.deactivate(book)
        Activator.activate(book)
        history.restoreFrom(window, entities)
        history.goBack()
        assertEquals("old-result", pageManager.currentPage.singleKey?.getOsisRef())
    }

    @Test
    fun backReportsFalseWhenTheLastDestinationDisappearsAfterThePrecheck() {
        open("old-result")
        history.clear()
        open("new-result")
        assertTrue(history.canGoBack())
        delete("old-result")

        assertFalse(history.goBack())
        assertEquals("new-result", pageManager.currentPage.singleKey?.getOsisRef())
        open("other")
        assertTrue(history.goBack())
        assertEquals("new-result", pageManager.currentPage.singleKey?.getOsisRef())
    }

    @Test
    fun traversalFallsThroughWhenItsDestinationDisappears() {
        val item = object : HistoryItem {
            override val window get() = this@DeletedMyDocumentHistoryTest.window
            override val description = "Disappearing screen"
            override val createdAt = net.bible.android.database.DbDate(1_000)
            override fun revertTo() = error("Unavailable screen must not be restored")
            override fun tryRevertTo() = false
        }
        val manager = HistoryManager(control, object : HistoryPlatform {
            override fun screenHistoryItem(window: Window, screenToken: Any?) = item
            override fun isOnReadingScreen() = false
            override fun leaveCurrentScreen() = error("No screen was restored")
        })
        manager.addHistoryItem(window, item)

        assertFalse(HistoryTraversal(manager, true).goBack())
    }

    @Test
    fun selectingAnItemDeletedWhileTheHistoryListIsOpenDoesNotNavigate() {
        open("old-result")
        open("other")
        val selected = history.getHistory(window.id).first()
        delete("old-result")

        selected.revertTo()

        assertEquals("other", pageManager.currentPage.singleKey?.getOsisRef())
    }

    @Test
    fun reactivationRefreshesTheCachedPagesUsedByHistoryAvailability() {
        open("old-result")
        open("other")
        assertEquals(2, history.getHistory(window.id).size)
        pages.remove("old-result")
        // A raw store change is deliberately invisible until the manager reactivates the book.
        assertEquals(2, history.getHistory(window.id).size)
        Activator.deactivate(book)
        Activator.activate(book)
        book.getKey("other")

        assertEquals(listOf("source"), history.getHistory(window.id).map { (it as KeyHistoryItem).key.getOsisRef() })
    }

    private fun open(page: String) = pageManager.setCurrentDocumentAndKey(book, key(page))
    private fun key(page: String) = DefaultLeafKeyList(page, page)
    private fun delete(page: String) {
        pages.remove(page)
        // MyDocumentBookManager refreshes the existing book in place after every deletion path.
        Activator.deactivate(book)
        Activator.activate(book)
    }

    /** The fixture never calls these controllers; the real pages/window own all navigation under test. */
    private inline fun <reified T> blank(): T {
        val type = Class.forName("sun.misc.Unsafe")
        val field = type.getDeclaredField("theUnsafe").apply { isAccessible = true }
        return type.getMethod("allocateInstance", Class::class.java).invoke(field.get(null), T::class.java) as T
    }
}

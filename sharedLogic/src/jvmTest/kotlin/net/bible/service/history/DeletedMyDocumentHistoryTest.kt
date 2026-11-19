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
import net.bible.service.download.FakeBookFactory
import net.bible.sharedcore.platform.CoreStrings
import org.crosswire.jsword.book.sword.NullBackend
import org.crosswire.jsword.book.sword.SwordBookMetaData
import org.crosswire.jsword.book.sword.SwordGenBook
import org.crosswire.jsword.passage.DefaultKeyList
import org.crosswire.jsword.passage.DefaultLeafKeyList
import org.crosswire.jsword.passage.Key
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.lang.reflect.Proxy
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** Uses real page navigation and history, with an in-memory page index instead of the document database. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DeletedMyDocumentHistoryTest {
    private val job = SupervisorJob()
    private val pages = mutableSetOf("source", "old-result", "new-result", "other")
    private lateinit var window: Window
    private lateinit var pageManager: CurrentPageManager
    private lateinit var history: HistoryManager
    private lateinit var book: SwordGenBook

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val strings = Proxy.newProxyInstance(CoreStrings::class.java.classLoader, arrayOf(CoreStrings::class.java)) { _, method, _ ->
            method.name
        } as CoreStrings
        startKoin { modules(module {
            single { FakeBookFactory(strings) }
            single { WindowStateServiceImpl() }
            single<GlobalTextDisplaySettingsSource> { object : GlobalTextDisplaySettingsSource {
                override val current = WorkspaceEntities.TextDisplaySettings.default
            } }
        }) }
        book = SwordGenBook(SwordBookMetaData(
            "[AIDocuments]\nModDrv=RawGenBook\nDescription=AI Documents\nCategory=Generic Books\nAndBibleMyDocument=1\n".encodeToByteArray(),
            "AIDocuments.conf",
        ), object : NullBackend() {
            override fun contains(key: Key?) = key?.getOsisRef() in pages
            override fun readIndex(): Key = DefaultKeyList().also { index ->
                pages.forEach { index.addAll(key(it)) }
            }
        })
        val control = WindowControl()
        val repository = WindowRepository(CoroutineScope(Dispatchers.Unconfined + job))
        pageManager = CurrentPageManager(blank<BibleTraverser>(), blank<BookmarkControl>(), control)
        window = Window(WorkspaceEntities.Window(IdType(), false, false,
            windowLayout = WorkspaceEntities.WindowLayout("VISIBLE")), pageManager, repository)
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
    fun deletedPagesAreNotOfferedOrPersistedInHistory() {
        open("old-result")
        open("other")
        delete("old-result")

        assertEquals(listOf("source"), history.getHistory(window.id).map { (it as KeyHistoryItem).key.getOsisRef() })
        assertEquals(listOf("source"), history.getEntities(window.id).map { it.key })
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

    private fun open(page: String) = pageManager.setCurrentDocumentAndKey(book, key(page))
    private fun key(page: String) = DefaultLeafKeyList(page, page)
    private fun delete(page: String) {
        pages.remove(page)
        // MyDocumentBookManager refreshes the existing book in place after every deletion path.
        book.deactivate(null)
    }

    /** The fixture never calls these controllers; the real pages/window own all navigation under test. */
    private inline fun <reified T> blank(): T {
        val type = Class.forName("sun.misc.Unsafe")
        val field = type.getDeclaredField("theUnsafe").apply { isAccessible = true }
        return type.getMethod("allocateInstance", Class::class.java).invoke(field.get(null), T::class.java) as T
    }
}

package net.bible.service.history

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.page.CurrentPage
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.page.window.WindowRepository
import net.bible.android.control.readingplan.ReadingPlanControl
import net.bible.service.common.CommonUtils
import net.bible.sharedcore.reading.ReadingHostPresence
import net.bible.sharedcore.reading.ReadingViewVisibility
import net.bible.test.DatabaseResetter
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.passage.Key
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.versification.BibleBook
import org.crosswire.jsword.versification.system.Versifications
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class HistoryDirectCallTest {
    private lateinit var repository: WindowRepository
    private lateinit var originalRepository: WindowRepository
    private lateinit var history: HistoryManager
    private val window get() = repository.activeWindow
    private val kjv get() = requireNotNull(Books.installed().getBook("KJV"))
    private fun verse(book: BibleBook, chapter: Int, number: Int) =
        Verse(Versifications.instance().getVersification("KJV"), book, chapter, number)

    @Before
    fun setUp() {
        ReadingViewVisibility.setVisible(false)
        ReadingHostPresence.setForeground(null)
        originalRepository = CommonUtils.windowControl.windowRepository
        repository = WindowRepository(CoroutineScope(Dispatchers.Main))
        CommonUtils.windowControl.windowRepository = repository
        repository.initialize()
        HistoryManager.resetInstanceForTest()
        window.pageManager.setCurrentDocumentAndKey(kjv, verse(BibleBook.GEN, 1, 1), false)
        history = HistoryManager(CommonUtils.windowControl, net.bible.android.platform.AndroidHistoryPlatform())
        ReadingViewVisibility.setVisible(true)
    }

    @After
    fun tearDown() {
        HistoryManager.resetInstanceForTest()
        ReadingViewVisibility.setVisible(false)
        ReadingHostPresence.setForeground(null)
        try {
            DatabaseResetter.resetDatabase(repository.scope)
        } finally {
            CommonUtils.windowControl.windowRepository = originalRepository
        }
    }

    @Test
    fun recordBeforeAnyManagerExistsDoesNothing() {
        HistoryManager.resetInstanceForTest()
        HistoryManager.recordIfCreated(null)
        val created = HistoryManager(CommonUtils.windowControl, net.bible.android.platform.AndroidHistoryPlatform())
        assertTrue(created.getHistory(window.id).isEmpty())
    }

    @Test
    fun setKeyRecordsTheOldPositionBeforeTheKeyChanges() {
        window.pageManager.currentBible.setKey(verse(BibleBook.JOHN, 3, 16), true)
        val item = history.getHistory(window.id).single() as KeyHistoryItem
        assertEquals("Gen.1.1", item.key.osisID)
        assertEquals("John.3.16", window.pageManager.currentBible.singleKey.osisID)
    }

    @Test
    fun documentSwapRecordsTheOldDocument() {
        window.pageManager.setCurrentDocument(requireNotNull(Books.installed().getBook("FinRK")))
        assertEquals("KJV", (history.getHistory(window.id).single() as KeyHistoryItem).document.initials)
        assertEquals("FinRK", window.pageManager.currentPage.currentDocument?.initials)
    }

    @Test
    fun traversalRecordsOnlyWhenIntegratedAndUsesTheLiveManager() {
        val traversal = HistoryTraversal(history, false)
        val live = HistoryManager(CommonUtils.windowControl, net.bible.android.platform.AndroidHistoryPlatform())
        traversal.beforeStartActivity()
        assertTrue(live.getHistory(window.id).isEmpty())
        traversal.isIntegrateWithHistoryManager = true
        traversal.beforeStartActivity()
        assertEquals("Gen.1.1", (live.getHistory(window.id).single() as KeyHistoryItem).key.osisID)
        assertTrue(history.getHistory(window.id).isEmpty())
    }

    @Test
    fun aThrowingHistoryManagerDoesNotBreakTheCaller() {
        val broken = object : WindowControl() {
            override var windowRepository: WindowRepository
                get() = throw IllegalStateException("boom")
                set(_) {}
        }
        val manager = HistoryManager(broken, net.bible.android.platform.AndroidHistoryPlatform())
        assertFailsWith<IllegalStateException> { manager.addHistoryItem(null) }
        HistoryManager.recordIfCreated(null)
    }

    @Test
    fun setKeyStillNavigatesWhenRecordingTheSuppliedWindowThrows() {
        val pm = window.pageManager
        val page = pm.currentPage
        try {
            // Fail the history read, not navigation: setKey still runs on the real Bible page.
            org.robolectric.util.ReflectionHelpers.setField(pm, "currentPage", object : CurrentPage by page {
                override val singleKey: Key
                    get() {
                        if (pm.currentBible.singleKey.osisID == "Gen.1.1") {
                            throw IllegalStateException("history key failed")
                        }
                        return page.singleKey!!
                    }
            })
            assertFailsWith<IllegalStateException> { history.addHistoryItem(window) }
            pm.currentBible.setKey(verse(BibleBook.JOHN, 3, 16), true)
            assertEquals("John.3.16", pm.currentBible.singleKey.osisID)
            assertTrue(history.getHistory(window.id).isEmpty())
        } finally {
            org.robolectric.util.ReflectionHelpers.setField(pm, "currentPage", page)
        }
    }

    @Test
    fun readingPlanRecordsBeforeResolvingTheNavigationWindow() = kotlinx.coroutines.runBlocking {
        val navigationSnapshots = mutableListOf<List<HistoryItem>>()
        val navigationControl = object : WindowControl() {
            override var windowRepository: WindowRepository
                get() {
                    navigationSnapshots += history.getHistory(window.id)
                    return repository
                }
                set(_) {}
        }
        val koin = GlobalContext.get()
        val control = ReadingPlanControl(koin.get(), navigationControl, koin.get(), koin.get(), koin.get())
        val originalPlan = control.currentPlanCode
        try {
            control.setReadingPlan("y1ntpspr")
            control.getReadingStatus(1)
            navigationSnapshots.clear()
            // The next window lookup is the navigation boundary. Inspect history there so the
            // later setKey recording cannot hide a missing ReadingPlanControl.read recording.
            control.read(1, 0, verse(BibleBook.JOHN, 3, 16))
            val recorded = navigationSnapshots.first()
            assertEquals("Gen.1.1", (recorded.single() as KeyHistoryItem).key.osisID)
            assertEquals("John.3.16", window.pageManager.currentBible.singleKey.osisID)
            assertTrue(control.getReadingStatus(1).isRead(0))
        } finally {
            control.setReadingPlan(originalPlan)
        }
    }
}

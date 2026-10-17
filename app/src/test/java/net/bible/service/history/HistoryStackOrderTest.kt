package net.bible.service.history

import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.page.window.WindowRepository
import net.bible.android.database.DbDate
import net.bible.android.database.WorkspaceEntities
import net.bible.service.common.CommonUtils
import net.bible.sharedcore.reading.ReadingHostPresence
import net.bible.sharedcore.reading.ReadingViewVisibility
import net.bible.test.DatabaseResetter
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.versification.BibleBook
import org.crosswire.jsword.versification.system.Versifications
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the order contract of the per-window history stack (bottom to top, `getHistory` most recent first,
 * `goBack` pops the top, restore keeps entity order). Written as an oracle before the stack type changed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class HistoryStackOrderTest {
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
        repository = net.bible.test.testWindowRepository()
        CommonUtils.windowControl.windowRepository = repository
        repository.initializeAtEdge()
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

    private fun osis(items: List<HistoryItem>) = items.map { (it as KeyHistoryItem).key.getOsisID() }

    private fun pushABC() {
        // Each setKey records the position it leaves: Gen.1.1 (A), John.3.16 (B), Rom.1.1 (C).
        val bible = window.pageManager.currentBible
        bible.setKey(verse(BibleBook.JOHN, 3, 16), true)
        bible.setKey(verse(BibleBook.ROM, 1, 1), true)
        bible.setKey(verse(BibleBook.PS, 1, 1), true)
    }

    @Test
    fun getHistoryIsMostRecentFirst() {
        pushABC()
        assertEquals(listOf("Rom.1.1", "John.3.16", "Gen.1.1"), osis(history.getHistory(window.id)))
    }

    @Test
    fun entitiesAreInPushOrderBottomToTop() {
        pushABC()
        assertEquals(listOf("Gen.1.1", "John.3.16", "Rom.1.1"), history.getEntities(window.id).map { it.key })
    }

    @Test
    fun goBackPopsTheTopItemOnly() {
        pushABC()
        assertTrue(history.canGoBack())
        history.goBack()
        assertEquals(listOf("John.3.16", "Gen.1.1"), osis(history.getHistory(window.id)))
        assertEquals("Rom.1.1", window.pageManager.currentBible.singleKey.getOsisID())
    }

    @Test
    fun popHistoryItemRemovesTheTopAndEmptyStackCannotGoBack() {
        pushABC()
        history.popHistoryItem()
        assertEquals(listOf("John.3.16", "Gen.1.1"), osis(history.getHistory(window.id)))
        history.popHistoryItem()
        history.popHistoryItem()
        assertFalse(history.canGoBack())
        assertTrue(history.getHistory(window.id).isEmpty())
    }

    @Test
    fun restoreFromKeepsEntityOrder() {
        val entities = listOf("Gen.1.1", "Exod.2.3", "Matt.5.1").map {
            WorkspaceEntities.HistoryItem(window.id, DbDate(1000), "KJV", it, null)
        }
        history.restoreFrom(window, entities)
        assertEquals(listOf("Matt.5.1", "Exod.2.3", "Gen.1.1"), osis(history.getHistory(window.id)))
        assertEquals(listOf("Gen.1.1", "Exod.2.3", "Matt.5.1"), history.getEntities(window.id).map { it.key })
        history.goBack()
        assertEquals(listOf("Exod.2.3", "Gen.1.1"), osis(history.getHistory(window.id)))
    }
}

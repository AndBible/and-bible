package net.bible.service.history

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.page.window.Window
import net.bible.android.control.page.window.WindowRepository
import net.bible.service.common.CommonUtils
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
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the branch structure of `HistoryManager.createHistoryItem` through a fake [HistoryPlatform]:
 * a screen token wins over everything; otherwise the reading screen records a verse
 * [KeyHistoryItem] WITHOUT consulting the platform; otherwise the platform's screen item (or
 * nothing). `goBack` leaves the screen only when it is not the reading screen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class HistoryManagerPlatformTest {
    private class ScreenItem(val label: String, window: Window) : HistoryItemBase(window) {
        override val description: CharSequence get() = label
        override val createdAt: Date = Date()
        var reverted = 0
        override fun revertTo() { reverted++ }
        override fun equals(other: Any?) = other is ScreenItem && other.label == label
        override fun hashCode() = label.hashCode()
    }

    private class FakePlatform : HistoryPlatform {
        var onReading = false
        var screenItemLabel: String? = null
        val tokensAsked = mutableListOf<Any?>()
        var left = 0
        override fun screenHistoryItem(window: Window, screenToken: Any?): HistoryItem? {
            tokensAsked += screenToken
            return if (screenToken != null) ScreenItem("token:$screenToken", window)
            else screenItemLabel?.let { ScreenItem(it, window) }
        }
        override fun isOnReadingScreen() = onReading
        override fun leaveCurrentScreen() { left++ }
    }

    private lateinit var repository: WindowRepository
    private lateinit var originalRepository: WindowRepository
    private lateinit var platform: FakePlatform
    private lateinit var manager: HistoryManager
    private val window get() = repository.activeWindow

    @Before
    fun setUp() {
        originalRepository = CommonUtils.windowControl.windowRepository
        repository = WindowRepository(CoroutineScope(Dispatchers.Main))
        CommonUtils.windowControl.windowRepository = repository
        repository.initialize()
        HistoryManager.resetInstanceForTest()
        val kjv = requireNotNull(Books.installed().getBook("KJV"))
        window.pageManager.currentBible.setCurrentDocumentAndKey(
            kjv, Verse(Versifications.instance().getVersification("KJV"), BibleBook.GEN, 1, 1)
        )
        platform = FakePlatform()
        manager = HistoryManager(CommonUtils.windowControl, platform)
    }

    @After
    fun tearDown() {
        HistoryManager.resetInstanceForTest()
        try {
            DatabaseResetter.resetDatabase(repository.scope)
        } finally {
            CommonUtils.windowControl.windowRepository = originalRepository
        }
    }

    @Test
    fun aScreenTokenIsTurnedIntoAnItemByThePlatformEvenOnTheReadingScreen() {
        platform.onReading = true
        manager.addHistoryItem(window, "tok")
        assertEquals("token:tok", manager.getHistory(window.id).single().description)
        assertEquals(listOf<Any?>("tok"), platform.tokensAsked)
    }

    @Test
    fun theReadingScreenRecordsTheVerseWithoutAskingThePlatformForAScreen() {
        platform.onReading = true
        platform.screenItemLabel = "must not be used"
        manager.addHistoryItem(window)
        assertEquals("Genesis 1:1 KJV", (manager.getHistory(window.id).single() as KeyHistoryItem).description)
        assertTrue(platform.tokensAsked.isEmpty())
    }

    @Test
    fun anotherScreenIsRecordedWhenThePlatformGivesAnItem() {
        platform.onReading = false
        platform.screenItemLabel = "search results"
        manager.addHistoryItem(window)
        assertEquals("search results", manager.getHistory(window.id).single().description)
        assertEquals(listOf<Any?>(null), platform.tokensAsked)
    }

    @Test
    fun nothingIsRecordedWhenNeitherAScreenItemNorTheReadingScreenApplies() {
        platform.onReading = false
        platform.screenItemLabel = null
        manager.addHistoryItem(window)
        assertTrue(manager.getHistory(window.id).isEmpty())
    }

    @Test
    fun goBackLeavesTheScreenOnlyWhenItIsNotTheReadingScreen() {
        platform.onReading = false
        platform.screenItemLabel = "screen"
        manager.addHistoryItem(window)
        manager.goBack()
        assertEquals(1, platform.left)

        platform.screenItemLabel = "screen2"
        platform.onReading = false
        manager.addHistoryItem(window)
        platform.onReading = true
        manager.goBack()
        assertEquals(1, platform.left, "on the reading screen going back must not leave the host")
    }
}

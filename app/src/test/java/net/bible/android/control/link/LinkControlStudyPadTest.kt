package net.bible.android.control.link

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.page.window.WindowRepository
import net.bible.android.control.search.SearchControl
import net.bible.android.database.IdType
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.service.download.FakeBookFactory
import net.bible.service.sword.StudyPadKey
import net.bible.sharedcore.platform.CoreStrings
import net.bible.sharedcore.platform.UserNotifier
import net.bible.test.DatabaseResetter.resetDatabase
import net.bible.test.testAppSettings
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.versification.BibleBook
import org.crosswire.jsword.versification.system.Versifications
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [LinkControl.openStudyPad] is suspend (the label lookup is a DB read). Its URL-handler caller launches it, and the
 * context-menu window mode is reset as soon as that synchronous handler returns, so the mode must be the one read
 * at click time, not [LinkControl.windowMode] when the link is finally shown.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class LinkControlStudyPadTest {
    private val label = BookmarkEntities.Label(name = "pad").apply { new = true }
    private val windowControl = WindowControl()
    private lateinit var windowRepository: WindowRepository
    private val bookmarkControl: BookmarkControl get() = GlobalContext.get().get()
    private val notifier = object : UserNotifier {
        override fun showError(message: String, cause: Throwable?) {}
    }

    @Before
    fun setUp() {
        runBlocking { bookmarkControl.insertOrUpdateLabel(label) }
        windowRepository = WindowRepository(CoroutineScope(Dispatchers.Main))
        windowControl.windowRepository = windowRepository
        windowRepository.initialize()
        val kjv = Books.installed().getBook("KJV") as SwordBook
        val verse = Verse(Versifications.instance().getVersification("KJV"), BibleBook.GEN, 1, 1)
        windowRepository.activeWindow.pageManager.currentBible.setCurrentDocumentAndKey(kjv, verse)
    }

    @After
    fun tearDown() { resetDatabase(windowRepository.scope) }

    private fun control() = LinkControl(
        windowControl, bookmarkControl, GlobalContext.get().get<SearchControl>(),
        testAppSettings(), notifier, Mockito.mock(CoreStrings::class.java), Mockito.mock(LinkPlatform::class.java),
    )

    @Test fun clickTimeNewWindowModeOpensTheStudyPadInANewWindowAfterTheModeWasReset() {
        val control = control()
        control.windowMode = WindowMode.WINDOW_MODE_UNDEFINED // the menu handler already reset it
        val opened = runBlocking { control.openStudyPad(label.id, null, WindowMode.WINDOW_MODE_NEW) }
        assertTrue(opened)
        // NEW opens an ordinary window showing the StudyPad; the reset (UNDEFINED) mode would have used the links window.
        assertTrue(windowRepository.windowList.none { it.isLinksWindow })
        val padWindow = windowRepository.windowList.single { it.pageManager.currentPage.currentDocument == FakeBookFactory.journalDocument }
        assertTrue(padWindow.pageManager.currentPage.key is StudyPadKey)
    }

    @Test fun missingLabelOpensNothing() {
        val control = control()
        val opened = runBlocking { control.openStudyPad(MISSING, null, WindowMode.WINDOW_MODE_NEW) }
        val before = windowRepository.windowList.size
        assertFalse(opened)
        assertEquals(before, windowRepository.windowList.size)
    }

    private companion object {
        val MISSING = IdType()
    }
}

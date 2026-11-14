package net.bible.android.control.page

import kotlinx.coroutines.test.runTest
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.page.window.WindowRepository
import net.bible.service.common.CommonUtils
import net.bible.service.download.FakeBookFactory
import net.bible.test.DatabaseResetter
import net.bible.test.testWindowRepository
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.versification.BibleBook
import org.crosswire.jsword.versification.BookName
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.mp.KoinPlatform
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class MissingBibleBookNoticeTest {
    private lateinit var repository: WindowRepository
    private val initials = "TestOtOnly4084"

    @After
    fun tearDown() {
        Books.installed().getBook(initials)?.let { Books.installed().removeBook(it) }
        if (::repository.isInitialized) DatabaseResetter.resetDatabase(repository.scope)
        else DatabaseResetter.resetDatabase()
    }

    @Test
    fun switchingFromRomans8ToAnOldTestamentOnlyBibleNamesRomansInTheNotice() = runTest {
        val otOnly = KoinPlatform.getKoin().get<FakeBookFactory>().createFakeRepoBook(
            initials,
            """[$initials]
Description=Old Testament only test Bible
Category=Biblical Texts
Versification=Leningrad
Encoding=UTF-8
Lang=en""",
            "",
            BookCategory.BIBLE,
        ) as SwordBook
        Books.installed().addBook(otOnly)
        assertFalse(otOnly.versification.containsBook(BibleBook.ROM))

        repository = testWindowRepository()
        CommonUtils.windowControl.windowRepository = repository
        repository.initializeAtEdge()
        val pageManager = CommonUtils.windowControl.activeWindowPageManager
        val kjv = Books.installed().getBook("KJV") as SwordBook
        pageManager.setCurrentDocumentAndKey(kjv, Verse(kjv.versification, BibleBook.ROM, 8, 1))
        val originalFullNames = BookName.isFullBookName()
        BookName.setFullBookName(true)
        try {
            pageManager.setCurrentDocument(otOnly)
            assertEquals(otOnly, pageManager.currentPage.currentDocument)

            val content = pageManager.currentPage.currentPageContent()
            assertTrue("A missing book must produce a notice", content is ErrorDocument)
            assertEquals(
                "`Romans 8 was not found in document $initials.`",
                (content as ErrorDocument).asHashMap["errorMessage"],
            )
        } finally {
            BookName.setFullBookName(originalFullNames)
        }
    }
}

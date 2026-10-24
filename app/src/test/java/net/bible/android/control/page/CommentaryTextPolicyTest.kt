package net.bible.android.control.page

import com.nhaarman.mockitokotlin2.*
import kotlinx.coroutines.test.runTest
import net.bible.android.TestBibleApplication
import net.bible.android.control.page.window.WindowRepository
import net.bible.service.common.CommonUtils
import net.bible.service.download.FakeBookFactory
import net.bible.service.format.SaxTextPolicy
import net.bible.service.sword.BookAndKey
import net.bible.test.DatabaseResetter
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.passage.VerseRange
import org.crosswire.jsword.versification.BibleBook
import org.crosswire.jsword.versification.system.Versifications
import fi.sykero.kdom.input.build
import fi.sykero.kdom.input.KdomBuilder
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.StringReader

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [35])
class CommentaryTextPolicyTest {
    @After fun resetDatabase() = DatabaseResetter.resetDatabase()

    @Test fun memorizeOutputUsesInjectedPolicyRatherThanAndroidSdkDefault() = runTest {
        val koin = GlobalContext.get()
        val originalPolicy = koin.get<SaxTextPolicy>()
        val control = CommonUtils.windowControl
        val originalRepo = control.windowRepository
        val repo = WindowRepository(backgroundScope)
        try {
            assertEquals(SaxTextPolicy.MODERN, net.bible.android.platform.androidSaxTextPolicy())
            koin.loadModules(listOf(module { single { SaxTextPolicy.LEGACY } }))
            control.windowRepository = repo
            repo.initializeAtEdge()
            val page = repo.activeWindow.pageManager.currentCommentary
            val v11n = Versifications.instance().getVersification("KJV")
            val verse = Verse(v11n, BibleBook.GEN, 1, 1)
            val book = mock<Book>()
            whenever(book.bookMetaData).thenReturn(Books.installed().getBook("KJV")!!.bookMetaData)
            whenever(book.getOsisIterator(anyOrNull(), any(), any())).thenAnswer {
                val element = KdomBuilder().build(StringReader(
                    "<verse osisID=\"Gen.1.1\">a&amp;quot;quoted&amp;quot;b</verse>"
                )).rootElement.detach()
                mutableListOf<fi.sykero.kdom.Content>(element).iterator()
            }
            page.setCurrentDocumentAndKey(koin.get<FakeBookFactory>().memorizeDocument, verse)
            page.sourceBookAndKey = BookAndKey(VerseRange(v11n, verse), book)
            val output = (page.currentPageContent() as MemorizeDocument).asHashMap["texts"].toString()
            assertTrue("Legacy policy must preserve escaped text in actual memorize output: $output", output.contains("a&quot;quoted&quot;b"))
            assertFalse(output.contains("a\\\"quoted\\\"b"))
        } finally {
            try {
                repo.windowList.forEach { it.destroy(destroyView = false) }
            } finally {
                control.windowRepository = originalRepo
                koin.loadModules(listOf(module { single { originalPolicy } }))
            }
        }
    }
}

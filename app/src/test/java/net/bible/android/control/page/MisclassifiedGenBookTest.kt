package net.bible.android.control.page

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.page.window.WindowRepository
import net.bible.service.common.CommonUtils
import net.bible.service.sword.SwordDocumentFacade
import net.bible.test.DatabaseResetter
import net.bible.test.testWindowRepository
import org.crosswire.common.activate.Activator
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.SwordBookDriver
import org.crosswire.jsword.book.sword.SwordBookMetaDataJvm
import org.crosswire.jsword.book.sword.SwordGenBook
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A misleading Category must not send a real tree-backed module to VersePage (#3879). */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class MisclassifiedGenBookTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()
    private lateinit var repository: WindowRepository
    private lateinit var book: SwordGenBook

    @Before
    fun setUp() {
        repository = testWindowRepository()
        CommonUtils.windowControl.windowRepository = repository
        repository.initializeAtEdge()
        val root = temporaryFolder.root
        val conf = File(root, "test.conf").apply {
            writeText("""
                [MisclassifiedGenBook]
                ModDrv=RawGenBook
                Category=Biblical Texts
                DataPath=./test
                SourceType=Plaintext
                Encoding=UTF-8
                Lang=en
            """.trimIndent())
        }
        // RawGenBook: two index offsets, a root pointing to one leaf, and its text.
        val text = "A general-book entry.".toByteArray()
        val rootNode = ByteBuffer.allocate(15).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(-1).putInt(-1).putInt(4).put(0.toByte()).putShort(0).array()
        val entryName = "Entry".toByteArray()
        val entryNode = ByteBuffer.allocate(12 + entryName.size + 1 + 2 + 8).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(0).putInt(-1).putInt(-1).put(entryName).put(0.toByte())
            .putShort(8).putInt(0).putInt(text.size).array()
        File(root, "test.idx").writeBytes(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(0).putInt(rootNode.size).array())
        File(root, "test.dat").writeBytes(rootNode + entryNode)
        File(root, "test.bdt").writeBytes(text)
        val metadata = SwordBookMetaDataJvm.create(conf, root.toURI())
        metadata.setDriver(SwordBookDriver.instance())
        book = metadata.bookType!!.createBook(metadata) as SwordGenBook
        Books.installed().addBook(book)
    }

    @After
    fun tearDown() {
        repository.activeWindow.pageManager.destroy()
        Books.installed().removeBook(book)
        Activator.deactivate(book)
        DatabaseResetter.resetDatabase(repository.scope)
    }

    @Test
    fun selectingRawGenBookWithBibleCategoryUsesGeneralBookPage() {
        val pageManager = repository.activeWindow.pageManager
        val previousBible = pageManager.currentBible.currentDocument

        // This used to throw SwordGenBook -> AbstractPassageBook in VersePage.
        pageManager.setCurrentDocument(book)

        assertSame(pageManager.currentGeneralBook, pageManager.currentPage)
        assertSame(book, pageManager.currentPage.currentDocument)
        assertSame(previousBible, pageManager.currentBible.currentDocument)
        assertFalse(SwordDocumentFacade.bibles.contains(book))
        assertFalse(SwordDocumentFacade.unlockedBibles.contains(book))
        assertFalse(SwordDocumentFacade.getBooks(BookCategory.COMMENTARY).contains(book))
        assertEquals(listOf(book), SwordDocumentFacade.getBooks(BookCategory.GENERAL_BOOK).filter { it == book })
        val key = book.getKey("Entry")
        assertEquals("A general-book entry.", book.getRawText(key))
        pageManager.setCurrentDocumentAndKey(book, key)
        assertEquals("Entry", pageManager.currentPage.key?.getOsisRef())
    }
}

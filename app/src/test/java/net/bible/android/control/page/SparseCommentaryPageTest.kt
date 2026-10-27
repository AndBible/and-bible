package net.bible.android.control.page

import kotlinx.coroutines.test.runTest
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.page.window.WindowRepository
import net.bible.service.common.CommonUtils
import net.bible.service.sword.OsisToPlainText
import net.bible.service.sword.SwordContentFacade
import net.bible.test.DatabaseResetter
import okio.Path.Companion.toPath
import org.crosswire.common.util.KUri
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.BookType
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.book.sword.SwordBookMetaData
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.versification.BibleBook
import org.crosswire.jsword.versification.system.Versifications
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.rules.TemporaryFolder
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class SparseCommentaryPageTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun `opening a repeated no-note entry returns the notice without scanning the whole gap`() = runTest {
        val v11n = Versifications.instance().getVersification("KJV")!!
        val selected = Verse(v11n, BibleBook.NUM, 4, 45)
        val root = temporaryFolder.newFolder("sparse-commentary")
        val data = File(root, "modules/sparse").apply { mkdirs() }
        val first = selected.ordinal - 250
        val last = selected.ordinal + 250
        val notice = "No commentary on these verses is included"
        val raw = "<div><p>$notice</p></div>".toByteArray()
        // Like Luther, a sparse module can link a non-empty no-note notice over a huge gap.
        val index = ByteBuffer.allocate((v11n.getTestamentOrdinal(last) + 1) * 6)
            .order(ByteOrder.LITTLE_ENDIAN)
        for (ordinal in first..last) {
            index.position(v11n.getTestamentOrdinal(ordinal) * 6)
            index.putInt(0).putShort(raw.size.toShort())
        }
        File(data, "ot").writeBytes(raw)
        File(data, "ot.vss").writeBytes(index.array())
        File(data, "nt").writeBytes(byteArrayOf())
        File(data, "nt.vss").writeBytes(byteArrayOf())
        val conf = File(root, "mods.d/SparseCommentary.conf").apply {
            parentFile.mkdirs()
            writeText("""
                [SparseCommentary]
                DataPath=./modules/sparse/
                ModDrv=RawCom
                SourceType=OSIS
                Encoding=UTF-8
                Lang=en
                Description=Synthetic sparse commentary
            """.trimIndent())
        }
        val metadata = SwordBookMetaData(conf.absolutePath.toPath(), KUri(root.toURI().toString()))
        val book = BookType.fromString("RawCom")!!.createBook(metadata) as SwordBook
        val control = CommonUtils.windowControl
        val originalRepo = control.windowRepository
        val repo = WindowRepository(backgroundScope)
        try {
            Books.installed().addBook(book)
            SwordContentFacade.clearCaches()
            control.windowRepository = repo
            repo.initializeAtEdge()
            val page = repo.activeWindow.pageManager.currentCommentary
            page.onlySetCurrentDocument(book)
            page.doSetKey(selected)

            val document = page.getPageContent(selected)
            assertTrue("The page must finish loading its no-note content, not an error: $document", document is OsisDocument)
            val osis = document as OsisDocument
            assertEquals(notice, OsisToPlainText.convert(osis.osisFragment.xml).trim())
            assertEquals(selected.getOsisRef(), osis.commentaryRange?.startOsisRef)
            assertEquals(selected.getOsisRef(), osis.commentaryRange?.endOsisRef)
        } finally {
            try {
                repo.windowList.forEach { it.destroy(destroyView = false) }
            } finally {
                control.windowRepository = originalRepo
                SwordContentFacade.evictBook(book.initials)
                Books.installed().removeBook(book)
                book.deactivate(null)
                DatabaseResetter.resetDatabase()
            }
        }
    }
}

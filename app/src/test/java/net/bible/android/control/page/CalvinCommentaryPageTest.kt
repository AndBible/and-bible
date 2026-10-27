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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipInputStream

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class CalvinCommentaryPageTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun `Psalm 85 paging preserves every available commentary entry`() = verifyChapter(
        chapter = 85,
        expectedVerses = listOf(1, 2, 4, 5, 6, 7, 8, 9, 10, 12, 13),
        firstText = "O Jehovah! thou hast been favorable to thy land.",
        lastText = "Righteousness shall go before him.",
    )

    @Test
    fun `Psalm 86 paging preserves every available commentary entry`() = verifyChapter(
        chapter = 86,
        expectedVerses = listOf(1, 2, 3, 5, 6, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17),
        firstText = "Incline thy ear, O Jehovah!",
        lastText = "The last verse contains an additional confirmation",
    )

    private fun verifyChapter(
        chapter: Int,
        expectedVerses: List<Int>,
        firstText: String,
        lastText: String,
    ) = runTest {
        val root = temporaryFolder.newFolder("calvin-psalms")
        ZipInputStream(javaClass.getResourceAsStream("/commentaries/calvin-psalms-85-86.zip")!!).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val file = File(root, entry.name)
                check(file.canonicalPath.startsWith(root.canonicalPath + File.separator))
                file.parentFile!!.mkdirs()
                file.outputStream().use { zip.copyTo(it) }
            }
        }
        val metadata = SwordBookMetaData(
            File(root, "mods.d/calvinpsalms.conf").absolutePath.toPath(), KUri(root.toURI().toString()),
        )
        val book = BookType.fromString("zCom")!!.createBook(metadata) as SwordBook
        val control = CommonUtils.windowControl
        val originalRepo = control.windowRepository
        val repo = WindowRepository(backgroundScope)
        try {
            Books.installed().addBook(book)
            SwordContentFacade.clearCaches()
            control.windowRepository = repo
            repo.initializeAtEdge()
            val page = repo.activeWindow.pageManager.currentCommentary
            val first = Verse(book.versification, BibleBook.PS, chapter, 1)
            page.onlySetCurrentDocument(book)
            page.doSetKey(first)

            val loadedVerses = mutableListOf<Int>()
            val rendered = mutableListOf<String>()
            var selected: Verse? = first
            // Exercise the same page/block sequence as DocumentLoader.requestMoreToEnd.
            while (selected != null && selected.chapter == chapter) {
                assertTrue("Commentary paging must advance: $loadedVerses -> $selected", loadedVerses.size < 20)
                val document = page.getPageContent(selected)
                assertTrue("Expected a commentary page at $selected, got $document", document is OsisDocument)
                val osis = document as OsisDocument
                loadedVerses += selected.verse
                val text = OsisToPlainText.convert(osis.osisFragment.xml)
                assertTrue("Commentary at $selected must contain text", text.isNotBlank())
                rendered += text
                assertEquals(selected.getOsisRef(), osis.commentaryRange?.startOsisRef)
                assertEquals(selected.getOsisRef(), osis.commentaryRange?.endOsisRef)
                selected = if (selected.verse == book.versification.getLastVerse(BibleBook.PS, chapter)) null
                    else page.nextBlockStart(selected)
            }

            assertEquals("No available Psalm $chapter entry may be skipped", expectedVerses, loadedVerses)
            assertTrue(rendered.first(), rendered.first().contains(firstText))
            assertTrue(rendered.last(), rendered.last().contains(lastText))
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

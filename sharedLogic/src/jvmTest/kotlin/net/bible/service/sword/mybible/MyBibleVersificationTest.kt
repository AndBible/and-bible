package net.bible.service.sword.mybible

import fi.sykero.kdom.output.XMLOutputter
import net.bible.service.db.exec
import net.bible.service.db.openSqlite
import net.bible.service.sword.SwordContentFacade
import net.bible.sharedcore.platform.CoreStrings
import net.bible.sharedlogic.platform.ReadOnlyModuleDbOpener
import net.bible.sharedlogic.platform.host.HostReadOnlyModuleDbOpener
import okio.Path.Companion.toPath
import org.crosswire.common.activate.Activator
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.versification.BibleBook
import org.crosswire.jsword.versification.VersificationConverter
import org.crosswire.jsword.versification.system.Versifications
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.lang.reflect.Proxy
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MyBibleVersificationTest {
    @Test
    fun germanChapterLayoutReadsAllMalachiVersesWithoutAFourthChapter() = withBible(germanLayout = true) { book ->
        val chapter = book.getKey("Mal.3")
        val xml = XMLOutputter().outputString(SwordContentFacade.readOsisFragment(book, chapter, strings))
        (1..24).forEach { assertContains(xml, "Malachi verse $it.") }
        assertEquals(24, book.versification.getLastVerse(BibleBook.MAL, 3))
        assertEquals(3, book.versification.getLastChapter(BibleBook.MAL))
        assertTrue(book.versification.isEndOfBook(Verse(book.versification, BibleBook.MAL, 3, 24)))
    }

    @Test
    fun kjvReferenceAndMyBibleLinkUseTheDetectedVersification() = withBible(germanLayout = true) { book ->
        val kjva = Versifications.instance().getVersification("KJVA")!!
        val mapped = VersificationConverter().convert(Verse(kjva, BibleBook.MAL, 4, 1), book.versification)
        assertEquals("Mal.3.19", mapped.getOsisID())
        assertEquals("Malachi verse 19.", book.getRawText(mapped))
        val xml = XMLOutputter().outputString(SwordContentFacade.readOsisFragment(book, book.getKey("Matt.1.1"), strings))
        assertContains(xml, "osisRef=\"Mal.3.24\"")
    }

    @Test
    fun germanLanguageDoesNotOverrideAKjvChapterLayout() = withBible(germanLayout = false) { book ->
        val chapter = book.getKey("Mal.4")
        val xml = XMLOutputter().outputString(SwordContentFacade.readOsisFragment(book, chapter, strings))
        assertContains(xml, "Malachi fourth chapter verse 6.")
        assertEquals(4, book.versification.getLastChapter(BibleBook.MAL))
        assertEquals(18, book.versification.getLastVerse(BibleBook.MAL, 3))
        assertEquals("KJVA", book.versification.name)
    }

    @Test
    fun reportersWholeBibleLayoutSelectsGermanDespiteTwoShortChapters() = withBible(germanLayout = true, reporterBounds = true) { book ->
        assertEquals("German", book.versification.name)
        val xml = XMLOutputter().outputString(SwordContentFacade.readOsisFragment(book, book.getKey("Mal.3"), strings))
        (1..24).forEach { assertContains(xml, "Malachi verse $it.") }
        assertEquals(3, book.versification.getLastChapter(BibleBook.MAL))
        // Other chapter divisions affected by the same KJVA assumption must not be truncated either.
        assertEquals(4, book.versification.getLastChapter(BibleBook.JOEL))
        assertEquals(33, book.versification.getLastVerse(BibleBook.GEN, 32))
    }

    private val strings = Proxy.newProxyInstance(CoreStrings::class.java.classLoader, arrayOf(CoreStrings::class.java)) { _, method, _ ->
        method.name
    } as CoreStrings

    private fun withBible(germanLayout: Boolean, reporterBounds: Boolean = false, check: (SwordBook) -> Unit) {
        val root = Files.createTempDirectory("mybible-versification").toFile()
        var book: SwordBook? = null
        startKoin { modules(module { single<ReadOnlyModuleDbOpener> { HostReadOnlyModuleDbOpener() } }) }
        try {
            val file = root.resolve("VersificationFixture.SQLite3")
            openSqlite(file.path).use { db ->
                db.exec("CREATE TABLE info (name TEXT, value TEXT)")
                db.exec("INSERT INTO info VALUES ('description', 'Chapter layout fixture')")
                db.exec("INSERT INTO info VALUES ('language', 'de')")
                db.exec("CREATE TABLE verses (book_number INTEGER, chapter INTEGER, verse INTEGER, text TEXT, PRIMARY KEY (book_number, chapter, verse))")
                val malachi = if (germanLayout) listOf(14, 17, 24) else listOf(14, 17, 18, 6)
                malachi.forEachIndexed { chapter, count ->
                    (1..count).forEach { verse ->
                        val text = if (chapter == 3) "Malachi fourth chapter verse $verse." else "Malachi verse $verse."
                        db.exec("INSERT INTO verses VALUES (460, ${chapter + 1}, $verse, '$text')")
                    }
                }
                if (reporterBounds) {
                    val bounds = javaClass.getResourceAsStream("/mybible/elb06-chapter-bounds.txt")!!.bufferedReader().use { it.readLines() }
                    bounds.filterNot { it.startsWith("#") || it.isBlank() }.forEach { line ->
                        val (number, counts) = line.split(":")
                        counts.split(",").forEachIndexed { chapter, lastVerse ->
                            db.exec("INSERT OR IGNORE INTO verses VALUES ($number, ${chapter + 1}, $lastVerse, 'Chapter end')")
                        }
                    }
                }
                db.exec("INSERT INTO verses VALUES (470, 1, 1, '<a href=\"B:460 3:24\">Last verse</a>')")
            }
            addMyBibleBook(file.path.toPath())
            book = Books.installed().getBook("MyBible-VersificationFixture") as SwordBook
            check(book)
            // The installed global key list is also used by search and scope discovery.
            assertTrue(book.getGlobalKeyList().contains(book.getKey(if (germanLayout) "Mal.3.24" else "Mal.4.6")))
        } finally {
            book?.let {
                SwordContentFacade.evictBook(it.initials)
                Books.installed().removeBook(it)
                Activator.deactivate(it)
                (it.backend as SqliteBackend).state.close()
            }
            stopKoin()
            root.deleteRecursively()
        }
    }
}

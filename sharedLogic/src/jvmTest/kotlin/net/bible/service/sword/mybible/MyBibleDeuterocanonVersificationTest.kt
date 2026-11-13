package net.bible.service.sword.mybible

import net.bible.android.control.navigation.DocumentBibleBooks
import net.bible.service.db.exec
import net.bible.service.db.openSqlite
import net.bible.service.sword.SwordContentFacade
import net.bible.sharedcore.platform.AppDirectories
import net.bible.sharedlogic.platform.ReadOnlyModuleDbOpener
import net.bible.sharedlogic.platform.host.HostReadOnlyModuleDbOpener
import okio.Path.Companion.toPath
import org.crosswire.common.activate.Activator
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.versification.BibleBook
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Issue #2747: MyBible Bibles with deuterocanonical books must keep every book and verse. The fixtures
 * reproduce the book numbers and chapter-end verse numbers of real MyBible modules (resources
 * `mybible/<name>-chapter-bounds.txt`, numbers only); each verse's text is "<book number> <chapter>:<verse>".
 */
class MyBibleDeuterocanonVersificationTest {
    /** NRSV: separate deuterocanonical books, Greek Esther (192), Psalm 151 (232), Bel (345), Prayer of Manasseh (790). */
    @Test
    fun nrsvLayoutKeepsEveryDeuterocanonicalBookAndVerse() = withBible("nrsv") { book ->
        assertEquals("NRSVA", book.versification.name)
        val books = DocumentBibleBooks(book).getBookList()
        val expected = listOf(
            BibleBook.TOB, BibleBook.JDT, BibleBook.ESTH_GR, BibleBook.WIS, BibleBook.SIR, BibleBook.BAR,
            BibleBook.PR_AZAR, BibleBook.SUS, BibleBook.BEL, BibleBook.MACC1, BibleBook.MACC2, BibleBook.MACC3,
            BibleBook.MACC4, BibleBook.ESD1, BibleBook.ESD2, BibleBook.PR_MAN, BibleBook.ADD_PS,
        )
        assertEquals(emptyList(), expected - books.toSet())
        assertVerses(book,
            "Bel.1.42" to "345 1:42", "AddPs.1.7" to "232 1:7", "PrMan.1.15" to "790 1:15", "1Esd.9.55" to "165 9:55",
            "EsthGr.10.13" to "192 10:13", "3Macc.7.23" to "466 7:23", "4Macc.18.24" to "467 18:24",
            "2Esd.7.140" to "468 7:140", "PrAzar.1.68" to "305 1:68", "Sus.1.64" to "325 1:64", "Sir.51.30" to "280 51:30",
        )
    }

    /** LXXe: Septuagint psalm (with Psalm 151) and Jeremiah numbering, Epistle of Jeremiah, 3 and 4 Maccabees. */
    @Test
    fun septuagintLayoutSelectsLxxAndKeepsItsBooks() = withBible("lxxe") { book ->
        assertEquals("LXX", book.versification.name)
        val books = DocumentBibleBooks(book).getBookList()
        val expected = listOf(BibleBook.EP_JER, BibleBook.BEL, BibleBook.MACC3, BibleBook.MACC4, BibleBook.ESD1, BibleBook.PR_MAN)
        assertEquals(emptyList(), expected - books.toSet())
        assertVerses(book,
            "Ps.151.7" to "230 151:7", "Ps.118.176" to "230 118:176", "Jer.28.64" to "300 28:64", "EpJer.1.73" to "315 1:73",
            "Bel.1.42" to "345 1:42", "4Macc.18.24" to "467 18:24", "1Esd.9.55" to "165 9:55", "PrMan.1.14" to "790 1:14",
        )
    }

    /**
     * NRSVCE: Vulgate-style Daniel (3:24-90 inside chapter 3, Susanna 13, Bel 14), Esther 10:4-13 and Baruch 6.
     * NRSVA keeps every protocanonical verse; the Catholic systems that hold Daniel 13-14 would drop Joel 2:28-32
     * and Malachi 4 (they number them Joel 3 and Malachi 3:19-24), so the Greek additions are what is lost.
     */
    @Test
    fun catholicLayoutKeepsEveryProtocanonicalVerse() = withBible("nrsvce") { book ->
        assertEquals("NRSVA", book.versification.name)
        assertVerses(book,
            "Joel.2.32" to "360 2:32", "Joel.3.21" to "360 3:21", "Mal.4.6" to "460 4:6", "Dan.12.13" to "340 12:13",
            "Bar.6.72" to "320 6:72", "1Macc.16.24" to "462 16:24", "Esth.10.3" to "190 10:3",
        )
        // Daniel 13-14 have no place in NRSVA (see MyBibleVersificationDetectionTest for the full loss).
        assertEquals(12, book.versification.getLastChapter(BibleBook.DAN))
    }

    /**
     * Installations detected while 345 meant AddDan (betas 1263-1270) may be pinned to Luther, the only system
     * with AddDan. Book 345 must stay readable there, and AddDan keys must find book 345 again.
     */
    @Test
    fun lutherPinnedModuleKeepsBook345AsAdditionsToDaniel() = withBible("nrsv", pinned = "Luther") { book ->
        assertEquals("Luther", book.versification.name)
        assertTrue(BibleBook.ADD_DAN in DocumentBibleBooks(book).getBookList())
        assertVerses(book, "AddDan.1.1" to "345 1:1", "AddDan.1.42" to "345 1:42", "Dan.12.13" to "340 12:13")
    }

    private fun assertVerses(book: SwordBook, vararg refs: Pair<String, String>) {
        val actual = refs.associate { (ref, _) ->
            val key = book.getKey(ref)
            ref to (if (key.getOsisRef() == ref) book.getRawText(key) else "outside ${book.versification.name}: ${key.getOsisRef()}")
        }
        assertEquals(refs.toMap(), actual)
    }

    private fun withBible(bounds: String, pinned: String? = null, check: (SwordBook) -> Unit) {
        val root = Files.createTempDirectory("mybible-deuterocanon").toFile()
        var book: SwordBook? = null
        val dirs = object : AppDirectories {
            override val internalFilesDir = root.resolve("internal").path.toPath()
            override val modulesDir = root.path.toPath()
            override val manualInstallDir = modulesDir
            override val manualInstallDir2 = modulesDir
            override val manualReadingPlanDir = modulesDir
        }
        startKoin { modules(module {
            single<AppDirectories> { dirs }
            single<ReadOnlyModuleDbOpener> { HostReadOnlyModuleDbOpener() }
        }) }
        try {
            val file = root.resolve("Deuterocanon_$bounds.SQLite3")
            openSqlite(file.path).use { db ->
                db.exec("CREATE TABLE info (name TEXT, value TEXT)")
                db.exec("INSERT INTO info VALUES ('description', 'Deuterocanon fixture')")
                db.exec("CREATE TABLE verses (book_number INTEGER, chapter INTEGER, verse INTEGER, text TEXT, PRIMARY KEY (book_number, chapter, verse))")
                val lines = javaClass.getResourceAsStream("/mybible/$bounds-chapter-bounds.txt")!!.bufferedReader().use { it.readLines() }
                db.exec("BEGIN")
                lines.filterNot { it.startsWith("#") || it.isBlank() }.forEach { line ->
                    val (number, counts) = line.split(":")
                    counts.split(",").forEachIndexed { index, lastVerse ->
                        db.exec(
                            "WITH RECURSIVE n(v) AS (SELECT 1 UNION ALL SELECT v + 1 FROM n WHERE v < ?) " +
                                "INSERT INTO verses SELECT ?, ?, v, ? || ' ' || ? || ':' || v FROM n",
                            lastVerse.toInt(), number.toInt(), index + 1, number, index + 1,
                        )
                    }
                }
                db.exec("COMMIT")
            }
            pinned?.let { MyBibleVersificationStore.restore(file.path.toPath(), it) }
            addMyBibleBook(file.path.toPath())
            book = Books.installed().getBook("MyBible-Deuterocanon_$bounds") as SwordBook
            check(book)
            assertTrue(book.getGlobalKeyList().getCardinality() > 0)
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

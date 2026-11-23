package net.bible.service.sword.mybible

import fi.sykero.kdom.Element
import net.bible.service.db.exec
import net.bible.service.db.openSqlite
import net.bible.sharedcore.platform.AppDirectories
import net.bible.sharedlogic.platform.ReadOnlyModuleDbOpener
import net.bible.sharedlogic.platform.host.HostReadOnlyModuleDbOpener
import okio.Path.Companion.toPath
import org.crosswire.common.activate.Activator
import org.crosswire.jsword.book.BookData
import org.crosswire.jsword.book.sword.SwordBook
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Generated MyBible data: no copyrighted module or reporter attachment is needed. */
class MyBibleMarkupTest {
    @Test
    fun storyReferencesBecomeNamedScriptureLinksBeforeTheVerse() = withBible { book ->
        val fragment = BookData(book, book.getKey("Matt.3.1")).osisFragment
        val references = elements(fragment).filter { it.name == "reference" }
        assertEquals(listOf("Mark.1.2-Mark.1.8", "Luke.3.1-Luke.3.18", "John.1.19-John.1.28"),
            references.map { it.getAttributeValue("osisRef") })
        references.forEach { assertFalse(it.value.contains(Regex("480|490|500"))) }
        val title = elements(fragment).single { it.name == "title" && it.getAttributeValue("subType") == "x-preverse" }
        assertTrue(title.value.contains("The preaching"))
        assertEquals(3, elements(title).count { it.name == "reference" })
        val verse = elements(fragment).single { it.name == "verse" }
        assertEquals("In those days.", verse.value)
        assertTrue(verse.parentElement!!.content.indexOf(title) < verse.parentElement!!.content.indexOf(verse))
    }

    @Test
    fun leadingParagraphBreakPrecedesVerseMarkerButInlineBreakStaysInside() = withBible { book ->
        val fragment = BookData(book, book.getKey("Matt.3.2")).osisFragment
        val verse = elements(fragment).single { it.name == "verse" }
        val breaks = elements(fragment).filter { it.name.equals("pb", ignoreCase = true) }
        assertEquals(2, breaks.size)
        assertFalse(elements(verse).contains(breaks.first()), "Leading break must not follow the generated verse number")
        assertTrue(elements(verse).contains(breaks.last()), "An inline paragraph must not move out of its verse")
        assertEquals("Repent.Second paragraph.", verse.value)
        val siblings = verse.parentElement!!.content
        assertTrue(siblings.indexOf(breaks.first().parentElement!!) < siblings.indexOf(verse))
    }

    @Test
    fun inlineChapterReferencesAreConvertedWithoutChangingVerseText() = withBible { book ->
        val fragment = BookData(book, book.getKey("Ps.1.1")).osisFragment
        val reference = elements(fragment).single { it.name == "reference" }
        assertEquals("Ps.1-Ps.40", reference.getAttributeValue("osisRef"))
        assertFalse(reference.value.contains("230"))
        assertTrue(fragment.value.contains("Blessed"))
    }

    @Test
    fun structuralStoriesAreNotHiddenAsTitlesAndMalformedReferencesDoNotBlankText() = withBible { book ->
        val fragment = BookData(book, book.getKey("Matt.3.3")).osisFragment
        assertFalse(elements(fragment).any { it.name == "title" && it.getAttributeValue("canonical") == "false" },
            "Structural story markup must not be hidden by the section-title setting")
        val verse = elements(fragment).single { it.name == "verse" }
        assertTrue(elements(verse).any { it.name == "pb" })
        assertTrue(verse.value.contains("Formatted heading"))
        assertTrue(verse.value.contains("99999 1:1"))
        assertTrue(verse.value.contains("not a reference"))
        assertTrue(verse.value.contains("Still readable."))
        assertEquals("Gen.1.31-Gen.2.1", elements(verse).single { it.name == "reference" }.getAttributeValue("osisRef"))
    }

    @Test
    fun continuationReferencesInheritTheMostRecentBookWithinEachCrossReference() = withBible { book ->
        val fragment = BookData(book, book.getKey("Matt.3.4")).osisFragment
        assertEquals(listOf("Mark.1.2", "Mark.3.4", "Luke.2.1", "Luke.4.2"),
            elements(fragment).filter { it.name == "reference" }.map { it.getAttributeValue("osisRef") })
        assertTrue(fragment.value.contains("1:1"), "A new x tag must not inherit the previous tag's book")
        assertTrue(fragment.value.contains("99999 1:1; 2:2"), "Unknown books must not inherit a known book")
    }

    @Test
    fun spacedChapterRangesKeepTheInheritedBook() = withBible { book ->
        val fragment = BookData(book, book.getKey("Matt.3.10")).osisFragment
        assertEquals(listOf("Ps.1.1", "Ps.2-Ps.4", "Ps.5.1"),
            elements(fragment).filter { it.name == "reference" }.map { it.getAttributeValue("osisRef") })
    }

    @Test
    fun malformedExplicitReferencesStillUpdateTheContinuationBook() = withBible { book ->
        val fragment = BookData(book, book.getKey("Matt.3.9")).osisFragment
        assertEquals(listOf("Mark.1.2", "Luke.5.6"),
            elements(fragment).filter { it.name == "reference" }.map { it.getAttributeValue("osisRef") })
        assertTrue(fragment.value.contains("490 3:4a"), "Malformed references must stay visible")
        assertTrue(fragment.value.contains("99999 bad; 2:2"), "An unknown explicit book must replace Luke")
    }

    @Test
    fun missingVersificationLeavesCrossReferenceMarkupUnchanged() = withBible { book ->
        val key = book.getKey("Matt.3.4")
        book.bookMetaData.setProperty("Versification", "MissingMyBibleVersification")
        assertEquals("<x>480 1:2; 3:4; 490 2:1; 4:2; 99999 1:1; 2:2</x><x>1:1</x>Continuations.",
            book.getRawText(key))
    }

    @Test
    fun missingVersificationLeavesBibleAnchorsUnchanged() = withBible { book ->
        val key = book.getKey("Matt.3.5")
        book.bookMetaData.setProperty("Versification", "MissingMyBibleVersification")
        assertEquals("<a href=\"B:480 1:2\">Mark</a> Still readable.", book.getRawText(key))
    }

    @Test
    fun booksOutsideTheVersificationHaveReadableNamesWithoutLinks() = withBible { book ->
        book.bookMetaData.setProperty("Versification", "KJV")
        val fragment = BookData(book, book.getKey("Matt.3.6")).osisFragment
        assertEquals(emptyList(), elements(fragment).filter { it.name == "reference" })
        assertTrue(fragment.value.contains("Tobit 1:1; Tobit 2:2"), fragment.value)
        assertTrue(fragment.value.contains("99999 1:1"), "Unmapped books must remain readable")
    }

    @Test
    fun storiesStartingWithCrossReferencesRemainPreverseHeadings() = withBible { book ->
        val fragment = BookData(book, book.getKey("Matt.3.7")).osisFragment
        val title = elements(fragment).single { it.name == "title" && it.getAttributeValue("subType") == "x-preverse" }
        assertTrue(title.value.endsWith("The preaching"))
        assertEquals(listOf("Mark.1.2", "Luke.3.4"),
            elements(title).filter { it.name == "reference" }.map { it.getAttributeValue("osisRef") })
        val verse = elements(fragment).single { it.name == "verse" }
        assertEquals("Verse text.", verse.value)
        assertTrue(verse.parentElement!!.content.indexOf(title) < verse.parentElement!!.content.indexOf(verse))
    }

    @Test
    fun leadingCrossReferencesDoNotTurnStructuralStoriesIntoHeadings() = withBible { book ->
        val fragment = BookData(book, book.getKey("Matt.3.8")).osisFragment
        assertFalse(elements(fragment).any { it.name == "title" && it.getAttributeValue("canonical") == "false" })
        val verse = elements(fragment).single { it.name == "verse" }
        assertTrue(elements(verse).any { it.name == "pb" })
        assertTrue(verse.value.contains("Formatted heading"))
    }

    private fun elements(root: Element): List<Element> =
        (root.descendants as Iterable<*>).filterIsInstance<Element>()

    private fun withBible(check: (SwordBook) -> Unit) {
        val root = Files.createTempDirectory("mybible-markup").toFile()
        var state: SqliteVerseBackendState? = null
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
            val file = root.resolve("Markup.SQLite3")
            openSqlite(file.path).use { db ->
                db.exec("CREATE TABLE info (name TEXT, value TEXT)")
                db.exec("CREATE TABLE verses (book_number INTEGER, chapter INTEGER, verse INTEGER, text TEXT)")
                db.exec("INSERT INTO verses VALUES (470, 3, 1, 'In those days.')")
                db.exec("INSERT INTO verses VALUES (470, 3, 2, ' <pb/> Repent.<pb/>Second paragraph.')")
                db.exec("INSERT INTO verses VALUES (230, 1, 1, '<x>230 1-40</x>Blessed')")
                db.exec("CREATE TABLE stories (book_number INTEGER, chapter INTEGER, verse INTEGER, title TEXT)")
                db.exec("INSERT INTO stories VALUES (470, 3, 1, '(<x>480 1:2-8; 490 3:1-18; 500 1:19-28</x>) The preaching')")
                db.exec("INSERT INTO verses VALUES (470, 3, 3, '<x>99999 1:1; not a reference; 10 999999999999999999:1; 10 1:31-2:1</x>Still readable.')")
                db.exec("INSERT INTO stories VALUES (470, 3, 3, '<pb/><i>Formatted heading</i>')")
                db.exec("INSERT INTO verses VALUES (470, 3, 4, '<x>480 1:2; 3:4; 490 2:1; 4:2; 99999 1:1; 2:2</x><x>1:1</x>Continuations.')")
                db.exec("INSERT INTO verses VALUES (470, 3, 5, '<a href=\"B:480 1:2\">Mark</a> Still readable.')")
                db.exec("INSERT INTO verses VALUES (470, 3, 6, '<x>170 1:1; 2:2; 99999 1:1</x>Unsupported books.')")
                db.exec("INSERT INTO verses VALUES (470, 3, 7, 'Verse text.')")
                db.exec("INSERT INTO stories VALUES (470, 3, 7, ' <x>480 1:2</x> <x>490 3:4</x> The preaching')")
                db.exec("INSERT INTO verses VALUES (470, 3, 8, 'Verse text.')")
                db.exec("INSERT INTO stories VALUES (470, 3, 8, '<x>480 1:2</x> <pb/><i>Formatted heading</i>')")
                db.exec("INSERT INTO verses VALUES (470, 3, 9, '<x>480 1:2; 490 3:4a; 5:6; 99999 bad; 2:2</x>Malformed continuation.')")
                db.exec("INSERT INTO verses VALUES (470, 3, 10, '<x>230 1:1; 2 - 4; 5:1</x>Chapter range.')")
            }
            state = SqliteVerseBackendState(file.path.toPath())
            book = SwordBook(state.getBookMetaData(), SqliteBackend(state, state.getBookMetaData()))
            check(book)
        } finally {
            book?.let { Activator.deactivate(it) }
            state?.close()
            stopKoin()
            root.deleteRecursively()
        }
    }
}

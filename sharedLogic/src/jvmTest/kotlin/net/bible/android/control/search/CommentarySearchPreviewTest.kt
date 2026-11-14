/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */

package net.bible.android.control.search

import java.io.File
import java.lang.reflect.Proxy
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import net.bible.android.control.navigation.DocumentBibleBooksFactory
import net.bible.android.control.page.window.WindowControl
import net.bible.sharedcore.platform.AppCoroutineScope
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.platform.CoreStrings
import net.bible.sharedcore.search.MultiSearchResults
import net.bible.sharedcore.search.SearchBibleSection
import net.bible.sharedcore.search.SearchRequest
import net.bible.sharedcore.search.SearchType
import net.bible.sharedcore.search.StyledText
import net.bible.sharedlogic.search.CommonSearchHighlighter
import okio.Path.Companion.toOkioPath
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.SwordBookPath
import org.crosswire.jsword.index.IndexManagerFactory
import org.crosswire.jsword.versification.BibleBook
import org.crosswire.jsword.versification.system.Versifications
import org.crosswire.jsword.passage.Verse

/**
 * #4092: searching a commentary gave result cards with no excerpt (the commentary fragment has no `<verse>` wrapper,
 * which is all the highlighter read) and every hit of a long, linked entry was read and rendered in full.
 *
 * The fixture is a real on-disk RawCom commentary written for the test: a long entry linked to John 15:1-2 and a
 * short one at John 15:5, all mentioning "vine".
 */
class CommentarySearchPreviewTest {
    private val dir = Files.createTempDirectory("commentary-search").toFile()
    private var savedPath: Array<okio.Path> = emptyArray()
    private val indexed = mutableListOf<Book>()

    private val strings: CoreStrings = Proxy.newProxyInstance(
        CoreStrings::class.java.classLoader, arrayOf(CoreStrings::class.java),
    ) { _, m, _ -> m.name } as CoreStrings

    private inline fun <reified T : Any> blank(): T {
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val field = unsafeClass.getDeclaredField("theUnsafe").apply { isAccessible = true }
        return unsafeClass.getMethod("allocateInstance", Class::class.java).invoke(field.get(null), T::class.java) as T
    }

    private val longEntry = buildString {
        append("<p>Christ is the true vine, and his Father the husbandman.</p> ")
        repeat(80) { append("The branches are pruned that they may bring forth more fruit in their season. ") }
        append("<p>Abide in me, saith the vine, and I in you.</p>")
    }
    private val shortEntry = "<p>I am the vine, ye are the branches.</p>"
    /** Like Matthew Henry's chapter entries: a heading and an introduction that JSword puts before the <verse>. */
    private val introducedEntry = "<div type=\"x-milestone\" subType=\"x-preverse\" sID=\"pv1\"/>" +
        "<title>The Pruner</title> The pruner cuts the dead wood." +
        "<div type=\"x-milestone\" subType=\"x-preverse\" eID=\"pv1\"/> Every branch that beareth not fruit he taketh away."

    @BeforeTest fun setUp() {
        writeCommentary()
        savedPath = SwordBookPath.getAugmentPath()
        SwordBookPath.setAugmentPath(arrayOf(dir.toOkioPath(), kjvFixtureRoot().toOkioPath()))
    }

    @AfterTest fun tearDown() {
        indexed.forEach { book ->
            runCatching { IndexManagerFactory.getIndexManager().deleteIndex(book) }
            book.resetSearcher()
        }
        Books.installed().getBook(COMMENTARY)?.let { Books.installed().removeBook(it) }
        SwordBookPath.setAugmentPath(savedPath)
        dir.deleteRecursively()
    }

    private fun kjvFixtureRoot(): File {
        val root = File("../jsword/src/commonTest/resources/k4-modules/space dir/ä")
        check(File(root, "mods.d/K4RawText.conf").isFile) { "K4 fixture not found from ${File(".").absolutePath}" }
        return root
    }

    /** A RawCom module: `nt` holds the entries, `nt.vss` one (offset, size) pair per NT ordinal; linked = same pair. */
    private fun writeCommentary() {
        File(dir, "mods.d").mkdirs()
        File(dir, "mods.d/${COMMENTARY.lowercase()}.conf").writeText(
            "[$COMMENTARY]\nDataPath=./modules/comments/rawcom/$COMMENTARY/\nModDrv=RawCom\nDescription=Test commentary\n" +
                "SourceType=OSIS\nLang=en\nVersification=KJV\nEncoding=UTF-8\n",
        )
        val data = File(dir, "modules/comments/rawcom/$COMMENTARY").apply { mkdirs() }
        val v11n = Versifications.instance().getVersification("KJV")!!
        fun ntOrdinal(chapter: Int, verse: Int) = v11n.getTestamentOrdinal(v11n.getOrdinal(Verse(v11n, BibleBook.JOHN, chapter, verse)))
        val text = java.io.ByteArrayOutputStream()
        val entries = mutableMapOf<Int, Pair<Int, Int>>()
        fun add(content: String, vararg ordinals: Int) {
            val bytes = content.toByteArray()
            val at = text.size()
            text.write(bytes)
            ordinals.forEach { entries[it] = at to bytes.size }
        }
        add(longEntry, ntOrdinal(15, 1), ntOrdinal(15, 2))
        add(shortEntry, ntOrdinal(15, 5))
        add(introducedEntry, ntOrdinal(15, 6))
        val count = v11n.getTestamentOrdinal(v11n.maximumOrdinal()) + 1
        val vss = ByteBuffer.allocate(6 * count).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until count) {
            val (offset, size) = entries[i] ?: (0 to 0)
            vss.putInt(offset).putShort(size.toShort())
        }
        File(data, "nt").writeBytes(text.toByteArray())
        File(data, "nt.vss").writeBytes(vss.array())
        File(data, "ot").writeBytes(ByteArray(0))
        File(data, "ot.vss").writeBytes(ByteArray(6))
    }

    private fun search(initials: String, query: String): MultiSearchResults {
        val book = checkNotNull(Books.installed().getBook(initials)) { "$initials not installed" }
        IndexManagerFactory.getIndexManager().scheduleIndexCreation(book)
        indexed += book
        val control = SearchControl(blank<DocumentBibleBooksFactory>(), blank<WindowControl>(), blank<AppCoroutineScope>())
        val service = BibleSearchServiceImpl(control, CommonSearchHighlighter, Proxy.newProxyInstance(AppSettings::class.java.classLoader, arrayOf(AppSettings::class.java)) { _, m, _ -> error("unexpected ${m.name}") } as AppSettings, strings)
        return runBlocking {
            service.searchMulti(
                SearchRequest(
                    query = query, searchType = SearchType.ALL_WORDS, bibleSection = SearchBibleSection.ALL,
                    translationIds = listOf(initials), currentBookName = "",
                ),
            )
        }
    }

    private val StyledText.text get() = runs.joinToString("") { it.text }

    @Test fun commentaryResultsCarryAnExcerptWithTheMatchAndTheDocument() {
        val results = search(COMMENTARY, "vine")
        val rows = results.main + results.other
        assertEquals(listOf("John 15:1", "John 15:2", "John 15:5"), rows.map { it.referenceName })
        for (row in rows) {
            assertEquals(listOf(COMMENTARY), row.matches.map { it.abbreviation }, row.referenceName)
            val preview = row.primaryPreview
            assertTrue(preview.runs.any { it.highlight && it.text.equals("vine", ignoreCase = true) },
                "${row.referenceName}: the excerpt shows the highlighted match, was '${preview.text}'")
        }
        assertEquals("I am the vine, ye are the branches.", rows.last().primaryPreview.text.trim())
    }

    @Test fun aLongCommentaryEntryIsCutToAnExcerptAroundTheMatch() {
        val rows = search(COMMENTARY, "abide").let { it.main + it.other }
        val preview = rows.first().primaryPreview.text
        assertTrue(preview.length <= SEARCH_EXCERPT_MAX, "excerpt of ${preview.length} chars: '$preview'")
        assertTrue(preview.startsWith("…"), "the cut start is marked: '$preview'")
        assertTrue(preview.contains("Abide in me"), preview)
    }

    @Test fun textBeforeTheVerseElementIsPartOfTheExcerpt() {
        val rows = search(COMMENTARY, "pruner").let { it.main + it.other }
        assertEquals(listOf("John 15:6"), rows.map { it.referenceName })
        val preview = rows.single().primaryPreview
        assertTrue(preview.runs.any { it.highlight && it.text.equals("pruner", ignoreCase = true) }, preview.text)
    }

    @Test fun bibleVersePreviewsStayWhole() {
        val rows = search("K4RawText", "beginning").let { it.main + it.other }
        assertTrue(rows.isNotEmpty())
        rows.forEach { row -> assertTrue(!row.primaryPreview.text.contains("…"), row.primaryPreview.text) }
    }

    private companion object {
        const val COMMENTARY = "Com4092"
        /** The excerpt budget plus the two ellipses. */
        const val SEARCH_EXCERPT_MAX = net.bible.sharedcore.search.SEARCH_EXCERPT_LENGTH + 2
    }
}

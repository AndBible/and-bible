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

package net.bible.service.sword.epub

import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.launch
import net.bible.android.control.page.contentJson
import net.bible.test.testCoreStrings
import net.bible.android.platform.AndroidAppEnvironment
import net.bible.android.SharedConstants
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.BibleApplication.Companion.application
import org.crosswire.jsword.book.sword.SwordBookPath
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Regression tests for the crash where the reading-progress feature (added in 5.1.1105,
 * widely shipped in 5.1.1108) read *every* fragment of an EPUB via [EpubBackendState.totalCharacters].
 * When the fragment database and the optimized fragment files were out of sync — a fragment row
 * present but its `optimized/NNN.xhtml.gz` file missing — the unguarded read threw
 * FileNotFoundException and crashed the whole app on document load (i.e. on startup, since the
 * last-viewed document is restored). Reported in OSTicket #3364 and #3365.
 *
 * These build a real minimal EPUB, let [EpubBackendState] optimize it, then delete a fragment
 * file to reproduce the inconsistency and assert the reader degrades gracefully instead of throwing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class EpubBackendStateTest {
    private lateinit var epubRoot: File
    private lateinit var originalDownloadDir: File
    private val createdDbFilenames = mutableListOf<String>()

    @Before
    fun setUp() {
        originalDownloadDir = SwordBookPath.getSwordDownloadDir()
        SwordBookPath.setDownloadDir(SharedConstants.modulesDir)
        epubRoot = File(SharedConstants.modulesDir, "epub").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        try {
            epubRoot.deleteRecursively()
            createdDbFilenames.forEach { application.deleteDatabase(it) }
        } finally {
            SwordBookPath.setDownloadDir(originalDownloadDir)
        }
    }

    private fun chapterXhtml(title: String, body: String): String = """
        <?xml version="1.0" encoding="UTF-8"?>
        <html xmlns="http://www.w3.org/1999/xhtml">
          <head><title>$title</title></head>
          <body><p>$body</p></body>
        </html>
    """.trimIndent()

    /** Build a minimal two-chapter EPUB directory and return it. */
    private fun buildEpub(name: String): File {
        val epubDir = File(epubRoot, name)
        File(epubDir, "META-INF").mkdirs()
        File(epubDir, "META-INF/container.xml").writeText(
            """
            <?xml version="1.0"?>
            <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
              <rootfiles>
                <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
              </rootfiles>
            </container>
            """.trimIndent()
        )
        val oebps = File(epubDir, "OEBPS").apply { mkdirs() }
        File(oebps, "content.opf").writeText(
            """
            <?xml version="1.0"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="2.0" unique-identifier="id">
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                <dc:title>Test Tabletalk</dc:title>
                <dc:language>en</dc:language>
              </metadata>
              <manifest>
                <item id="toc" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
                <item id="ch1" href="ch1.xhtml" media-type="application/xhtml+xml"/>
                <item id="ch2" href="ch2.xhtml" media-type="application/xhtml+xml"/>
              </manifest>
              <spine>
                <itemref idref="ch1"/>
                <itemref idref="ch2"/>
              </spine>
            </package>
            """.trimIndent()
        )
        File(oebps, "toc.ncx").writeText("""
            <ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
              <navMap>
                <navPoint id="one" playOrder="1"><navLabel><text>Chapter One</text></navLabel><content src="ch1.xhtml"/></navPoint>
                <navPoint id="two" playOrder="2"><navLabel><text>Chapter Two</text></navLabel><content src="ch2.xhtml"/></navPoint>
              </navMap>
            </ncx>
        """.trimIndent())
        // Plain prose (no bible references) so anchor building does not need module registration.
        File(oebps, "ch1.xhtml").writeText(chapterXhtml("Chapter One", "This is the first chapter with some readable content."))
        File(oebps, "ch2.xhtml").writeText(chapterXhtml("Chapter Two", "This is the second chapter with more readable content."))
        createdDbFilenames.add("epub-${epubInitials(name)}.sqlite3")
        return epubDir
    }

    private fun newState(dir: File) = EpubBackendState(dir, strings = testCoreStrings(), env = AndroidAppEnvironment(application))

    @Test
    fun suspendQueriesReadGeneratedXhtmlAndToc() = runTest {
        val state = newState(buildEpub("suspend-queries"))
        // BVA text includes the sentence splitter's separator space (107, not prose length 104).
        assertEquals(107, state.totalCharacters())
        assertEquals(107, state.totalCharacters()) // cached read
        assertEquals(listOf("Chapter One", "Chapter Two"), state.tocKeys().map { it.getName() })
        assertEquals(state.keys.map { it.getOsisRef() }, state.tocKeys().map { (it as net.bible.service.sword.BookAndKey).key.getOsisRef() })
    }

    @Test
    fun indexBuildUsesAppScopeAfterCallerCancellationAndIndexesRealXhtml() = runTest {
        val state = newState(buildEpub("app-index"))
        val book = org.crosswire.jsword.book.sword.SwordGenBook(state.getBookMetaData(), EpubBackend(state, state.getBookMetaData()))
        val books = org.crosswire.jsword.book.Books.installed()
        val appJob = kotlinx.coroutines.SupervisorJob()
        val appScope = net.bible.sharedcore.platform.AppCoroutineScope(appJob + kotlinx.coroutines.test.StandardTestDispatcher(testScheduler))
        val callerJob = kotlinx.coroutines.Job()
        try {
            books.addBook(book)
            kotlinx.coroutines.CoroutineScope(callerJob + kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)).launch {
                net.bible.service.sword.SwordDocumentFacade.ensureIndexCreation(book, appScope)
                net.bible.service.sword.SwordDocumentFacade.ensureIndexCreation(book, appScope)
            }
            callerJob.cancel()
            assertEquals(org.crosswire.jsword.index.IndexStatus.SCHEDULED, book.indexStatus)
            advanceUntilIdle()
            appJob.children.forEach { it.join() }
            assertEquals(org.crosswire.jsword.index.IndexStatus.DONE, book.indexStatus)
            val document = net.bible.android.control.page.OsisDocument(
                net.bible.android.misc.OsisFragment(net.bible.service.sword.SwordContentFacade.readOsisFragment(book, state.keys[1]), state.keys[1], book),
                book, state.keys[1],
            )
            val serialized = document.contentJson()
            assertTrue(serialized.contains("'readingProgress': ${net.bible.android.control.page.ReadingProgressInfo.forEpub(state.fragmentOffset(state.keys[1]), state.bookOrdinalSpan(), 107).asJson}"))
            assertTrue(state.fragmentOffset(state.keys[1]) > 0)
            assertEquals(0, state.fragmentOffset(state.keys.first()))
            assertEquals(state.keys.first().getOsisRef(), state.getKey("ch1", "")?.getOsisRef())
            assertEquals(null, state.getKey("absent", ""))
            val hits = state.search("first")
            assertEquals(1, hits.size)
            assertEquals(state.keys.first().getOsisRef(), hits.single().key.key.getOsisRef())
            assertTrue(hits.single().text.contains("<b>first</b>"))
            state.buildSearchIndex() // existing index still reports DONE
            assertEquals(org.crosswire.jsword.index.IndexStatus.DONE, book.indexStatus)
        } finally {
            callerJob.cancel()
            appJob.cancel()
            books.removeBook(book)
        }
    }

    @Test
    fun failedIndexBuildResetsStatusForRetry() = runTest {
        val dir = buildEpub("failed-index")
        val state = newState(dir)
        optimizedDirOf(dir).listFiles { f -> f.name.endsWith(".xhtml.gz") }!!.first().writeText("not gzip")
        state.buildSearchIndex()
        assertEquals(org.crosswire.jsword.index.IndexStatus.UNDONE, state.getBookMetaData().indexStatus)
    }

    private fun optimizedDirOf(epubDir: File) = File(epubDir, "optimized")

    @Test
    fun optimizationProducesReadableFragments() = runTest {
        val state = newState(buildEpub("intact"))
        val keys = state.keys
        assertTrue("expected fragments to be created", keys.isNotEmpty())
        // Every fragment reads back non-empty content.
        assertTrue(keys.all { state.read(it).isNotEmpty() })
        // Whole-book character count is available and positive.
        assertTrue(state.totalCharacters() > 0)
    }

    @Test
    fun readReturnsEmptyInsteadOfThrowingWhenFragmentFileMissing() {
        val epubDir = buildEpub("missing-read")
        val state = newState(epubDir)
        val keys = state.keys
        assertTrue(keys.isNotEmpty())

        // Delete one optimized fragment file, leaving its row in the database.
        val optimized = optimizedDirOf(epubDir)
        val fragFiles = optimized.listFiles { f -> f.name.endsWith(".xhtml.gz") }!!.sortedBy { it.name }
        assertTrue("fragment files should exist after optimization", fragFiles.isNotEmpty())
        assertTrue(fragFiles.first().delete())

        // read() must never throw for any key, and the orphaned fragment reads as empty.
        val results = keys.map { runCatching { state.read(it) } }
        assertTrue("read must not throw on a missing fragment file", results.all { it.isSuccess })
        assertTrue("the missing fragment must read as empty", results.any { it.getOrNull()?.isEmpty() == true })
    }

    @Test
    fun totalCharactersSkipsMissingFragmentInsteadOfCrashing() = runTest {
        val epubDir = buildEpub("missing-total")
        // Construct without touching totalCharacters, so the count is not yet cached.
        val state = newState(epubDir)
        assertTrue(state.keys.size >= 2)

        val optimized = optimizedDirOf(epubDir)
        val fragFiles = optimized.listFiles { f -> f.name.endsWith(".xhtml.gz") }!!.sortedBy { it.name }
        assertTrue(fragFiles.first().delete())

        // This is the exact call path that crashed on startup in #3364 / #3365.
        val total = state.totalCharacters()
        assertTrue("totalCharacters must survive a missing fragment", total >= 0)
    }

    /** F124: a bundled optimized.sqlite3.gz without its optimized/ fragments (the kit EPUB) must be re-optimized. */
    @Test
    fun aStaleOptimizedDatabaseWithoutFragmentsIsReoptimized() {
        val epubDir = buildEpub("stale-gz")
        newState(epubDir) // first open: optimizes and writes optimized.sqlite3.gz
        assertTrue(File(epubDir, "optimized.sqlite3.gz").exists())
        assertTrue(optimizedDirOf(epubDir).deleteRecursively())
        buildEpub("stale-gz") // optimization deletes the source files; a kit EPUB ships them beside the foreign gz
        application.deleteDatabase("epub-${epubInitials("stale-gz")}.sqlite3") // as on a fresh install of the kit file

        val state = newState(epubDir)
        val keys = state.keys
        assertTrue(keys.isNotEmpty())
        assertTrue("every fragment must read back content", keys.all { state.read(it).isNotEmpty() })
    }

    /** F124 variant: sources present, fragments gone, app database surviving: optimized again. */
    @Test
    fun missingFragmentsWithASurvivingAppDatabaseAreReoptimized() {
        val epubDir = buildEpub("stale-frags")
        newState(epubDir)
        assertTrue(optimizedDirOf(epubDir).deleteRecursively())
        buildEpub("stale-frags") // optimization deletes the source files; restore them as the user's copy would have

        val state = newState(epubDir)
        assertTrue(state.keys.isNotEmpty())
        assertTrue(state.keys.all { state.read(it).isNotEmpty() })
    }

    /** Review Focus 5: an intact optimized EPUB is reused, not optimized again on every open. */
    @Test
    fun anIntactOptimizedEpubIsNotReoptimized() {
        val epubDir = buildEpub("intact-reuse")
        newState(epubDir)
        val versionFile = File(optimizedDirOf(epubDir), "version.txt")
        val stamp = 1_000_000_000_000L
        assertTrue(versionFile.setLastModified(stamp))
        newState(epubDir)
        assertEquals("version.txt rewritten means a second optimization ran", stamp, versionFile.lastModified())
    }

    /** F124 guard: an EPUB optimized before version.txt existed has no sources and no version.txt; reuse it. */
    @Test
    fun anIntactPre2023EpubWithoutVersionFileOrSourcesIsReused() {
        val epubDir = buildEpub("pre-2023")
        newState(epubDir)
        assertTrue(File(optimizedDirOf(epubDir), "version.txt").delete())
        assertFalse(File(epubDir, "OEBPS/ch1.xhtml").exists())

        val state = newState(epubDir)
        assertTrue(File(epubDir, "optimized.sqlite3.gz").exists())
        assertFalse(File(epubDir, "optimize.lock").exists())
        assertTrue(state.keys.isNotEmpty())
        assertTrue(state.keys.all { state.read(it).isNotEmpty() })
    }
}

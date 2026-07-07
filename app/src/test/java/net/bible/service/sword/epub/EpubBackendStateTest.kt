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
class EpubBackendStateTest {
    private lateinit var epubRoot: File
    private val createdDbFilenames = mutableListOf<String>()

    @Before
    fun setUp() {
        SwordBookPath.setDownloadDir(SharedConstants.modulesDir)
        epubRoot = File(SharedConstants.modulesDir, "epub").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        epubRoot.deleteRecursively()
        // EPUB databases live in internal storage, independent of the epub dir.
        createdDbFilenames.forEach { application.deleteDatabase(it) }
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
        // Plain prose (no bible references) so anchor building does not need module registration.
        File(oebps, "ch1.xhtml").writeText(chapterXhtml("Chapter One", "This is the first chapter with some readable content."))
        File(oebps, "ch2.xhtml").writeText(chapterXhtml("Chapter Two", "This is the second chapter with more readable content."))
        createdDbFilenames.add("epub-${epubInitials(name)}.sqlite3")
        return epubDir
    }

    private fun optimizedDirOf(epubDir: File) = File(epubDir, "optimized")

    @Test
    fun optimizationProducesReadableFragments() {
        val state = EpubBackendState(buildEpub("intact"))
        val keys = state.keys
        assertTrue("expected fragments to be created", keys.isNotEmpty())
        // Every fragment reads back non-empty content.
        assertTrue(keys.all { state.read(it).isNotEmpty() })
        // Whole-book character count is available and positive.
        assertTrue(state.totalCharacters > 0)
    }

    @Test
    fun readReturnsEmptyInsteadOfThrowingWhenFragmentFileMissing() {
        val epubDir = buildEpub("missing-read")
        val state = EpubBackendState(epubDir)
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
    fun totalCharactersSkipsMissingFragmentInsteadOfCrashing() {
        val epubDir = buildEpub("missing-total")
        // Construct without touching totalCharacters, so the count is not yet cached.
        val state = EpubBackendState(epubDir)
        assertTrue(state.keys.size >= 2)

        val optimized = optimizedDirOf(epubDir)
        val fragFiles = optimized.listFiles { f -> f.name.endsWith(".xhtml.gz") }!!.sortedBy { it.name }
        assertTrue(fragFiles.first().delete())

        // This is the exact call path that crashed on startup in #3364 / #3365.
        val total = state.totalCharacters
        assertTrue("totalCharacters must survive a missing fragment", total >= 0)
    }
}

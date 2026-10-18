/*
 * Copyright (c) 2022-2026 Martin Denham, Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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

import kotlinx.coroutines.sync.Mutex
import net.bible.android.BibleApplication.Companion.application
import net.bible.android.SharedConstants
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.database.EpubDao
import net.bible.android.database.EpubFragment
import net.bible.android.database.EpubHtmlToFrag
import net.bible.android.database.StyleSheet
import net.bible.android.platform.AndroidAppEnvironment
import net.bible.android.platform.AndroidEpubStoreEdge
import net.bible.sharedlogic.platform.EpubStoreEdge
import net.bible.test.KoinFactorySnapshot
import net.bible.test.testCoreStrings
import okio.Path.Companion.toOkioPath
import org.crosswire.jsword.book.sword.SwordBookPathJvm
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * The EPUB domain reaches its blocking store edge through the [EpubStoreEdge] port, resolved at the call.
 * A backend state must therefore be constructible before Koin has started, and reads must go through
 * whatever the Koin graph binds.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class EpubStoreEdgeResolutionTest {
    private lateinit var epubRoot: File
    private lateinit var originalDownloadDir: File
    private val createdDbFilenames = mutableListOf<String>()

    @Before
    fun setUp() {
        originalDownloadDir = SwordBookPathJvm.getSwordDownloadDir()
        SwordBookPathJvm.setDownloadDir(SharedConstants.modulesDir)
        epubRoot = File(SharedConstants.modulesDir, "epub").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        try {
            epubRoot.deleteRecursively()
            createdDbFilenames.forEach { application.deleteDatabase(it) }
        } finally {
            SwordBookPathJvm.setDownloadDir(originalDownloadDir)
        }
    }

    private fun buildEpub(name: String): File {
        val dir = File(epubRoot, name)
        File(dir, "META-INF").mkdirs()
        File(dir, "META-INF/container.xml").writeText(
            """<?xml version="1.0"?>
            <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
              <rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles>
            </container>""".trimIndent()
        )
        val oebps = File(dir, "OEBPS").apply { mkdirs() }
        File(oebps, "content.opf").writeText(
            """<?xml version="1.0"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="2.0" unique-identifier="id">
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>Edge Test</dc:title><dc:language>en</dc:language></metadata>
              <manifest>
                <item id="toc" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
                <item id="ch1" href="ch1.xhtml" media-type="application/xhtml+xml"/>
              </manifest>
              <spine><itemref idref="ch1"/></spine>
            </package>""".trimIndent()
        )
        File(oebps, "toc.ncx").writeText(
            """<ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1"><navMap>
              <navPoint id="one" playOrder="1"><navLabel><text>Chapter One</text></navLabel><content src="ch1.xhtml"/></navPoint>
            </navMap></ncx>""".trimIndent()
        )
        File(oebps, "ch1.xhtml").writeText(
            """<?xml version="1.0" encoding="UTF-8"?>
            <html xmlns="http://www.w3.org/1999/xhtml"><head><title>One</title></head><body><p>Readable prose.</p></body></html>""".trimIndent()
        )
        createdDbFilenames.add("epub-${epubInitials(name)}.sqlite3")
        return dir
    }

    private fun newState(dir: File) = EpubBackendState(dir.toOkioPath(), strings = testCoreStrings(), env = AndroidAppEnvironment(application))

    /**
     * Koin cannot be stopped for this: `EpubBackendState.getBookMetaData` already resolves `AppDirectories` at
     * construction (unchanged behaviour). What the port must guarantee is that construction does not resolve the
     * edge, so the binding is replaced by one that throws on resolution.
     */
    @Test
    fun backendStateConstructionDoesNotNeedKoin() {
        val dir = buildEpub("no-edge-at-construction")
        newState(dir) // first open optimizes the module and needs the real edge; the second construction is the test
        val koin = GlobalContext.get()
        val snapshot = KoinFactorySnapshot.of<EpubStoreEdge>(koin)
        try {
            koin.loadModules(listOf(module { single<EpubStoreEdge> { error("EpubStoreEdge resolved at construction") } }), allowOverride = true)
            newState(dir)
        } finally {
            snapshot.restore()
        }
    }

    @Test
    fun fragmentReadGoesThroughTheBoundEdge() {
        val state = newState(buildEpub("bound-edge"))
        val key = state.keys.first()
        val seen = mutableListOf<Long>()
        val fake = object : EpubStoreEdge {
            override fun fragment(dao: EpubDao, id: Long): EpubFragment? { seen += id; return AndroidEpubStoreEdge.fragment(dao, id) }
            override fun fragments(dao: EpubDao) = AndroidEpubStoreEdge.fragments(dao)
            override fun lockOwner(owner: Mutex) = AndroidEpubStoreEdge.lockOwner(owner)
            override fun clear(writeDao: EpubDao) = AndroidEpubStoreEdge.clear(writeDao)
            override fun insertFragments(writeDao: EpubDao, fragments: List<EpubFragment>, originalId: String, styleSheets: Array<StyleSheet>) =
                AndroidEpubStoreEdge.insertFragments(writeDao, fragments, originalId, styleSheets)
            override fun insertHtmlToFrags(writeDao: EpubDao, epubHtmlToFrags: List<EpubHtmlToFrag>) =
                AndroidEpubStoreEdge.insertHtmlToFrags(writeDao, epubHtmlToFrags)
        }
        val koin = GlobalContext.get()
        val snapshot = KoinFactorySnapshot.of<EpubStoreEdge>(koin)
        try {
            koin.loadModules(listOf(module { single<EpubStoreEdge> { fake } }), allowOverride = true)
            assertTrue(state.read(key).contains("Readable prose."))
            assertEquals(listOf(key.getOsisRef().toLong()), seen)
        } finally {
            snapshot.restore()
        }
    }
}

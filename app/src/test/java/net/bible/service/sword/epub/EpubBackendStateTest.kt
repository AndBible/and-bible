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

import okio.Path.Companion.toOkioPath
import okio.buffer
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import net.bible.android.control.page.contentJson
import net.bible.test.testCoreStrings
import net.bible.android.platform.AndroidAppEnvironment
import net.bible.android.SharedConstants
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.BibleApplication.Companion.application
import org.crosswire.jsword.book.sword.SwordBookPathJvm
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

    private fun chapterXhtml(title: String, body: String): String = """
        <?xml version="1.0" encoding="UTF-8"?>
        <html xmlns="http://www.w3.org/1999/xhtml">
          <head><title>$title</title></head>
          <body><p>$body</p></body>
        </html>
    """.trimIndent()

    /** Build a minimal two-chapter EPUB directory and return it. */
    internal fun buildEpub(name: String): File {
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

    private fun newState(dir: File) = EpubBackendState(dir.toOkioPath(), strings = testCoreStrings(), env = AndroidAppEnvironment(application))

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
                net.bible.android.misc.OsisFragment(net.bible.service.sword.SwordContentFacade.readOsisFragment(book, state.keys[1], strings = net.bible.test.testCoreStrings()), state.keys[1], book),
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

    @Test
    fun cancelledIndexCannotBeReopenedAsDoneAndRetryIndexesBothChapters() = runTest {
        val dir = buildEpub("cancel-retry")
        var buildJob: kotlinx.coroutines.Job? = null
        var cancel = true
        val strings = object : net.bible.sharedcore.platform.CoreStrings by testCoreStrings() {
            override fun creatingIndexFor(name: String): String {
                if (cancel) buildJob!!.cancel()
                return name
            }
        }
        val state = EpubBackendState(dir.toOkioPath(), strings, AndroidAppEnvironment(application))
        buildJob = launch(start = kotlinx.coroutines.CoroutineStart.LAZY) { state.buildSearchIndex() }
        try {
            buildJob.start()
            buildJob.join()
            assertFalse(state.isIndexed)
            val reopened = newState(dir)
            assertEquals(org.crosswire.jsword.index.IndexStatus.UNDONE, reopened.getBookMetaData().indexStatus)
            cancel = false
            state.buildSearchIndex()
            assertEquals(1, state.search("first").size)
            assertEquals(1, state.search("second").size)
            assertEquals(org.crosswire.jsword.index.IndexStatus.DONE, newState(dir).getBookMetaData().indexStatus)
        } finally {
            buildJob.cancel()
        }
    }

    private fun committer(strings: net.bible.sharedcore.platform.CoreStrings = testCoreStrings()) =
        net.bible.service.installzip.DefaultInstallCommitter(
            org.koin.java.KoinJavaComponent.get(net.bible.android.control.backup.BackupControl::class.java),
            strings, AndroidAppEnvironment(application),
        )

    private fun epubArchive(name: String, token: String): File {
        val source = buildEpub("source-$name-$token")
        File(source, "OEBPS/ch1.xhtml").writeText(chapterXhtml("Chapter One", "The $token river crosses the valley."))
        return File.createTempFile("ep3-", ".epub").also { archive ->
            java.util.zip.ZipOutputStream(archive.outputStream()).use { zip ->
                source.walkTopDown().filter { it.isFile }.forEach { file ->
                    zip.putNextEntry(java.util.zip.ZipEntry(file.relativeTo(source).invariantSeparatorsPath))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
            source.deleteRecursively()
            archive.deleteOnExit()
        }
    }

    private suspend fun assertReplacementContent(state: EpubBackendState, name: String) {
        assertTrue(state.read(state.keys.first()).contains("quartz"))
        assertFalse(state.read(state.keys.first()).contains("quiet"))
        state.buildSearchIndex()
        assertEquals(1, state.search("quartz").size)
        assertTrue(state.search("quiet").isEmpty())
        val disk = EpubSearch(File(epubRoot, "$name/epub-${epubInitials(name)}-search.sqlite3").toOkioPath())
        try {
            assertEquals(1, disk.search("quartz").size)
            assertTrue(disk.search("quiet").isEmpty())
        } finally {
            disk.close()
        }
    }

    @Test
    fun sameNameCommitReplacesOpenedReaderAndPersistedSearchIndex() = kotlinx.coroutines.runBlocking<Unit> {
        val name = "ep3-replace.epub"
        val books = org.crosswire.jsword.book.Books.installed()
        createdDbFilenames.add("epub-${epubInitials(name)}.sqlite3")
        try {
            assertTrue(committer().commitEpub(epubArchive(name, "quiet"), name, true))
            val oldBook = books.getBook(epubInitials(name))!!
            val old = oldBook.epubBackend!!.state
            val page = net.bible.android.control.page.CurrentPageManager(
                org.mockito.Mockito.mock(net.bible.android.control.versification.BibleTraverser::class.java),
                org.mockito.Mockito.mock(net.bible.android.control.bookmark.BookmarkControl::class.java),
                org.mockito.Mockito.mock(net.bible.android.control.page.window.WindowControl::class.java),
            ).currentGeneralBook
            page.onlySetCurrentDocument(oldBook)
            old.buildSearchIndex()
            assertEquals(1, old.search("quiet").size)
            assertTrue(old.read(old.keys.first()).contains("quiet"))
            assertTrue(committer().commitEpub(epubArchive(name, "quartz"), name, true))
            val fresh = books.getBook(epubInitials(name))!!.epubBackend!!.state
            // Content is the primary contract, not merely a different object identity.
            assertTrue("active page renders replacement without restart", page.currentDocument!!.getRawText(fresh.keys.first()).contains("quartz"))
            assertReplacementContent(fresh, name)
            org.junit.Assert.assertNotSame(old, fresh)
            org.junit.Assert.assertThrows(IllegalStateException::class.java) { old.read(old.get(0)) }
        } finally {
            books.getBook(epubInitials(name))?.let { it.epubBackend!!.state.close(); books.removeBook(it) }
        }
    }

    @Test
    fun replacementWaitsForAppOwnedIndexBeforeUnlinkingFiles() = kotlinx.coroutines.runBlocking<Unit> {
        val name = "ep3-overlap.epub"
        val entered = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val removed = java.util.concurrent.CountDownLatch(1)
        val strings = object : net.bible.sharedcore.platform.CoreStrings by testCoreStrings() {
            override fun creatingIndexFor(name: String): String {
                entered.countDown()
                check(release.await(10, java.util.concurrent.TimeUnit.SECONDS))
                return name
            }
        }
        val books = org.crosswire.jsword.book.Books.installed()
        val listener = object : org.crosswire.jsword.book.BooksListener {
            override fun bookAdded(ev: org.crosswire.jsword.book.BooksEvent) {}
            override fun bookRemoved(ev: org.crosswire.jsword.book.BooksEvent) { removed.countDown() }
        }
        val appJob = kotlinx.coroutines.SupervisorJob()
        val appScope = net.bible.sharedcore.platform.AppCoroutineScope(appJob + kotlinx.coroutines.Dispatchers.IO)
        var replacement: kotlinx.coroutines.Deferred<Boolean>? = null
        createdDbFilenames.add("epub-${epubInitials(name)}.sqlite3")
        try {
            assertTrue(committer(strings).commitEpub(epubArchive(name, "quiet"), name, true))
            val oldBook = books.getBook(epubInitials(name))!!
            val old = oldBook.epubBackend!!.state
            net.bible.service.sword.SwordDocumentFacade.ensureIndexCreation(oldBook, appScope)
            assertTrue("real app-owned build entered", entered.await(5, java.util.concurrent.TimeUnit.SECONDS))
            val archive = epubArchive(name, "quartz")
            books.addBooksListener(listener)
            replacement = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).async {
                committer().commitEpub(archive, name, true)
            }
            assertTrue("replacement retires registered book", removed.await(5, java.util.concurrent.TimeUnit.SECONDS))
            assertFalse("replacement must wait for active producer", replacement.isCompleted)
            assertTrue("old fragments remain until producer exits", old.fragDir.toFile().listFiles()!!.isNotEmpty())
            release.countDown()
            assertTrue(replacement.await())
            appJob.children.forEach { it.join() }
            assertReplacementContent(books.getBook(epubInitials(name))!!.epubBackend!!.state, name)
            assertFalse("old producer cannot publish replacement completion", old.isIndexed)
        } finally {
            release.countDown()
            replacement?.await()
            appJob.cancel()
            appJob.join()
            books.removeBooksListener(listener)
            books.getBook(epubInitials(name))?.let { it.epubBackend!!.state.close(); books.removeBook(it) }
        }
    }

    @Test
    fun overlappingCommitAndDiscoveryCannotBypassRetiringOwner() = kotlinx.coroutines.runBlocking<Unit> {
        val name = "ep3-multiple.epub"
        val entered = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val removed = java.util.concurrent.CountDownLatch(1)
        val books = org.crosswire.jsword.book.Books.installed()
        val strings = object : net.bible.sharedcore.platform.CoreStrings by testCoreStrings() {
            override fun creatingIndexFor(name: String): String {
                entered.countDown()
                check(release.await(15, java.util.concurrent.TimeUnit.SECONDS))
                return name
            }
        }
        val listener = object : org.crosswire.jsword.book.BooksListener {
            override fun bookAdded(ev: org.crosswire.jsword.book.BooksEvent) {}
            override fun bookRemoved(ev: org.crosswire.jsword.book.BooksEvent) { removed.countDown() }
        }
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
        val tasks = mutableListOf<kotlinx.coroutines.Job>()
        createdDbFilenames.add("epub-${epubInitials(name)}.sqlite3")
        try {
            assertTrue(committer(strings).commitEpub(epubArchive(name, "quiet"), name, true))
            val oldBook = books.getBook(epubInitials(name))!!
            net.bible.service.sword.SwordDocumentFacade.ensureIndexCreation(oldBook,
                net.bible.sharedcore.platform.AppCoroutineScope(scope.coroutineContext))
            assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS))
            val b = epubArchive(name, "quartz")
            val c = epubArchive(name, "cobalt")
            books.addBooksListener(listener)
            val first = scope.async { committer().commitEpub(b, name, true) }.also { tasks.add(it) }
            assertTrue(removed.await(5, java.util.concurrent.TimeUnit.SECONDS))
            // Discovery is synchronous: it must skip a module whose replacement owns the path.
            addEpubBook(File(epubRoot, name).toOkioPath(), testCoreStrings(), AndroidAppEnvironment(application))
            assertEquals("discovery must not republish retiring A", null, books.getBook(epubInitials(name)))
            val second = scope.async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                committer().commitEpub(c, name, true)
            }.also { tasks.add(it) }
            assertFalse("second replacement waits for first path owner", second.isCompleted)
            release.countDown()
            assertTrue(first.await())
            assertTrue(second.await())
            val final = books.getBook(epubInitials(name))!!.epubBackend!!.state
            assertTrue(final.read(final.keys.first()).contains("cobalt"))
            final.buildSearchIndex()
            assertEquals(1, final.search("cobalt").size)
            assertTrue(final.search("quiet").isEmpty())
            assertTrue(final.search("quartz").isEmpty())
        } finally {
            release.countDown()
            tasks.forEach { it.join() }
            scope.coroutineContext[kotlinx.coroutines.Job]!!.cancel()
            scope.coroutineContext[kotlinx.coroutines.Job]!!.join()
            books.removeBooksListener(listener)
            books.getBook(epubInitials(name))?.let { it.epubBackend!!.state.close(); books.removeBook(it) }
        }
    }

    @Test
    fun resourceResponseRetainsOriginalBytesWhileReplacementWaitsForStreamClose() = kotlinx.coroutines.runBlocking<Unit> {
        val name = "ep3-asset.epub"
        val books = org.crosswire.jsword.book.Books.installed()
        val removed = java.util.concurrent.CountDownLatch(1)
        val listener = object : org.crosswire.jsword.book.BooksListener {
            override fun bookAdded(ev: org.crosswire.jsword.book.BooksEvent) {}
            override fun bookRemoved(ev: org.crosswire.jsword.book.BooksEvent) { removed.countDown() }
        }
        var replacement: kotlinx.coroutines.Deferred<Boolean>? = null
        var stream: java.io.InputStream? = null
        createdDbFilenames.add("epub-${epubInitials(name)}.sqlite3")
        try {
            assertTrue(committer().commitEpub(epubArchive(name, "quiet"), name, true))
            File(epubRoot, "$name/OEBPS/asset.txt").writeText("quiet asset")
            val response = net.bible.android.view.activity.page.BibleView.ModuleStylesAssetHandler()
                .handle("epub/${epubInitials(name)}/asset.txt")
            stream = response.data
            assertEquals('q'.code, stream.read())
            books.addBooksListener(listener)
            val b = epubArchive(name, "quartz")
            replacement = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).async {
                committer().commitEpub(b, name, true)
            }
            assertTrue(removed.await(5, java.util.concurrent.TimeUnit.SECONDS))
            assertFalse("replacement must retain active asset stream", replacement.isCompleted)
            assertTrue("asset must remain linked until stream closes", File(epubRoot, "$name/OEBPS/asset.txt").exists())
            assertEquals("uiet asset", stream.readBytes().toString(Charsets.UTF_8))
            stream.close()
            assertTrue(replacement.await())
        } finally {
            stream?.close()
            replacement?.await()
            books.removeBooksListener(listener)
            books.getBook(epubInitials(name))?.let { it.epubBackend!!.state.close(); books.removeBook(it) }
        }
    }

    /** A registered EPUB book whose render is held after raw read and OSIS processing, before the caller caches it. */
    private class HeldRenderBook(
        state: EpubBackendState,
        private val processed: java.util.concurrent.CountDownLatch,
        private val release: java.util.concurrent.CountDownLatch,
    ) : org.crosswire.jsword.book.sword.SwordGenBook(state.getBookMetaData(), EpubBackend(state, state.getBookMetaData())) {
        override fun getOsisIterator(key: org.crosswire.jsword.passage.Key?, allowEmpty: Boolean, allowGenTitles: Boolean): MutableIterator<fi.sykero.kdom.Content> {
            val result = super.getOsisIterator(key, allowEmpty, allowGenTitles)
            processed.countDown()
            check(release.await(15, java.util.concurrent.TimeUnit.SECONDS))
            return result
        }
    }

    private fun xml(element: fi.sykero.kdom.Element) = fi.sykero.kdom.output.XMLOutputter().outputString(element)

    @Test
    fun lateRenderOfReplacedEpubNeverPublishesIntoReplacementCache() = kotlinx.coroutines.runBlocking<Unit> {
        val name = "ep3-render-cache.epub"
        val books = org.crosswire.jsword.book.Books.installed()
        val processed = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val strings = testCoreStrings()
        val oldState = newState(buildEpub(name))
        val oldBook = HeldRenderBook(oldState, processed, release)
        val oldKey = oldState.keys.first()
        var late: kotlinx.coroutines.Deferred<fi.sykero.kdom.Element>? = null
        net.bible.service.sword.SwordContentFacade.clearCaches()
        try {
            books.addBook(oldBook)
            late = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).async {
                net.bible.service.sword.SwordContentFacade.readOsisFragment(oldBook, oldKey, strings)
            }
            assertTrue("A processed its fragment and is held before cache publication",
                processed.await(5, java.util.concurrent.TimeUnit.SECONDS))
            assertTrue(committer().commitEpub(epubArchive(name, "quartz"), name, true))
            val fresh = books.getBook(epubInitials(name))!!
            val freshKey = fresh.epubBackend!!.state.keys.first()
            assertEquals("same initials and key share a cache slot", oldKey.getOsisRef(), freshKey.getOsisRef())
            release.countDown()
            assertTrue("the late A caller still receives A's own content", xml(late.await()).contains("first chapter"))
            val rendered = xml(net.bible.service.sword.SwordContentFacade.readOsisFragment(fresh, freshKey, strings))
            assertFalse("B must never be served A's late result: $rendered", rendered.contains("first chapter"))
            assertTrue(rendered, rendered.contains("quartz"))
        } finally {
            release.countDown()
            runCatching { late?.await() }
            net.bible.service.sword.SwordContentFacade.clearCaches()
            books.getBook(epubInitials(name))?.let { it.epubBackend!!.state.close(); books.removeBook(it) }
        }
    }

    private fun generalBookPage() = net.bible.android.control.page.CurrentPageManager(
        org.mockito.Mockito.mock(net.bible.android.control.versification.BibleTraverser::class.java),
        org.mockito.Mockito.mock(net.bible.android.control.bookmark.BookmarkControl::class.java),
        org.mockito.Mockito.mock(net.bible.android.control.page.window.WindowControl::class.java),
    ).currentGeneralBook

    private inline fun <T> withDefaultGeneralBook(initials: String, block: () -> T): T {
        val settings: net.bible.sharedcore.platform.AppSettings = org.koin.java.KoinJavaComponent.get(net.bible.sharedcore.platform.AppSettings::class.java)
        val key = "default-${org.crosswire.jsword.book.BookCategory.GENERAL_BOOK.name}"
        val saved = settings.getString(key, null)
        settings.setString(key, initials)
        try { return block() } finally { settings.setString(key, saved) }
    }

    @Test
    fun activePageKeepsEpubThroughReplacementGapAndResolvesReplacement() = kotlinx.coroutines.runBlocking<Unit> {
        val name = "ep3-gap.epub"
        val otherName = "ep3-gap-other.epub"
        val books = org.crosswire.jsword.book.Books.installed()
        val removed = java.util.concurrent.CountDownLatch(1)
        val listener = object : org.crosswire.jsword.book.BooksListener {
            override fun bookAdded(ev: org.crosswire.jsword.book.BooksEvent) {}
            override fun bookRemoved(ev: org.crosswire.jsword.book.BooksEvent) { removed.countDown() }
        }
        var replacement: kotlinx.coroutines.Deferred<Boolean>? = null
        var stream: java.io.InputStream? = null
        createdDbFilenames.add("epub-${epubInitials(name)}.sqlite3")
        createdDbFilenames.add("epub-${epubInitials(otherName)}.sqlite3")
        try {
            assertTrue(committer().commitEpub(epubArchive(name, "quiet"), name, true))
            assertTrue(committer().commitEpub(epubArchive(otherName, "cobalt"), otherName, true))
            val other = books.getBook(epubInitials(otherName))!!
            withDefaultGeneralBook(other.initials) {
                val oldBook = books.getBook(epubInitials(name))!!
                val page = generalBookPage()
                page.onlySetCurrentDocument(oldBook)
                File(epubRoot, "$name/OEBPS/asset.txt").writeText("quiet asset")
                stream = oldBook.epubBackend!!.openResource("asset.txt")!!.buffer().inputStream()
                books.addBooksListener(listener)
                val b = epubArchive(name, "quartz")
                replacement = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).async {
                    committer().commitEpub(b, name, true)
                }
                assertTrue(removed.await(5, java.util.concurrent.TimeUnit.SECONDS))
                assertFalse("replacement waits for the active stream", replacement!!.isCompleted)
                // A concurrent reader during the registry gap must not adopt C permanently.
                assertEquals(epubInitials(name), page.currentDocument?.initials)
                stream!!.close()
                assertTrue(replacement!!.await())
                val fresh = books.getBook(epubInitials(name))!!
                org.junit.Assert.assertSame("active page resolves B after the gap", fresh, page.currentDocument)
                assertTrue(page.currentDocument!!.getRawText(fresh.epubBackend!!.state.keys.first()).contains("quartz"))
            }
        } finally {
            stream?.close()
            runCatching { replacement?.await() }
            books.removeBooksListener(listener)
            listOf(name, otherName).forEach { n ->
                books.getBook(epubInitials(n))?.let { it.epubBackend!!.state.close(); books.removeBook(it) }
            }
        }
    }

    @Test
    fun permanentEpubDeletionStillFallsBackToDefaultGeneralBook() = kotlinx.coroutines.runBlocking<Unit> {
        val name = "ep3-gone.epub"
        val otherName = "ep3-gone-other.epub"
        val books = org.crosswire.jsword.book.Books.installed()
        createdDbFilenames.add("epub-${epubInitials(name)}.sqlite3")
        createdDbFilenames.add("epub-${epubInitials(otherName)}.sqlite3")
        try {
            assertTrue(committer().commitEpub(epubArchive(name, "quiet"), name, true))
            assertTrue(committer().commitEpub(epubArchive(otherName, "cobalt"), otherName, true))
            val other = books.getBook(epubInitials(otherName))!!
            withDefaultGeneralBook(other.initials) {
                val oldBook = books.getBook(epubInitials(name))!!
                val page = generalBookPage()
                page.onlySetCurrentDocument(oldBook)
                oldBook.driver!!.delete(oldBook)
                org.junit.Assert.assertSame(other, page.currentDocument)
            }
        } finally {
            listOf(name, otherName).forEach { n ->
                books.getBook(epubInitials(n))?.let { it.epubBackend!!.state.close(); books.removeBook(it) }
            }
        }
    }

    @Test
    fun cssResponseReadsOriginalBytesBeforeReplacementCanUnlink() = kotlinx.coroutines.runBlocking<Unit> {
        val name = "ep3-css.epub"
        val books = org.crosswire.jsword.book.Books.installed()
        val entered = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val removed = java.util.concurrent.CountDownLatch(1)
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
        var reader: kotlinx.coroutines.Deferred<android.webkit.WebResourceResponse>? = null
        var replacement: kotlinx.coroutines.Deferred<Boolean>? = null
        val listener = object : org.crosswire.jsword.book.BooksListener {
            override fun bookAdded(ev: org.crosswire.jsword.book.BooksEvent) {}
            override fun bookRemoved(ev: org.crosswire.jsword.book.BooksEvent) { removed.countDown() }
        }
        createdDbFilenames.add("epub-${epubInitials(name)}.sqlite3")
        try {
            assertTrue(committer().commitEpub(epubArchive(name, "quiet"), name, true))
            val old = books.getBook(epubInitials(name))!!.epubBackend!!.state
            File(epubRoot, "$name/OEBPS/test.css").writeText("p { font-family: quiet; }")
            val field = EpubBackendState::class.java.getDeclaredField("dao").apply { isAccessible = true }
            val dao = field.get(old) as net.bible.android.database.EpubDao
            field.set(old, object : net.bible.android.database.EpubDao by dao {
                override suspend fun styleSheets(origId: String): List<net.bible.android.database.StyleSheet> {
                    entered.countDown()
                    check(release.await(10, java.util.concurrent.TimeUnit.SECONDS))
                    return listOf(net.bible.android.database.StyleSheet(origId, "test.css"))
                }
            })
            val key = old.keys.first().getOsisRef()
            reader = scope.async {
                net.bible.android.view.activity.page.BibleView.ModuleStylesAssetHandler()
                    .handle("epub/${epubInitials(name)}/$key/style.css")
            }
            assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS))
            val b = epubArchive(name, "quartz")
            books.addBooksListener(listener)
            replacement = scope.async { committer().commitEpub(b, name, true) }
            assertTrue(removed.await(5, java.util.concurrent.TimeUnit.SECONDS))
            assertFalse(replacement.isCompleted)
            release.countDown()
            assertEquals("p { font-family: quiet; }", reader.await().data.use { it.readBytes().toString(Charsets.UTF_8) })
            assertTrue(replacement.await())
        } finally {
            release.countDown()
            reader?.await()?.data?.close()
            replacement?.await()
            scope.coroutineContext[kotlinx.coroutines.Job]!!.cancel()
            scope.coroutineContext[kotlinx.coroutines.Job]!!.join()
            books.removeBooksListener(listener)
            books.getBook(epubInitials(name))?.let { it.epubBackend!!.state.close(); books.removeBook(it) }
        }
    }

    @Test
    fun driverDeleteRemovesBookOnceAndAllowsCallerCleanup() = kotlinx.coroutines.runBlocking<Unit> {
        val name = "ep3-driver-delete.epub"
        val books = org.crosswire.jsword.book.Books.installed()
        createdDbFilenames.add("epub-${epubInitials(name)}.sqlite3")
        try {
            assertTrue(committer().commitEpub(epubArchive(name, "quiet"), name, true))
            val book = books.getBook(epubInitials(name))!!
            book.driver!!.delete(book)
            // The caller's post-delete cleanup is reachable only if the driver returns normally.
            assertEquals(null, books.getBook(epubInitials(name)))
            assertFalse(File(epubRoot, name).exists())
            assertFalse(File(AndroidAppEnvironment(application).databasePath("epub-${epubInitials(name)}.sqlite3")).exists())
        } finally {
            books.getBook(epubInitials(name))?.let { it.epubBackend!!.state.close(); books.removeBook(it) }
        }
    }

    @Test
    fun replacementWaitsForRenderBeforeClosingMetadataAndRemovingFragments() = readerOverlap(render = true)

    @Test
    fun replacementWaitsForCharacterCountBeforeClosingMetadataAndRemovingFragments() = readerOverlap(render = false)

    private fun readerOverlap(render: Boolean) = kotlinx.coroutines.runBlocking<Unit> {
        val name = "ep3-reader-$render.epub"
        val entered = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val removed = java.util.concurrent.CountDownLatch(1)
        val books = org.crosswire.jsword.book.Books.installed()
        val listener = object : org.crosswire.jsword.book.BooksListener {
            override fun bookAdded(ev: org.crosswire.jsword.book.BooksEvent) {}
            override fun bookRemoved(ev: org.crosswire.jsword.book.BooksEvent) { removed.countDown() }
        }
        var reader: kotlinx.coroutines.Deferred<*>? = null
        var replacement: kotlinx.coroutines.Deferred<Boolean>? = null
        createdDbFilenames.add("epub-${epubInitials(name)}.sqlite3")
        try {
            assertTrue(committer().commitEpub(epubArchive(name, "quiet"), name, true))
            val old = books.getBook(epubInitials(name))!!.epubBackend!!.state
            val key = old.keys.first()
            val field = EpubBackendState::class.java.getDeclaredField("dao").apply { isAccessible = true }
            val dao = field.get(old) as net.bible.android.database.EpubDao
            fun barrier() {
                entered.countDown()
                check(release.await(10, java.util.concurrent.TimeUnit.SECONDS))
            }
            field.set(old, object : net.bible.android.database.EpubDao by dao {
                override suspend fun getFragment(id: Long): net.bible.android.database.EpubFragment? {
                    barrier()
                    return dao.getFragment(id)
                }
                override suspend fun getMeta(): net.bible.android.database.EpubMeta? {
                    barrier()
                    return dao.getMeta()
                }
            })
            reader = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).async {
                if (render) old.read(key) else old.totalCharacters()
            }
            assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS))
            val archive = epubArchive(name, "quartz")
            books.addBooksListener(listener)
            replacement = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).async {
                committer().commitEpub(archive, name, true)
            }
            assertTrue(removed.await(5, java.util.concurrent.TimeUnit.SECONDS))
            assertFalse(replacement.isCompleted)
            assertTrue("active reader retains original fragment directory", old.fragDir.toFile().isDirectory)
            assertTrue(old.fragDir.toFile().listFiles()!!.isNotEmpty())
            release.countDown()
            val result = reader.await()
            if (render) assertTrue((result as String).contains("quiet"))
            else assertTrue((result as Int) > 0)
            assertTrue(replacement.await())
            assertReplacementContent(books.getBook(epubInitials(name))!!.epubBackend!!.state, name)
        } finally {
            release.countDown()
            reader?.await()
            replacement?.await()
            books.removeBooksListener(listener)
            books.getBook(epubInitials(name))?.let { it.epubBackend!!.state.close(); books.removeBook(it) }
        }
    }

    @Test
    fun insertFailureNeverPublishesDoneOrPartialCompletionTable() = kotlinx.coroutines.runBlocking<Unit> {
        val state = newState(buildEpub("ep3-insert-failure"))
        val searchField = EpubBackendState::class.java.getDeclaredField("search").apply { isAccessible = true }
        val search = searchField.get(state) as EpubSearch
        val dbField = EpubSearch::class.java.getDeclaredField("db").apply { isAccessible = true }
        val db = dbField.get(search) as androidx.sqlite.SQLiteConnection
        var inserts = 0
        dbField.set(search, object : androidx.sqlite.SQLiteConnection by db {
            override fun prepare(sql: String): androidx.sqlite.SQLiteStatement {
                if (sql.startsWith("INSERT", ignoreCase = true) && ++inserts == 2) {
                    throw androidx.sqlite.SQLiteException("controlled insert failure")
                }
                return db.prepare(sql)
            }
        })
        try {
            state.buildSearchIndex()
            assertEquals("test reached real index insertion", 2, inserts)
            assertEquals(org.crosswire.jsword.index.IndexStatus.UNDONE, state.getBookMetaData().indexStatus)
            val reopened = EpubSearch(search.file)
            try {
                assertFalse("failed index must not survive reopen as complete", reopened.isIndexed)
            } finally {
                reopened.close()
            }
        } finally {
            state.close()
        }
    }

    private fun optimizedDirOf(epubDir: File) = File(epubDir, "optimized")

    @Test
    fun cloneKeepsRuntimeClassMetadataKeysAndSharesLiveBackendState() {
        val state = newState(buildEpub("clone"))
        val original = EpubBackend(state, state.getBookMetaData())
        val copy = original.clone() as EpubBackend
        try {
            org.junit.Assert.assertNotSame(original, copy)
            org.junit.Assert.assertSame(original.javaClass, copy.javaClass)
            org.junit.Assert.assertSame(original.bookMetaData, copy.bookMetaData)
            org.junit.Assert.assertSame(state, copy.state)
            assertEquals(original.getCardinality(), copy.getCardinality())
            assertEquals(original.get(0).getOsisRef(), copy.get(0).getOsisRef())
            state.setLastAccess(123)
            assertEquals(123L, copy.state.getLastAccess())
            copy.state.setLastAccess(456)
            assertEquals(456L, original.state.getLastAccess())
        } finally {
            state.close()
        }
    }

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

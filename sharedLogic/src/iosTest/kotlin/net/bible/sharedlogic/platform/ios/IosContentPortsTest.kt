package net.bible.sharedlogic.platform.ios

import fi.sykero.kdom.input.KdomBuilder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import androidx.sqlite.SQLiteException
import androidx.sqlite.execSQL
import net.bible.service.db.openSqlite
import net.bible.service.db.queryRows
import net.bible.android.control.speak.PERSIST_SETTINGS
import net.bible.sharedcore.platform.AppCoroutineScope
import net.bible.sharedcore.platform.IosDeviceSettings
import net.bible.sharedcore.platform.IosReadingPlanSource
import net.bible.sharedcore.platform.search.IosSearchIndexingRunner
import net.bible.sharedlogic.addons.fakeAddonBook
import net.bible.sharedlogic.sync.withMonitor
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.buffer
import okio.use
import org.crosswire.jsword.index.IndexStatus
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import platform.Foundation.NSUserDefaults
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IosContentPortsTest {
    private val fs = FileSystem.SYSTEM
    private val tmp = (NSTemporaryDirectory().trimEnd('/') + "/content-ports-" + NSUUID().UUIDString).toPath()
    private val suites = mutableListOf<String>()

    init { fs.createDirectories(tmp) }

    @AfterTest fun cleanup() {
        fs.deleteRecursively(tmp)
        suites.forEach { NSUserDefaults().removePersistentDomainForName(it) }
    }

    private fun suite(): NSUserDefaults {
        val name = "content-ports-" + NSUUID().UUIDString
        suites += name
        return NSUserDefaults(suiteName = name)
    }

    // --- IosReadingPlanSource

    @Test fun readingPlanSourceListsOnlyPropertiesFilesSorted() {
        fs.write(tmp / "b.properties") { writeUtf8("x=1") }
        fs.write(tmp / "a.v2.properties") { writeUtf8("x=2") }
        fs.write(tmp / "readme.txt") { writeUtf8("no") }
        assertEquals(listOf("a.v2", "b"), IosReadingPlanSource(tmp.toString()).builtInPlanCodes())
    }

    @Test fun readingPlanSourceDecodesIso88591LosslesslyAndMissingIsNull() {
        // 0xE4 is "a umlaut" in ISO-8859-1 but an invalid lone byte in UTF-8; 0xFF must stay one char.
        fs.write(tmp / "p.properties") { write(byteArrayOf('k'.code.toByte(), '='.code.toByte(), 0xE4.toByte(), 0xFF.toByte())) }
        val source = IosReadingPlanSource(tmp.toString())
        assertEquals("k=äÿ", source.openBuiltInPlan("p"))
        assertNull(source.openBuiltInPlan("nope"))
    }

    @Test fun readingPlanSourceMissingDirectoryIsEmpty() {
        assertEquals(emptyList(), IosReadingPlanSource((tmp / "absent").toString()).builtInPlanCodes())
    }

    // --- IosContentSource

    @Test fun contentSourceReadsBytesForPathAndFileUrlAndNamesTheFile() {
        val file = tmp / "my file.json"
        fs.write(file) { write(byteArrayOf(1, 2, 3)) }
        val source = IosContentSource()
        for (uri in listOf(file.toString(), "file://" + file.toString().replace(" ", "%20"))) {
            assertContentEquals(byteArrayOf(1, 2, 3), source.openSource(uri)!!.buffer().use { it.readByteArray() }, uri)
            assertEquals("my file.json", source.displayName(uri), uri)
        }
    }

    @Test fun contentSourceAcceptsAFileUriWithUnescapedSpaces() {
        val file = tmp / "un escaped.txt"
        fs.write(file) { writeUtf8("hi") }
        val uri = "file://$file" // a space makes NSURL.URLWithString return null
        assertEquals("hi", IosContentSource().openSource(uri)!!.buffer().use { it.readUtf8() })
        assertEquals("un escaped.txt", IosContentSource().displayName(uri))
    }

    @Test fun contentSourceMissingFileGivesNullSource() {
        assertNull(IosContentSource().openSource((tmp / "gone.bin").toString()))
    }

    @Test fun contentSourceMimeTypeFromExtension() {
        val source = IosContentSource()
        assertEquals("application/json", source.mimeType("x.json"))
        assertEquals("text/plain", source.mimeType("/a/b/notes.txt"))
        assertNull(source.mimeType("noextension"))
        assertNull(source.mimeType("weird.zzqqxx9"))
    }

    @Test fun contentSourceMimeTypeFallbackTableHasTheExchangedTypes() {
        // The explicit table, read directly (not through UTType, which answers first and varies by runtime).
        val expected = mapOf(
            "json" to "application/json", "xml" to "application/xml", "txt" to "text/plain", "html" to "text/html",
            "csv" to "text/csv", "md" to "text/markdown", "zip" to "application/zip",
            "epub" to "application/epub+zip", "pdf" to "application/pdf", "png" to "image/png",
            "jpg" to "image/jpeg", "jpeg" to "image/jpeg", "gif" to "image/gif", "db" to "application/octet-stream",
            "sqlite" to "application/vnd.sqlite3", "mp3" to "audio/mpeg", "wav" to "audio/wav", "m4a" to "audio/mp4",
            "mp4" to "video/mp4", "conf" to "text/plain", "properties" to "text/plain",
        )
        for ((extension, mime) in expected) assertEquals(mime, IosContentSource.FALLBACK_MIME_TYPES[extension], extension)
    }

    @Test fun contentSourceMimeTypeEndToEndIsStableOrLoosened() {
        val source = IosContentSource()
        // Same answer from the system database and the fallback table.
        assertEquals("application/json", source.mimeType("a.json"))
        assertEquals("application/json", source.mimeType("A.JSON"), "the extension is case-insensitive")
        // The system UTType database answers first and differs between simulator runtimes: only the media class is pinned.
        for (name in listOf("a.db", "a.wav", "a.mp3", "a.m4a", "a.sqlite", "a.mp4", "a.epub", "a.md", "a.conf")) {
            val mime = source.mimeType(name)
            val major = IosContentSource.FALLBACK_MIME_TYPES.getValue(name.substringAfterLast('.')).substringBefore('/')
            assertTrue(mime != null && mime.startsWith("$major/"), "$name -> $mime (expected a $major/* type)")
        }
    }

    // --- IosMarkdownRenderer

    @Test fun markdownRendersStrongAndListItemsAsWellFormedXml() {
        val xhtml = IosMarkdownRenderer().toXhtml("**a**\n\n- b")
        assertTrue("<strong>a</strong>" in xhtml, xhtml)
        assertTrue("<li>b</li>" in xhtml, xhtml)
        assertFalse("<body" in xhtml, xhtml)
        KdomBuilder(allowDocType = true).build("<root>$xhtml</root>")
    }

    @Test fun markdownKeepsTextEntitiesEscapedAndWellFormed() {
        val xhtml = IosMarkdownRenderer().toXhtml("a & b < c\n\nline one  \nline two\n\n---")
        KdomBuilder(allowDocType = true).build("<root>$xhtml</root>")
        assertTrue("&amp;" in xhtml, xhtml)
        assertTrue("<br />" in xhtml, xhtml)
        assertTrue("<hr />" in xhtml, xhtml)
    }

    // --- IosSpeakSettingsStore / IosNightModeState

    @Test fun speakSettingsRoundTripUnderAndroidsKeyAndEmptyWhenUnset() {
        val defaults = suite()
        val store = IosSpeakSettingsStore(IosDeviceSettings(defaults))
        assertEquals("", store.load())
        store.save("""{"a":1}""")
        assertEquals("""{"a":1}""", store.load())
        assertEquals("SpeakSettings", PERSIST_SETTINGS)
        assertEquals("""{"a":1}""", defaults.stringForKey("SpeakSettings"))
    }

    @Test fun nightModeReadsTheManualPreferenceKey() {
        val defaults = suite()
        val state = IosNightModeState(IosDeviceSettings(defaults))
        assertFalse(state.nightMode)
        defaults.setBool(true, forKey = "night_mode_pref")
        assertTrue(state.nightMode)
        defaults.setBool(false, forKey = "night_mode_pref")
        assertFalse(state.nightMode)
    }

    // --- IosSearchIndexScheduler

    private class Fixture(val scope: AppCoroutineScope = AppCoroutineScope(SupervisorJob() + Dispatchers.Default)) {
        val built = mutableListOf<String>()
        val cancelled = mutableListOf<String>()
        val gate = CompletableDeferred<Unit>()
        var failFor: String? = null
        val scheduler = IosSearchIndexScheduler(
            IosSearchIndexingRunner(begin = { 1UL }, end = {}), scope,
            build = { book ->
                if (book.initials == "A") runBlocking { gate.await() }
                withMonitor(built) { built += book.initials }
                if (book.initials == failFor) error("boom")
            },
            cancelBuild = { withMonitor(cancelled) { cancelled += it.initials } },
        )

        fun builtNow() = withMonitor(built) { built.toList() }
        fun await(condition: () -> Boolean) = runBlocking { withTimeout(10_000) { while (!condition()) delay(10) } }
    }

    @Test fun schedulerBuildsInOrderIgnoresDuplicatesAndDequeueDropsAQueuedBook() {
        val f = Fixture()
        val a = fakeAddonBook("A", "/a"); val b = fakeAddonBook("B", "/b"); val c = fakeAddonBook("C", "/c")
        b.indexStatus = IndexStatus.SCHEDULED
        f.scheduler.enqueue(a)
        f.scheduler.enqueue(a) // ignored: running/queued
        f.scheduler.enqueue(b)
        f.scheduler.enqueue(c)
        f.scheduler.dequeue("B")
        assertEquals(IndexStatus.UNDONE, b.indexStatus)
        f.gate.complete(Unit)
        f.await { f.builtNow().size >= 2 }
        assertEquals(listOf("A", "C"), f.builtNow())
    }

    @Test fun schedulerBuildsStrictlyOneAtATimeInEnqueueOrder() {
        val f = Fixture()
        val names = listOf("A", "B", "C", "D", "E", "F")
        names.forEach { f.scheduler.enqueue(fakeAddonBook(it, "/$it")) } // A blocks on the gate while the rest queue up
        f.gate.complete(Unit)
        f.await { f.builtNow().size == names.size }
        assertEquals(names, f.builtNow())
    }

    @Test fun schedulerSurvivesConcurrentEnqueueAndDequeueWhileDraining() {
        val f = Fixture()
        f.gate.complete(Unit)
        val producers = 4
        val perProducer = 50
        val dequeuedByProducer = HashMap<Int, MutableSet<String>>()
        runBlocking {
            withTimeout(60_000) {
                (0 until producers).map { p ->
                    async(Dispatchers.Default) {
                        val dequeued = mutableSetOf<String>()
                        for (i in 0 until perProducer) {
                            val name = "P$p-${i.toString().padStart(3, '0')}"
                            f.scheduler.enqueue(fakeAddonBook(name, "/$name"))
                            if (i % 7 == 3) { f.scheduler.dequeue(name); dequeued += name }
                            if (i % 5 == 0) yield()
                        }
                        withMonitor(dequeuedByProducer) { dequeuedByProducer[p] = dequeued }
                    }
                }.awaitAll()
            }
        }
        f.await { f.scheduler.isIdle }
        val built = f.builtNow()
        assertEquals(built.size, built.toSet().size, "a book was built twice")
        for (p in 0 until producers) {
            val mine = built.filter { it.startsWith("P$p-") }
            assertEquals(mine.sorted(), mine, "FIFO order broken for producer $p")
            val dequeued = dequeuedByProducer.getValue(p)
            val expected = (0 until perProducer).map { "P$p-${it.toString().padStart(3, '0')}" }.filter { it !in dequeued }
            assertTrue(mine.containsAll(expected), "a non-dequeued book was never built for producer $p")
        }
        assertTrue(f.scheduler.isIdle)
    }

    @Test fun schedulerFailedBuildLeavesUndoneAndQueueContinues() {
        val f = Fixture().apply { failFor = "A" }
        val a = fakeAddonBook("A", "/a"); val b = fakeAddonBook("B", "/b")
        a.indexStatus = IndexStatus.CREATING
        f.gate.complete(Unit)
        f.scheduler.enqueue(a)
        f.scheduler.enqueue(b)
        f.await { f.builtNow().size == 2 }
        assertEquals(listOf("A", "B"), f.builtNow())
        assertEquals(IndexStatus.UNDONE, a.indexStatus)
    }

    @Test fun schedulerAcceptsTheSameBookAgainAfterItFinished() {
        val f = Fixture()
        val a = fakeAddonBook("A", "/a")
        f.gate.complete(Unit)
        f.scheduler.enqueue(a)
        f.scheduler.enqueue(fakeAddonBook("B", "/b"))
        // The queue is FIFO under one mutex: when B has been built, A's whole run (bookkeeping included) is over.
        f.await { f.builtNow().size == 2 }
        f.scheduler.enqueue(a)
        f.await { f.builtNow().size == 3 }
        assertEquals(listOf("A", "B", "A"), f.builtNow())
    }

    @Test fun schedulerUnderExpiredBackgroundTimeCancelsTheJobAndLeavesUndone() {
        val f = Fixture()
        val expiring = IosSearchIndexScheduler(
            IosSearchIndexingRunner(begin = { expire -> expire(); 1UL }, end = {}), f.scope,
            build = { f.built += it.initials }, cancelBuild = { f.cancelled += it.initials },
        )
        val book = fakeAddonBook("E", "/e")
        book.indexStatus = IndexStatus.CREATING
        expiring.enqueue(book)
        f.await { withMonitor(f.cancelled) { f.cancelled.isNotEmpty() } && book.indexStatus == IndexStatus.UNDONE }
        assertEquals(listOf("E"), f.cancelled)
        assertTrue(f.built.isEmpty())
    }

    // --- edges (the DAO-backed edges are exercised by the Task 9 acceptance test)

    @Test fun firstLoadEdgeBlocksUntilASuspendingBodyFinishesAndPropagatesFailures() {
        assertEquals("done", IosFirstLoadEdge.awaitFirstLoad { delay(20); yield(); "done" })
        assertFailsWith<IllegalStateException> { IosFirstLoadEdge.awaitFirstLoad<Unit> { delay(5); error("boom") } }
    }

    // --- IosReadOnlyModuleDbOpener

    @Test fun readOnlyOpenerOpensAValidDatabaseReadOnly() {
        val path = tmp / "module.sqlite"
        openSqlite(path.toString()).use { it.execSQL("CREATE TABLE t(a INTEGER)"); it.execSQL("INSERT INTO t VALUES (42)") }
        IosReadOnlyModuleDbOpener().open(path).use { db ->
            assertEquals(listOf(42L), db.queryRows("SELECT a FROM t") { it.getLong(0) })
            assertFailsWith<SQLiteException> { db.execSQL("INSERT INTO t VALUES (1)") } // read-only
        }
    }

    @Test fun readOnlyOpenerThrowsSqliteExceptionForANonDatabaseFile() {
        val path = tmp / "junk.sqlite"
        fs.write(path) { writeUtf8("this is definitely not a sqlite database file, just text".repeat(20)) }
        assertFailsWith<SQLiteException> { IosReadOnlyModuleDbOpener().open(path) }
    }
}

package net.bible.sharedlogic.platform.ios

import fi.sykero.kdom.input.KdomBuilder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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

    @Test fun contentSourceMimeTypeFallbackTableCoversExchangedTypes() {
        val source = IosContentSource()
        // Exact values; the system UTType database may answer first for some, so these are the values both agree on.
        val expected = mapOf(
            "a.json" to "application/json", "a.xml" to "application/xml", "a.txt" to "text/plain", "a.html" to "text/html",
            "a.csv" to "text/csv", "a.md" to "text/markdown", "a.zip" to "application/zip",
            "a.epub" to "application/epub+zip", "a.pdf" to "application/pdf", "a.png" to "image/png",
            "a.JPG" to "image/jpeg", "a.jpeg" to "image/jpeg", "a.gif" to "image/gif", "a.db" to "application/octet-stream",
            "a.sqlite" to "application/vnd.sqlite3", "a.mp3" to "audio/mpeg", "a.wav" to "audio/wav", "a.m4a" to "audio/mp4",
            "a.mp4" to "video/mp4", "a.conf" to "text/plain", "a.properties" to "text/plain",
        )
        for ((name, mime) in expected) assertEquals(mime, source.mimeType(name), name)
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

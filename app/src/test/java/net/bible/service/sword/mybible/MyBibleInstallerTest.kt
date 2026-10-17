package net.bible.service.sword.mybible

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import net.bible.android.SharedConstants
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.service.db.exec
import net.bible.service.db.openSqlite
import net.bible.service.download.GenericFileDownloader
import net.bible.sharedcore.http.DownloadHttp
import net.bible.sharedcore.http.HttpHeaders
import net.bible.sharedcore.platform.AppEnvironment
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.platform.CoreStrings
import net.bible.sharedcore.platform.UserNotifier
import org.crosswire.common.util.HttpBody
import org.crosswire.common.util.HttpHead
import org.crosswire.common.util.HttpTransport
import org.crosswire.common.util.WebResource
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.install.InstallException
import org.crosswire.jsword.passage.DefaultLeafKeyList
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent.get
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowStatFs
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class MyBibleInstallerTest {
    private lateinit var temp: File
    private lateinit var previousBooks: List<Book>
    private var previousTransport: HttpTransport? = null
    private val root get() = File(SharedConstants.modulesDir, "mybible")
    private var manifestBytes: ByteArray? = null
    private val id = "dl15${System.nanoTime()}"
    private val base = "https://$id.test"
    private val settings get() = get<AppSettings>(AppSettings::class.java)
    private val bodies = mutableMapOf<String, ByteArray>()
    private var cancelled: CancellationException? = null
    private var downloadCancelled = false

    @Before fun setUp() {
        temp = Files.createTempDirectory("dl15").toFile()
        root.mkdirs()
        ShadowStatFs.registerStats(root.path, 4096, 10000, 10000)
        ShadowStatFs.registerStats(File(temp, "mybible").path, 4096, 10000, 10000)
        previousBooks = Books.installed().getBooks().toList()
        previousTransport = WebResource.transport
        manifestBytes = File(root, "manifest.json").takeIf { it.exists() }?.readBytes()
        WebResource.transport = object : HttpTransport {
            override suspend fun head(uri: URI, timeoutMs: Int) = HttpHead(200, null, -1, 0)
            override suspend fun get(uri: URI, timeoutMs: Int): HttpBody {
                val bytes = bodies.getValue(uri.path)
                val input = bytes.inputStream()
                return object : HttpBody {
                    override suspend fun status() = 200
                    override suspend fun contentLength() = bytes.size
                    override suspend fun prepareRead() {}
                    override suspend fun read(buf: ByteArray): Int {
                        if (uri.path == "/module") {
                            cancelled?.let { throw it }
                            if (downloadCancelled) throw org.crosswire.common.util.TransportCancelledException()
                        }
                        return input.read(buf)
                    }
                    override fun close() = input.close()
                }
            }
        }
    }

    @After fun tearDown() {
        Books.installed().getBooks().filter { it !in previousBooks }.forEach { Books.installed().removeBook(it) }
        WebResource.transport = previousTransport
        listOf("manifest", "module").forEach { settings.removeLong("last-downloaded-$base/$it") }
        root.listFiles()?.filter { it.name.contains(id) }?.forEach { it.deleteRecursively() }
        File(root, "manifest.json").let { file -> manifestBytes?.let { file.writeBytes(it) } ?: file.delete() }
        ShadowStatFs.unregisterStats(root.path)
        ShadowStatFs.unregisterStats(File(temp, "mybible").path)
        temp.deleteRecursively()
    }

    private fun sqlite(supported: Boolean = true): ByteArray {
        val file = File(temp, "fixture.SQLite3")
        openSqlite(file.path).use {
            it.exec("CREATE TABLE info (name TEXT, value TEXT)")
            it.exec("INSERT INTO info VALUES ('description', 'DL15 dictionary')")
            if (supported) {
                it.exec("CREATE TABLE dictionary (topic TEXT, definition TEXT)")
                it.exec("INSERT INTO dictionary VALUES ('alpha', 'first definition')")
            }
        }
        return file.readBytes()
    }

    private fun zip(name: String, bytes: ByteArray): ByteArray = ByteArrayOutputStream().also { out ->
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(name))
            zip.write(bytes)
            zip.closeEntry()
        }
    }.toByteArray()

    private suspend fun installer(filename: String, payload: ByteArray, downloadUrl: String = "$base/module"): Pair<MyBibleInstaller, Book> {
        bodies["/manifest"] = """{"url":"$base","file_name":"registry.json","description":"test","modules":[{"file_name":"$filename","description":"DL15 dictionary","download_url":"$downloadUrl"}]}""".toByteArray()
        bodies["/module"] = payload
        val http = object : DownloadHttp by get<DownloadHttp>(DownloadHttp::class.java) {
            override suspend fun headers(url: String) = HttpHeaders(200, -1, 0)
        }
        val downloader = GenericFileDownloader(settings = settings, notifier = get(UserNotifier::class.java), strings = get(CoreStrings::class.java), http = http, copyFile = net.bible.service.download.testCopyFile)
        val environment = object : AppEnvironment by get<AppEnvironment>(AppEnvironment::class.java) {
            override val cacheDir = temp.path
        }
        val installer = MyBibleInstaller("$base/manifest", downloader, environment)
        installer.reloadBookList()
        return installer to installer.getBooks().single()
    }

    private fun assertInstalledDictionary() {
        val book = Books.installed().getBook("MyBible-${id}_dictionary")
        assertNotNull("Downloaded MyBible dictionary must be registered", book)
        assertEquals("first definition", book!!.getRawText(DefaultLeafKeyList("alpha")))
    }

    @Test fun rawSqliteDownloadRegistersReadableDictionary() = runBlocking {
        val (installer, book) = installer("$id.dictionary.SQLite3", sqlite())
        installer.install(book)
        assertInstalledDictionary()
    }

    @Test fun zipDownloadStillRegistersReadableDictionary() = runBlocking {
        val (installer, book) = installer("$id.dictionary.zip", zip(".SQLite3", sqlite()))
        installer.install(book)
        assertInstalledDictionary()
    }

    private suspend fun assertRejected(payload: ByteArray) {
        val (installer, book) = installer("$id.dictionary.SQLite3", payload)
        try {
            installer.install(book)
            fail("Unsupported payload must fail installation")
        } catch (expected: InstallException) {
            assertFalse("Invalid bytes are not user cancellation", expected is org.crosswire.jsword.book.install.DownloadCancelledException)
        }
        assertNull(Books.installed().getBook("MyBible-${id}_dictionary"))
        assertFalse(root.walkTopDown().any { it.isFile && it.name.contains(id) })
    }

    @Test fun malformedDownloadFailsInsteadOfReportingSuccess() = runBlocking {
        assertRejected("not SQLite or ZIP".toByteArray())
    }

    @Test fun unsupportedSqliteFailsInsteadOfRegisteringBook() = runBlocking {
        assertRejected(sqlite(supported = false))
    }

    private suspend fun assertMalformedTableRejected(table: String) {
        sqlite(supported = false)
        val file = File(temp, "fixture.SQLite3")
        openSqlite(file.path).use { it.exec("CREATE TABLE $table (bogus TEXT)") }
        assertRejected(file.readBytes())
    }

    @Test fun dictionaryMissingRequiredColumnsIsRejected() = runBlocking {
        assertMalformedTableRejected("dictionary")
    }

    @Test fun bibleMissingRequiredColumnsIsRejected() = runBlocking {
        assertMalformedTableRejected("verses")
    }

    @Test fun commentaryMissingRequiredColumnsIsRejected() = runBlocking {
        assertMalformedTableRejected("commentaries")
    }

    @Test fun zipWithoutSupportedModuleFails() = runBlocking {
        assertRejected(zip("readme.txt", "not a module".toByteArray()))
    }

    @Test fun cancelledDownloadInstallsNothing() = runBlocking {
        val (installer, book) = installer("$id.dictionary.SQLite3", sqlite())
        downloadCancelled = true
        try {
            installer.install(book)
            fail("Cancelled download must not succeed")
        } catch (expected: org.crosswire.jsword.book.install.DownloadCancelledException) {
            assertNull(Books.installed().getBook("MyBible-${id}_dictionary"))
            assertFalse(root.walkTopDown().any { it.isFile && it.name.contains(id) })
        }
    }

    @Test fun callerCancellationRemainsCancellationAndInstallsNothing() = runBlocking {
        val (installer, book) = installer("$id.dictionary.SQLite3", sqlite())
        val cancellation = CancellationException("test cancellation")
        cancelled = cancellation
        try {
            installer.install(book)
            fail("Cancellation must propagate")
        } catch (actual: CancellationException) {
            assertSame(cancellation, actual)
        }
        assertNull(Books.installed().getBook("MyBible-${id}_dictionary"))
        assertFalse(root.walkTopDown().any { it.isFile && it.name.contains(id) })
    }

    @Test fun malformedManifestUrlFailsAsBefore() = runBlocking {
        val downloader = GenericFileDownloader(settings = settings, notifier = get(UserNotifier::class.java), strings = get(CoreStrings::class.java), http = get(DownloadHttp::class.java), copyFile = net.bible.service.download.testCopyFile)
        val installer = MyBibleInstaller("not a url", downloader, get(AppEnvironment::class.java))
        // java.net.URL(...) threw MalformedURLException here; the common check throws IllegalArgumentException
        val reload = try { installer.reloadBookList(); null } catch (e: Exception) { e }
        assertTrue("reload: $reload", reload is IllegalArgumentException)
        val modified = try { installer.indexLastUpdated(); null } catch (e: Exception) { e }
        assertTrue("indexLastUpdated: $modified", modified is IllegalArgumentException)
        assertTrue(installer.getBooks().isEmpty())
    }

    /** The KUri call sites (toRemoteURI, getSize, downloadSearchIndex, install) with a module URL that is not a valid URI. */
    @Test fun malformedModuleUrlFailsAtTheKUriCallSites() = runBlocking {
        val badUrl = "https://exa mple.org/a b"
        val (installer, book) = installer("$id.dictionary.SQLite3", sqlite(), downloadUrl = badUrl)
        assertTrue(try { installer.toRemoteURI(book); false } catch (e: java.net.URISyntaxException) { true })
        assertTrue(try { installer.getSize(book); false } catch (e: java.net.URISyntaxException) { true })
        installer.downloadSearchIndex(book, org.crosswire.common.util.KUri("file:///tmp/none"))
        assertTrue(try { installer.install(book); false } catch (e: java.net.URISyntaxException) { true })
        assertNull(Books.installed().getBook("MyBible-${id}_dictionary"))
        assertFalse(root.walkTopDown().any { it.isFile && it.name.contains(id) })
    }

    @Test fun downloadShorterThanSqliteHeaderFailsAsBefore() = runBlocking {
        assertRejected("short".toByteArray())
    }
}

package net.bible.service.download

import net.bible.service.db.reset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import org.crosswire.common.util.HttpTransport
import org.crosswire.common.util.WebResource
import java.net.URI
import org.junit.Assert.fail
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.service.db.DatabaseContainer
import net.bible.sharedcore.platform.AppSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent.get
import org.crosswire.common.util.CWProject
import org.crosswire.jsword.book.install.sword.AbstractSwordInstaller
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.GZIPOutputStream
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * `Installer.getBooks()` never downloads a catalog; the manager must distinguish a missing cache from
 * a successfully parsed catalog that happens to contain no books.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class DownloadManagerCatalogTest {
    @Before fun before() = DatabaseContainer.reset()
    @After fun after() = DatabaseContainer.reset()

    /** The 14 supported book types of the fixture catalog (`.local/k5b-inventory/catalog.tsv`; the 15th conf has no registered type). */
    private val expectedInitials = setOf(
        "K4ZText4Verse", "K4Devotion", "K4Latin1", "K4ZComChapter", "K4ZTextChapterLzss", "K4ZTextBook", "K4ZLD",
        "K4Cipher", "K4RawText", "K4GenBook", "K4RawLD", "K4RawCom", "K4Combining", "K4RawLD4",
    )

    @Test fun missingCatalogCacheIsDownloadedByTheManager() = runBlocking {
        val fixture = RepoServerFixture.create()
        try {
            val books = withContext(Dispatchers.IO) { fixture.manager.getDownloadableBooks(null, fixture.repoName, refresh = false) }
            assertEquals(expectedInitials, books.map { it.initials }.toSet())
            assertEquals(14, books.size)
            assertTrue(fixture.server.requests.any { it.path == "/cat/mods.d.tar.gz" })
            assertTrue(fixture.manager.failedRepos.isEmpty())
        } finally {
            fixture.close()
        }
    }

    @Test fun missingCacheIsReloadedEvenWhenTheStoredTimestampIsCurrent() = runBlocking {
        val fixture = RepoServerFixture.create()
        try {
            // the repository was loaded before (stored timestamp equals the server's), then its cache vanished
            get<AppSettings>(AppSettings::class.java).setLong("repo-${fixture.repoName}-updated", 1_600_000_000_000L)
            val books = withContext(Dispatchers.IO) { fixture.manager.getDownloadableBooks(null, fixture.repoName, refresh = false) }
            assertEquals(14, books.size)
        } finally {
            get<AppSettings>(AppSettings::class.java).removeLong("repo-${fixture.repoName}-updated")
            fixture.close()
        }
    }

    private fun emptyCatalog(): ByteArray = ByteArrayOutputStream().also { out ->
        // Two zero-filled tar end-of-archive blocks, inside a real gzip stream.
        GZIPOutputStream(out).use { it.write(ByteArray(1024)) }
    }.toByteArray()

    private fun catalogGets(fixture: RepoServerFixture) = fixture.server.requests.count {
        it.method == "GET" && it.path == "/cat/mods.d.tar.gz"
    }

    private fun cacheDirectory(fixture: RepoServerFixture): File {
        val installer = fixture.manager.getInstallerFor(fixture.repository) as AbstractSwordInstaller
        return File(CWProject.instance().getWritableProjectSubdir(
            "download-${installer.getHost()}${installer.getCatalogDirectory()!!.replace('/', '_')}", true,
        )!!.getPath()!!)
    }

    @Test fun validEmptyCatalogIsReusedAcrossListingsAndInstallManagerReconstruction() = runBlocking {
        val fixture = RepoServerFixture.create(catalogBytes = emptyCatalog())
        val settings = get<AppSettings>(AppSettings::class.java)
        try {
            repeat(2) {
                assertTrue(withContext(Dispatchers.IO) {
                    fixture.manager.getDownloadableBooks(null, fixture.repoName, refresh = false)
                }.isEmpty())
                assertTrue(fixture.manager.failedRepos.isEmpty())
                assertEquals("A valid empty catalog must not be downloaded again", 1, catalogGets(fixture))
            }
            fixture.manager.refreshInstallManager()
            assertTrue(withContext(Dispatchers.IO) {
                fixture.manager.getDownloadableBooks(null, fixture.repoName, refresh = false)
            }.isEmpty())
            assertTrue(fixture.manager.failedRepos.isEmpty())
            assertEquals(1, catalogGets(fixture))

            // An explicit refresh still checks the timestamp without refetching unchanged content.
            val heads = fixture.server.requests.count { it.method == "HEAD" }
            withContext(Dispatchers.IO) { fixture.manager.getDownloadableBooks(null, fixture.repoName, refresh = true) }
            assertEquals(heads + 1, fixture.server.requests.count { it.method == "HEAD" })
            assertEquals(1, catalogGets(fixture))

            settings.setLong("repo-${fixture.repoName}-updated", 0)
            withContext(Dispatchers.IO) { fixture.manager.getDownloadableBooks(null, fixture.repoName, refresh = true) }
            assertEquals(2, catalogGets(fixture))
            assertEquals(1_600_000_000_000L, settings.getLong("repo-${fixture.repoName}-updated", 0))

            assertTrue(cacheDirectory(fixture).deleteRecursively())
            withContext(Dispatchers.IO) { fixture.manager.getDownloadableBooks(null, fixture.repoName, refresh = false) }
            assertEquals("Deleting a valid empty cache must force a download", 3, catalogGets(fixture))
            assertTrue(fixture.manager.failedRepos.isEmpty())

            File(cacheDirectory(fixture), "mods.d.zip").writeText("corrupt cached ZIP")
            fixture.manager.refreshInstallManager()
            withContext(Dispatchers.IO) { fixture.manager.getDownloadableBooks(null, fixture.repoName, refresh = false) }
            assertEquals("An unreadable cache must be downloaded again", 4, catalogGets(fixture))
            assertTrue(fixture.manager.failedRepos.isEmpty())
        } finally {
            settings.removeLong("repo-${fixture.repoName}-updated")
            cacheDirectory(fixture).deleteRecursively()
            fixture.close()
        }
    }

    @Test fun truncatedPersistentZipIsDownloadedAgainAfterReconstruction() = runBlocking {
        val fixture = RepoServerFixture.create(catalogBytes = emptyCatalog())
        try {
            withContext(Dispatchers.IO) { fixture.manager.getDownloadableBooks(null, fixture.repoName, refresh = false) }
            assertEquals(1, catalogGets(fixture))
            File(cacheDirectory(fixture), "mods.d.zip").writeBytes(byteArrayOf())
            fixture.manager.refreshInstallManager()
            assertTrue(withContext(Dispatchers.IO) {
                fixture.manager.getDownloadableBooks(null, fixture.repoName, refresh = false)
            }.isEmpty())
            assertEquals("A zero-byte ZIP is corrupt, not a valid empty catalog", 2, catalogGets(fixture))
            assertTrue(fixture.manager.failedRepos.isEmpty())
        } finally {
            get<AppSettings>(AppSettings::class.java).removeLong("repo-${fixture.repoName}-updated")
            cacheDirectory(fixture).deleteRecursively()
            fixture.close()
        }
    }

    @Test fun failedHeadMetadataStillAllowsCatalogDownloadOnRefresh() = runBlocking {
        for (status in listOf(405, 500)) {
            val fixture = RepoServerFixture.create(catalogBytes = emptyCatalog(), headStatus = status)
            val settings = get<AppSettings>(AppSettings::class.java)
            try {
                settings.setLong("repo-${fixture.repoName}-updated", 1_600_000_000_000L)
                withContext(Dispatchers.IO) { fixture.manager.getDownloadableBooks(null, fixture.repoName, refresh = false) }
                assertEquals(1, catalogGets(fixture))
                assertEquals(0L, settings.getLong("repo-${fixture.repoName}-updated", -1))
                withContext(Dispatchers.IO) { fixture.manager.getDownloadableBooks(null, fixture.repoName, refresh = true) }
                assertEquals("HEAD $status must not suppress explicit refresh GET", 2, catalogGets(fixture))
                assertTrue(fixture.manager.failedRepos.isEmpty())
            } finally {
                settings.removeLong("repo-${fixture.repoName}-updated")
                cacheDirectory(fixture).deleteRecursively()
                fixture.close()
            }
        }
    }

    @Test fun headTransportErrorFallsBackToDownloadButCancellationPropagates() = runBlocking {
        val fixture = RepoServerFixture.create(catalogBytes = emptyCatalog())
        val settings = get<AppSettings>(AppSettings::class.java)
        val previous = WebResource.transport!!
        var cancelHead = false
        WebResource.transport = object : HttpTransport {
            override suspend fun head(uri: URI, timeoutMs: Int): org.crosswire.common.util.HttpHead {
                if (cancelHead) throw CancellationException("cancel HEAD")
                throw java.io.IOException("HEAD transport error")
            }
            override suspend fun get(uri: URI, timeoutMs: Int) = previous.get(uri, timeoutMs)
        }
        try {
            withContext(Dispatchers.IO) { fixture.manager.getDownloadableBooks(null, fixture.repoName, refresh = false) }
            assertEquals(1, catalogGets(fixture))
            assertEquals(0L, settings.getLong("repo-${fixture.repoName}-updated", -1))
            assertTrue(fixture.manager.failedRepos.isEmpty())

            settings.setLong("repo-${fixture.repoName}-updated", 123L)
            cancelHead = true
            try {
                withContext(Dispatchers.IO) { fixture.manager.getDownloadableBooks(null, fixture.repoName, refresh = true) }
                fail("HEAD cancellation must propagate")
            } catch (expected: CancellationException) {
                assertEquals("cancel HEAD", expected.message)
            }
            assertEquals(1, catalogGets(fixture))
            assertEquals(123L, settings.getLong("repo-${fixture.repoName}-updated", -1))
            assertTrue(fixture.manager.failedRepos.isEmpty())
        } finally {
            WebResource.transport = previous
            settings.removeLong("repo-${fixture.repoName}-updated")
            cacheDirectory(fixture).deleteRecursively()
            fixture.close()
        }
    }

    @Test fun invalidCatalogBodiesAreRetriedRatherThanReusedAsEmptyCatalogs() = runBlocking {
        for (body in listOf(byteArrayOf(), "not a gzip archive".toByteArray())) {
            val fixture = RepoServerFixture.create(catalogBytes = body)
            try {
                withContext(Dispatchers.IO) { fixture.manager.getDownloadableBooks(null, fixture.repoName, refresh = false) }
                val firstGets = catalogGets(fixture)
                assertTrue(firstGets > 0)
                withContext(Dispatchers.IO) { fixture.manager.getDownloadableBooks(null, fixture.repoName, refresh = false) }
                assertTrue("Invalid catalog body must be fetched again", catalogGets(fixture) > firstGets)
            } finally {
                get<AppSettings>(AppSettings::class.java).removeLong("repo-${fixture.repoName}-updated")
                cacheDirectory(fixture).deleteRecursively()
                fixture.close()
            }
        }
    }

    @Test fun repositoryListsTheBooksAndStoresTheRepositoryName() = runBlocking {
        val fixture = RepoServerFixture.create()
        try {
            val books = withContext(Dispatchers.IO) { fixture.repository.getRepoBooks(refresh = false) }
            assertEquals(14, books.size)
            assertTrue(books.all { it.getProperty(DownloadManager.REPOSITORY_KEY) == fixture.repoName })
        } finally {
            fixture.close()
        }
    }
}

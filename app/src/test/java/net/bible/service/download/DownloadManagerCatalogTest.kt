package net.bible.service.download

import kotlinx.coroutines.Dispatchers
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
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * `Installer.getBooks()` no longer downloads a missing catalog (K5b); `DownloadManager` has to produce it through
 * its `getBooks().size == 0 -> reloadBookList()` path.
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

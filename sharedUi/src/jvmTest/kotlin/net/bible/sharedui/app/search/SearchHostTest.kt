package net.bible.sharedui.app.search

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.crosswire.jsword.index.IndexStatus
import net.bible.sharedui.search.nav.IndexOutcome
import net.bible.android.control.search.SearchControl
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedui.reading.host.slots.MapSearchSettings
import net.bible.sharedui.reading.host.slots.FakeBibleSearch
import net.bible.sharedcore.search.BibleSearchService
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.SwordBookMetaData
import net.bible.sharedui.app.HostShell
import net.bible.sharedui.app.ports.ExternalLinks
import net.bible.sharedui.app.ports.NotificationPermission
import net.bible.sharedui.strings.Strings
import net.bible.sharedui.strings.iosStrings
import org.crosswire.jsword.book.Book
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SearchHostTest {
    private val strings: Strings = iosStrings("en")

    private val shell = object : HostShell {
        override val hostScope: CoroutineScope = CoroutineScope(Job())
        override fun exitHost() = Unit
        override fun setWindowTitle(title: String) = Unit
        override fun recreateHost() = Unit
        override fun openReading() = Unit
        override fun setHistoryRoute(owner: Any, route: String) = Unit
        override fun clearHistoryRoute(owner: Any) = Unit
        override fun goBackInHistory(): Boolean = false
        override fun navigate(route: String) = Unit
    }

    private fun host(
        settings: AppSettings? = null,
        bible: BibleSearchService? = null,
    ) = SearchHost(
        shell = shell,
        strings = strings,
        appSettingsProvider = { settings ?: error("settings must stay lazy") },
        notificationPermission = object : NotificationPermission {
            override suspend fun request() = Unit
        },
        links = object : ExternalLinks {
            override val askBeforeOpening = false
            override fun open(url: String) = Unit
        },
        searchControlProvider = { error("searchControl must stay lazy") },
        bibleSearchServiceProvider = { bible ?: error("bibleSearchService must stay lazy") },
        searchIndexServiceProvider = { error("searchIndexService must stay lazy") },
        windowControlProvider = { error("windowControl must stay lazy") },
        pageControlProvider = { error("pageControl must stay lazy") },
    )

    @Test
    fun commentaryFormDoesNotRestoreSavedBibleSelection() {
        fun book(initials: String, driver: String): Book {
            val config = """
                [$initials]
                DataPath=./modules/$initials
                ModDrv=$driver
                Description=$initials regression fixture
                SourceType=OSIS
                Lang=en
                Encoding=UTF-8
            """.trimIndent()
            val metadata = SwordBookMetaData(config.toByteArray(), "$initials.conf")
            return metadata.bookType!!.createBook(metadata)
        }
        val bible = book("Issue3873Bible", "RawText")
        val secondBible = book("Issue3873SecondBible", "RawText")
        val commentary = book("Issue3873Commentary", "RawCom")
        val saved = "Issue3873Bible,Issue3873Gone,Issue3873SecondBible"
        val settings = MapSearchSettings(mutableMapOf(SearchControl.SEARCH_TRANSLATIONS_PREF to saved))
        // Seeding reads the cheap in-memory status; the (here contradicting) full validation must not matter.
        bible.indexStatus = IndexStatus.DONE
        secondBible.indexStatus = IndexStatus.UNDONE
        Books.installed().addBook(bible)
        Books.installed().addBook(secondBible)
        try {
            val host = host(settings = settings, bible = FakeBibleSearch(unindexed = setOf(bible.initials)))
            assertEquals(listOf("Issue3873Commentary"), host.loadSearchTranslations(commentary).selected)
            assertEquals(saved, settings.strings[SearchControl.SEARCH_TRANSLATIONS_PREF])
            assertEquals(
                listOf("Issue3873Bible"),
                host.loadSearchTranslations(bible).selected,
            )
            assertEquals(
                listOf("Issue3873Bible", "Issue3873SecondBible"),
                host.loadSearchTranslations(secondBible).selected,
                "the active Bible remains selected even without an index",
            )
            assertEquals(saved, settings.strings[SearchControl.SEARCH_TRANSLATIONS_PREF])
        } finally {
            Books.installed().removeBook(secondBible)
            Books.installed().removeBook(bible)
        }
    }

    @Test
    fun idleProgressSubscriptionRechecksCompletionWithoutWaitingForANewEvent() {
        var completions = 0
        val stop = host().observeIndexJobs(onJobs = {}, onJobFinished = { completions++ })
        try {
            assertEquals(1, completions)
        } finally {
            stop()
        }
    }

    @Test
    fun progressSubscriptionWhileAJobIsRunningWaitsForTheRealFinishedEvent() {
        val job = org.crosswire.common.progress.JobManager.createJob("CREATE_INDEX-2632", "Indexing", null)
        var completions = 0
        val stop = host().observeIndexJobs(onJobs = {}, onJobFinished = { completions++ })
        try {
            assertEquals(0, completions, "a running job will report its own completion; no synthetic one")
        } finally {
            stop()
            job.done()
        }
    }

    @Test
    fun indexObserverExcludesDownloadsButGenericProgressKeepsThem() {
        val download = org.crosswire.common.progress.JobManager.createJob("Downloading files")
        val index = org.crosswire.common.progress.JobManager.createJob("CREATE_INDEX-3911", "Indexing", null)
        val h = host()
        var indexLabels = emptyList<String>()
        var allLabels = emptyList<String>()
        val stopIndex = h.observeIndexJobs(onJobs = { indexLabels = it.map { row -> row.label } }, onJobFinished = {})
        val stopAll = h.observeProgressStatusJobs { allLabels = it.map { row -> row.label } }
        try {
            // JobManager is a JVM-global registry: jobs left by other tests may be listed too, so assert membership.
            assertTrue("Indexing" in indexLabels, "index observer must list the index job: $indexLabels")
            assertFalse("Downloading files" in indexLabels, "index observer must exclude downloads: $indexLabels")
            assertTrue("Downloading files" in allLabels && "Indexing" in allLabels, "generic progress keeps both: $allLabels")
        } finally {
            stopIndex()
            stopAll()
            download.done()
            index.done()
        }
    }

    @Test
    fun queuedLegacyIndexIsStillRunningRatherThanFailed() = runBlocking {
        val config = """
            [Issue2632Queued]
            DataPath=./modules/Issue2632Queued
            ModDrv=RawText
            Description=Queued regression fixture
            SourceType=OSIS
            Lang=en
            Encoding=UTF-8
        """.trimIndent()
        val metadata = SwordBookMetaData(config.toByteArray(), "Issue2632Queued.conf")
        val book = metadata.bookType!!.createBook(metadata)
        Books.installed().addBook(book)
        try {
            for (status in listOf(IndexStatus.SCHEDULED, IndexStatus.CREATING)) {
                book.indexStatus = status
                assertEquals(IndexOutcome.STILL_RUNNING, host().awaitIndexed(book.initials))
            }
        } finally {
            Books.installed().removeBook(book)
        }
    }

}

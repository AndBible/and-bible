package net.bible.sharedlogic.startup

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import net.bible.android.control.speak.SpeakSettingsStore
import net.bible.android.platform.SuspendHttpTransport
import net.bible.sharedcore.http.DownloadHttp
import net.bible.sharedcore.http.DownloadResponse
import net.bible.sharedcore.http.HttpHeaders
import net.bible.sharedcore.http.HttpTextResult
import net.bible.sharedcore.platform.OrderedLauncher
import org.crosswire.common.util.PropertyMap
import org.crosswire.common.util.Reporter
import org.crosswire.common.util.ReporterEvent
import org.crosswire.common.util.WebResource
import org.crosswire.jsword.book.sword.BookType
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.mp.KoinPlatform
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SharedAppStartupTest {
    private val calls = mutableListOf<String>()
    private val reported = mutableListOf<ReporterEvent>()

    private class FakeHttp : DownloadHttp {
        override suspend fun head(url: String): HttpHeaders = error("unused")
        override suspend fun headers(url: String): HttpHeaders = error("unused")
        override suspend fun <T> open(url: String, block: suspend (DownloadResponse) -> T): T = error("unused")
        override suspend fun text(url: String): HttpTextResult = error("unused")
    }

    private val testModule = module {
        single<DownloadHttp> { FakeHttp() }
        single<SpeakSettingsStore> {
            object : SpeakSettingsStore {
                override fun load(): String? = null
                override fun save(json: String) {}
            }
        }
        single { OrderedLauncher(CoroutineScope(SupervisorJob() + Dispatchers.Default)) }
    }

    /**
     * "watcher" is recorded by the isSwordLoaded probe, which [SharedAppStartup.start] consults immediately before
     * resolving the book-install watcher; it answers false so the (heavy, Koin-resolved) watcher is not built.
     */
    private fun hooks() = StartupHooks(
        initialiseJSwordFolders = { calls += "folders" },
        koinModules = { calls += "koin"; listOf(testModule) },
        reportJSwordError = { reported += it },
        isSwordLoaded = { calls += "watcher"; false },
    )

    @BeforeTest
    fun setUp() {
        // MyDocumentBookManager's initialiser touches Dispatchers.Main
        Dispatchers.setMain(UnconfinedTestDispatcher())
        stopKoin()
        SharedAppStartup.resetForTest()
    }

    @AfterTest
    fun tearDown() {
        SharedAppStartup.resetForTest()
        stopKoin()
        Dispatchers.resetMain()
    }

    @Test
    fun start_runs_hooks_in_order_and_starts_koin_once() {
        SharedAppStartup.start(hooks())
        assertEquals(listOf("folders", "koin", "watcher"), calls)
        val koin = KoinPlatform.getKoin()
        assertTrue(WebResource.transport is SuspendHttpTransport)

        calls.clear()
        SharedAppStartup.start(hooks())
        assertSame(koin, KoinPlatform.getKoin())
        assertEquals(listOf("folders", "watcher"), calls) // Koin modules not requested again
    }

    @Test
    fun error_listener_is_registered_once() {
        SharedAppStartup.start(hooks())
        SharedAppStartup.start(hooks())
        Reporter.informUser(this, "boom")
        assertEquals(1, reported.size)
    }

    @Test
    fun installEnvironment_registers_the_eight_book_types_once() {
        SharedAppStartup.installEnvironment(PropertyMap(), org.crosswire.jsword.internationalisation.LocaleProviderManager.getLocaleProvider())
        SharedAppStartup.installEnvironment(PropertyMap(), org.crosswire.jsword.internationalisation.LocaleProviderManager.getLocaleProvider())
        val types = listOf(
            net.bible.service.sword.mybible.myBibleBible, net.bible.service.sword.mybible.myBibleCommentary,
            net.bible.service.sword.mybible.myBibleDictionary, net.bible.service.sword.mysword.mySwordBible,
            net.bible.service.sword.mysword.mySwordCommentary, net.bible.service.sword.mysword.mySwordDictionary,
            net.bible.service.sword.epub.epubBookType, net.bible.service.sword.esword.eSwordBible,
        )
        types.forEach { assertSame(it, assertNotNull(BookType.fromString(it.toString()))) }
        assertEquals(8, types.map { it.toString() }.toSet().size)
    }
}

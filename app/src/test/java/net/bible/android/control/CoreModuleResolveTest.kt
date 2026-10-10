package net.bible.android.control

import org.koin.android.ext.koin.androidContext
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.speak.SpeakControl
import net.bible.android.view.activity.bookmark.BookmarksServiceImpl
import net.bible.android.view.activity.settings.TextDisplaySettingsServiceImpl
import net.bible.android.view.activity.speak.actionbarbuttons.SpeakActionBarButton
import net.bible.sharedcore.bookmark.BookmarksService
import net.bible.sharedcore.settings.TextDisplaySettingsService
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Actually RESOLVES beans from a real Koin container (as opposed to
 * [CoreModuleVerifyTest], which only runs Koin's static [org.koin.test.verify.verify]).
 *
 * verify() special-cases `Lazy` and so did NOT catch that
 * `singleOf(::SpeakControl)` could not be constructed at runtime (SpeakControl's
 * constructor takes a `kotlin.Lazy<TextToSpeechServiceManager>`, for which nothing
 * is registered — a real `get<SpeakControl>()` threw NoDefinitionFoundException).
 * This test would have caught that regression: it FAILS before the explicit
 * `single { SpeakControl(lazy { ... }, get()) }` binding and PASSES after.
 *
 * The Koin container is the production one, started by [net.bible.android.BibleApplication.onCreate]
 * (via [TestBibleApplication]) with androidContext + coreModule.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class CoreModuleResolveTest {

    @Test
    fun `SpeakControl resolves from a real Koin container`() {
        val koin = GlobalContext.get()
        // Previously threw NoDefinitionFoundException: No definition found for type 'Lazy'
        val speakControl = koin.get<SpeakControl>()
        assertNotNull(speakControl)
        // An action-bar button that depends (transitively) on SpeakControl also resolves.
        val speakButton = koin.get<SpeakActionBarButton>()
        assertNotNull(speakButton)
    }

    @Test
    fun `BookmarksServiceImpl resolves both as its concrete type and as BookmarksService, same instance`() {
        // BookmarksComposeActivity injects the CONCRETE BookmarksServiceImpl (for its id -> entity
        // map, beyond the portable BookmarksService interface used by BookmarksController); this
        // relies on singleOf(::BookmarksServiceImpl) { bind<BookmarksService>() } keeping BOTH the
        // primary (concrete) type and the bound interface resolvable to the SAME singleton.
        val koin = GlobalContext.get()
        val concrete = koin.get<BookmarksServiceImpl>()
        val bound = koin.get<BookmarksService>()
        assertNotNull(concrete)
        assertSame(concrete, bound)
    }

    @Test
    fun `TextDisplaySettingsServiceImpl resolves both as its concrete type and as TextDisplaySettingsService, same instance`() {
        // TextDisplaySettingsComposeActivity injects the CONCRETE TextDisplaySettingsServiceImpl
        // (for the HIDELABELS bridge helper, beyond the portable TextDisplaySettingsService
        // interface); this relies on `single { TextDisplaySettingsServiceImpl() } bind
        // TextDisplaySettingsService::class` keeping BOTH the primary (concrete) type and the bound
        // interface resolvable to the SAME singleton -- an earlier `single<TextDisplaySettingsService>
        // { TextDisplaySettingsServiceImpl() }` registered ONLY the interface as resolvable and broke
        // that injection with NoDefinitionFoundException.
        val koin = GlobalContext.get()
        val concrete = koin.get<TextDisplaySettingsServiceImpl>()
        val bound = koin.get<TextDisplaySettingsService>()
        assertNotNull(concrete)
        assertSame(concrete, bound)
    }

    @Test
    fun `cloud engines resolve reciprocal providers and watcher in isolated fresh modules`() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val first = org.koin.dsl.koinApplication {
            androidContext(context)
            modules(buildCoreModule())
        }
        val second = org.koin.dsl.koinApplication {
            androidContext(context)
            modules(buildCoreModule())
        }
        try {
            val backup = first.koin.get<net.bible.android.control.backup.BackupControl>()
            assertSame(backup, first.koin.get<net.bible.android.control.backup.BackupControl>())
            assertNotNull(first.koin.get<net.bible.android.control.page.window.WindowControl>())
            // The pre-migration backup is synchronous and must not open a database while resolving its graph.
            assertNotNull(backup.zipDatabaseFiles())
            org.junit.Assert.assertNotSame(backup, second.koin.get<net.bible.android.control.backup.BackupControl>())
            val cloud = first.koin.get<net.bible.service.cloudsync.CloudSync>()
            val documents = first.koin.get<net.bible.service.cloudsync.documents.DocumentSync>()
            assertSame(cloud, first.koin.get<net.bible.service.cloudsync.CloudSyncProvider>().get())
            assertSame(documents, first.koin.get<net.bible.service.cloudsync.DocumentSyncProvider>().get())
            assertNotNull(first.koin.get<net.bible.android.control.versification.BookInstallWatcher>())
            assertSame(first.koin.get<net.bible.android.platform.AndroidSyncScheduler>(), first.koin.get<net.bible.service.cloudsync.SyncScheduler>())
            org.junit.Assert.assertNotSame(cloud, second.koin.get<net.bible.service.cloudsync.CloudSync>())
            org.junit.Assert.assertNotSame(documents, second.koin.get<net.bible.service.cloudsync.documents.DocumentSync>())
        } finally {
            first.close()
            second.close()
        }
    }

    @After
    fun tearDown() {
        // Don't leak the container into other test classes; BibleApplication.onCreate
        // restarts it (its start is guarded on GlobalContext.getOrNull() == null).
        if (GlobalContext.getOrNull() != null) {
            stopKoin()
        }
    }
}

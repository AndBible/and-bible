package net.bible.android.control

import kotlinx.coroutines.launch
import kotlinx.coroutines.cancelAndJoin

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

    private class OpeningTrap {
        val calls = java.util.concurrent.atomic.AtomicInteger()
        private val attempts = java.util.concurrent.ConcurrentLinkedQueue<Throwable>()
        fun open(): net.bible.service.db.DatabaseContainer {
            val attempt = IllegalStateException("Cold database opening on ${Thread.currentThread().name}")
            attempts.add(attempt)
            calls.incrementAndGet()
            throw attempt
        }
        fun diagnostic() = attempts.joinToString("\n") { it.stackTraceToString() }
    }

    private fun drainCurrentFixture() {
        val application = androidx.test.core.app.ApplicationProvider.getApplicationContext<TestBibleApplication>()
        // Join this application's captured owners while their database is still attached.
        net.bible.test.DatabaseResetter.runResetBlocking(timeoutMs = 5000) {
            application.drainAiSettingsFixture()
        }
    }

    @Test
    fun `cold preparation joins the exact current fixture producers`() {
        val scope = GlobalContext.get().get<net.bible.sharedcore.platform.AppCoroutineScope>()
        val job = scope.coroutineContext[kotlinx.coroutines.Job]!!
        val finished = kotlinx.coroutines.CompletableDeferred<Unit>()
        val child = scope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            try { kotlinx.coroutines.CompletableDeferred<Unit>().await() }
            finally { finished.complete(Unit) }
        }
        try {
            drainCurrentFixture()
            org.junit.Assert.assertTrue("exact producer must finish before detachment", child.isCompleted)
            org.junit.Assert.assertTrue(finished.isCompleted)
            org.junit.Assert.assertTrue("preparation must not terminate the application scope", job.isActive)
        } finally {
            kotlinx.coroutines.runBlocking { kotlinx.coroutines.withTimeout(5000) { child.cancelAndJoin() } }
        }
    }

    @Test
    fun `unquiesced real AI observer reaches the cold container trap`() {
        drainCurrentFixture()
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<TestBibleApplication>()
        val scope = net.bible.sharedcore.platform.AppCoroutineScope()
        val job = scope.coroutineContext[kotlinx.coroutines.Job]!!
        val collecting = kotlinx.coroutines.CompletableDeferred<Unit>()
        val release = kotlinx.coroutines.CompletableDeferred<Unit>()
        val observationEnded = kotlinx.coroutines.CompletableDeferred<Unit>()
        val store = net.bible.service.llm.AiSettingsStore(
            { net.bible.service.db.DatabaseContainer.instance.aiSettingsDb.globalAiSettingsDao() },
            net.bible.sharedcore.platform.OrderedLauncher(scope), scope,
            net.bible.android.platform.AndroidAppEnvironment(context),
            lifetime = net.bible.service.db.DatabaseContainer.aiLifetime,
            invalidations = { kotlinx.coroutines.flow.flow {
                try { collecting.complete(Unit); release.await(); emit(Unit) }
                finally { observationEnded.complete(Unit) }
            } },
        )
        val factory = net.bible.service.db.DatabaseContainer.containerFactory
        val ready = net.bible.service.db.DatabaseContainer.ready
        val initialized = net.bible.service.common.CommonUtils.initialized
        var detached: net.bible.service.db.DatabaseContainer? = null
        var didDetach = false
        val trap = OpeningTrap()
        try {
            kotlinx.coroutines.runBlocking { kotlinx.coroutines.withTimeout(5000) { store.ensureLoaded(); collecting.await() } }
            detached = net.bible.service.db.DatabaseContainer.detachInstanceForTest()
            didDetach = true
            net.bible.service.db.DatabaseContainer.ready = false
            net.bible.service.common.CommonUtils.initialized = false
            net.bible.service.db.DatabaseContainer.containerFactory = trap::open
            release.complete(Unit)
            kotlinx.coroutines.runBlocking { kotlinx.coroutines.withTimeout(5000) { observationEnded.await(); job.cancelAndJoin() } }
            org.junit.Assert.assertEquals(trap.diagnostic(), 1, trap.calls.get())
            org.junit.Assert.assertTrue(trap.diagnostic(), trap.diagnostic().contains("AiSettingsStore\$refresh"))
            println("CONTROLLED_AI_OBSERVER_ATTEMPT\n${trap.diagnostic()}")
            val app = org.koin.dsl.koinApplication { androidContext(context); modules(buildCoreModule()) }
            try {
                val backup = app.koin.get<net.bible.android.control.backup.BackupControl>()
                assertNotNull(app.koin.get<net.bible.android.control.page.window.WindowControl>())
                assertNotNull(app.koin.get<net.bible.service.cloudsync.CloudSync>())
                assertNotNull(app.koin.get<net.bible.service.cloudsync.documents.DocumentSync>())
                java.util.zip.ZipFile(backup.zipDatabaseFiles()!!).use { archive ->
                    assertNotNull(archive.getEntry("db/settings.sqlite3"))
                }
                org.junit.Assert.assertEquals("Only the controlled observer attempted opening\n${trap.diagnostic()}",
                    1, trap.calls.get())
            } finally { app.close() }
        } finally {
            release.complete(Unit)
            kotlinx.coroutines.runBlocking { kotlinx.coroutines.withTimeout(5000) { job.cancelAndJoin() } }
            net.bible.service.db.DatabaseContainer.containerFactory = factory
            net.bible.service.db.DatabaseContainer.ready = ready
            net.bible.service.common.CommonUtils.initialized = initialized
            if (didDetach) restoreContainer(detached)
        }
    }

    private fun restoreContainer(container: net.bible.service.db.DatabaseContainer?) {
        net.bible.service.db.DatabaseContainer::class.java.getDeclaredField("_instance")
            .apply { isAccessible = true }.set(null, container)
    }

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

    @Test
    fun `cold backup graph and synchronous zip never open the database container`() {
        drainCurrentFixture()
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val factory = net.bible.service.db.DatabaseContainer.containerFactory
        val ready = net.bible.service.db.DatabaseContainer.ready
        val initialized = net.bible.service.common.CommonUtils.initialized
        val detached = net.bible.service.db.DatabaseContainer.detachInstanceForTest()
        var app: org.koin.core.KoinApplication? = null
        val trap = OpeningTrap()
        try {
            net.bible.service.db.DatabaseContainer.ready = false
            net.bible.service.common.CommonUtils.initialized = false
            net.bible.service.db.DatabaseContainer.containerFactory = trap::open
            app = org.koin.dsl.koinApplication { androidContext(context); modules(buildCoreModule()) }
            val backup = app.koin.get<net.bible.android.control.backup.BackupControl>()
            assertNotNull(app.koin.get<net.bible.android.control.page.window.WindowControl>())
            assertNotNull(app.koin.get<net.bible.service.cloudsync.CloudSync>())
            assertNotNull(app.koin.get<net.bible.service.cloudsync.documents.DocumentSync>())
            val zip = backup.zipDatabaseFiles()!!
            java.util.zip.ZipFile(zip).use { archive ->
                org.junit.Assert.assertNotNull(archive.getEntry("db/settings.sqlite3"))
            }
            org.junit.Assert.assertEquals(trap.diagnostic(), 0, trap.calls.get())
        } finally {
            try { app?.close() } finally {
                net.bible.service.db.DatabaseContainer.containerFactory = factory
                net.bible.service.db.DatabaseContainer.ready = ready
                net.bible.service.common.CommonUtils.initialized = initialized
                // Restore the exact instance; its fixture observer was joined before detachment.
                restoreContainer(detached)
            }
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

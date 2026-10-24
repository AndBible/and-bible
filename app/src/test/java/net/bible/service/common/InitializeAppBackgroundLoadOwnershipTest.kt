package net.bible.service.common

import net.bible.service.db.reset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.test.StandardTestDispatcher
import net.bible.android.BibleApplication
import net.bible.android.platform.AndroidLlmStrings
import net.bible.android.view.activity.ai.PromptServiceImpl
import net.bible.service.llm.AiSettings
import net.bible.service.llm.AiSettingsStore
import net.bible.service.llm.BuiltInPrompts
import net.bible.service.llm.GlobalAiSettings
import net.bible.service.llm.GlobalAiSettingsDao
import net.bible.sharedcore.ai.PromptService
import net.bible.sharedcore.platform.AppCoroutineScope
import net.bible.sharedcore.platform.AppEnvironment
import net.bible.test.KoinFactorySnapshot
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.koin.dsl.module
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.database.progress.GlobalReadingProgressSettings
import net.bible.android.database.progress.GlobalReadingProgressSettingsDao
import net.bible.service.db.DatabaseContainer
import net.bible.sharedcore.platform.OrderedLauncher
import net.bible.test.DatabaseResetter
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * `initializeApp()` starts the snapshot loads without blocking. A Room 3 close under an in-flight read is a
 * native crash (SIGSEGV in `sqlite3VdbeHalt`, full gate l1a8-final-16b43079e), so every database close path must
 * wait for that read first. The DAO call here stands in for the native step: it cannot be cancelled, only waited for.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class InitializeAppBackgroundLoadOwnershipTest {
    @After fun tearDown() { DatabaseResetter.resetDatabase() }

    @Test fun resetWaitsForTheInFlightBackgroundLoadBeforeClosingTheDatabases() {
        val koin = GlobalContext.get()
        val previous = koin.get<ReadingProgressSettingsStore>()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val gatedDao = object : GlobalReadingProgressSettingsDao {
            override suspend fun get(): GlobalReadingProgressSettings? {
                entered.complete(Unit)
                withContext(NonCancellable) { release.await() }
                return null
            }
            override suspend fun set(settings: GlobalReadingProgressSettings) {}
        }
        val gatedStore = ReadingProgressSettingsStore(koin.get<OrderedLauncher>(), { gatedDao })
        koin.loadModules(listOf(org.koin.dsl.module { single { gatedStore } }))
        val resetDone = CountDownLatch(1)
        try {
            val load = CommonUtils.loadSnapshotStoresInBackground()
            runBlocking { withTimeout(10_000) { entered.await() } }

            val resetter = Thread { DatabaseContainer.reset(); resetDone.countDown() }.apply { start() }
            assertFalse(
                "reset() closed the databases while the background load was still reading",
                resetDone.await(1, TimeUnit.SECONDS),
            )

            release.complete(Unit)
            resetter.join(10_000)
            assertTrue("reset() must finish once the read has ended", resetDone.count == 0L)
            assertTrue(load.isCompleted)
        } finally {
            release.complete(Unit)
            resetDone.await(10, TimeUnit.SECONDS)
            koin.loadModules(listOf(org.koin.dsl.module { single { previous } }))
        }
    }

    private class GatedProgressStore(koin: org.koin.core.Koin, val row: GlobalReadingProgressSettings) {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        private val dao = object : GlobalReadingProgressSettingsDao {
            override suspend fun get(): GlobalReadingProgressSettings? {
                entered.complete(Unit)
                release.await()
                return row
            }
            override suspend fun set(settings: GlobalReadingProgressSettings) {}
        }
        val store = ReadingProgressSettingsStore(koin.get<OrderedLauncher>(), { dao })
    }

    @Test fun coldStartBarrierWaitsForTheBackgroundLoadSoAStoreReadSeesTheRealRow() {
        val koin = GlobalContext.get()
        val previous = koin.get<ReadingProgressSettingsStore>()
        val real = GlobalReadingProgressSettings(autoTrackReading = true, activeCycle = 7)
        val gated = GatedProgressStore(koin, real)
        koin.loadModules(listOf(org.koin.dsl.module { single { gated.store } }))
        val barrierDone = CountDownLatch(1)
        try {
            CommonUtils.loadSnapshotStoresInBackground()
            runBlocking { withTimeout(10_000) { gated.entered.await() } }

            val reader = Thread { CommonUtils.awaitSnapshotLoadsAtEdge(); barrierDone.countDown() }.apply { start() }
            assertFalse("the barrier returned while the load was still reading", barrierDone.await(1, TimeUnit.SECONDS))

            gated.release.complete(Unit)
            assertTrue("the barrier must end with the load", barrierDone.await(10, TimeUnit.SECONDS))
            reader.join(10_000)
            assertTrue(gated.store.current.autoTrackReading)
            assertTrue(gated.store.current.activeCycle == 7)
        } finally {
            gated.release.complete(Unit)
            koin.loadModules(listOf(org.koin.dsl.module { single { previous } }))
        }
    }

    @Test fun backgroundStartupAwaitsAiSettingsBeforePublishingThePromptSnapshot() = runBlocking {
        val koin = GlobalContext.get()
        val snapshots = listOf(
            KoinFactorySnapshot.of<AiSettings>(),
            KoinFactorySnapshot.of<PromptService>(),
            KoinFactorySnapshot.of<AppCoroutineScope>(),
        )
        val constructorDispatcher = StandardTestDispatcher()
        val constructorJob = SupervisorJob()
        val constructorScope = AppCoroutineScope(constructorJob + constructorDispatcher)
        val startupScope = AppCoroutineScope()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val dao = DatabaseContainer.instance.aiSettingsDb.globalAiSettingsDao()
        dao.set(GlobalAiSettings(hiddenBuiltInPrompts = setOf(BuiltInPrompts.SUMMARY_ID)))
        val gatedDao = object : GlobalAiSettingsDao {
            override suspend fun get(): GlobalAiSettings? {
                entered.complete(Unit)
                release.await()
                return dao.get()
            }
            override suspend fun set(settings: GlobalAiSettings) = dao.set(settings)
        }
        val environment = koin.get<AppEnvironment>()
        val store = AiSettingsStore(
            { gatedDao }, OrderedLauncher(constructorScope), constructorScope,
            object : AppEnvironment by environment { override val isDebugMode = true },
            lifetime = DatabaseContainer.aiLifetime, invalidations = { emptyFlow() },
        )
        var load: Job? = null
        try {
            // Hold the service's sibling constructor job: only the actual startup path can load settings.
            koin.loadModules(listOf(module {
                single<AiSettings> { store }
                single<AppCoroutineScope> { constructorScope }
            }))
            val service = PromptServiceImpl(AndroidLlmStrings(BibleApplication.application))
            koin.loadModules(listOf(module {
                single<PromptService> { service }
                single<AppCoroutineScope> { startupScope }
            }))
            val backgroundLoad = CommonUtils.loadSnapshotStoresInBackground()
            load = backgroundLoad
            val prerequisiteStarted = withTimeout(10_000) {
                select<Boolean> {
                    entered.onAwait { true }
                    backgroundLoad.onJoin { entered.isCompleted }
                }
            }
            // Joining alone would miss the refresh exception swallowed by runEachCatching.
            assertTrue("background startup skipped the AI settings prerequisite", prerequisiteStarted)
            assertFalse("startup must await the gated settings read", backgroundLoad.isCompleted)
            assertFalse("prompt snapshot must not publish before settings are loaded", service.ready.value)

            release.complete(Unit)
            withTimeout(10_000) { backgroundLoad.join() }
            assertEquals(setOf(BuiltInPrompts.SUMMARY_ID), store.hiddenBuiltInPrompts)
            assertTrue("startup must publish the prompt snapshot after settings load", service.ready.value)
            assertTrue("the snapshot must reflect persisted hidden prompts", service.hasHiddenPrompts.value)
        } finally {
            release.complete(Unit)
            try {
                withContext(NonCancellable) {
                    withTimeout(10_000) {
                        load?.cancelAndJoin()
                        startupScope.coroutineContext[Job]!!.cancelAndJoin()
                        constructorJob.cancel()
                        constructorDispatcher.scheduler.runCurrent()
                        constructorJob.join()
                    }
                }
            } finally {
                snapshots.asReversed().forEach { it.restore() }
            }
        }
    }

    @Test fun coldStartBarrierDoesNotHangOnACancelledLoad() {
        val koin = GlobalContext.get()
        val previous = koin.get<ReadingProgressSettingsStore>()
        val gated = GatedProgressStore(koin, GlobalReadingProgressSettings())
        koin.loadModules(listOf(org.koin.dsl.module { single { gated.store } }))
        try {
            val load = CommonUtils.loadSnapshotStoresInBackground()
            runBlocking { withTimeout(10_000) { gated.entered.await() } }
            load.cancel()
            val done = CountDownLatch(1)
            Thread { CommonUtils.awaitSnapshotLoadsAtEdge(); done.countDown() }.start()
            assertTrue("a cancelled load must not hang the barrier", done.await(10, TimeUnit.SECONDS))
        } finally {
            gated.release.complete(Unit)
            koin.loadModules(listOf(org.koin.dsl.module { single { previous } }))
        }
    }

    @Test fun initializeAppCoroutineSurvivesAFailingStoreLoadAndStillRunsTheLaterSteps() {
        val koin = GlobalContext.get()
        val previousGlobal = koin.get<GlobalTextDisplaySettingsStore>()
        val previousProgress = koin.get<ReadingProgressSettingsStore>()
        var progressReads = 0
        val failingGlobal = GlobalTextDisplaySettingsStore(koin.get<OrderedLauncher>(), {
            object : net.bible.android.database.GlobalTextDisplaySettingsDao {
                override suspend fun get(): net.bible.android.database.GlobalTextDisplaySettings? = throw IllegalStateException("unreadable row")
                override suspend fun set(settings: net.bible.android.database.GlobalTextDisplaySettings) {}
            }
        })
        val countingProgress = ReadingProgressSettingsStore(koin.get<OrderedLauncher>(), {
            object : GlobalReadingProgressSettingsDao {
                override suspend fun get(): GlobalReadingProgressSettings? { progressReads++; return null }
                override suspend fun set(settings: GlobalReadingProgressSettings) {}
            }
        })
        koin.loadModules(listOf(org.koin.dsl.module { single { failingGlobal }; single { countingProgress } }))
        try {
            runBlocking { withTimeout(60_000) { CommonUtils.initializeAppCoroutine() } }
            assertTrue("the step after the failing load must still run", progressReads > 0)
        } finally {
            koin.loadModules(listOf(org.koin.dsl.module { single { previousGlobal }; single { previousProgress } }))
        }
    }
}

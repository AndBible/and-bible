package net.bible.service.common

import kotlinx.coroutines.CompletableDeferred
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

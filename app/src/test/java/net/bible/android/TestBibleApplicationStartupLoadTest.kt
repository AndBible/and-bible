package net.bible.android

import kotlinx.coroutines.Job
import net.bible.android.view.activity.ai.PromptServiceImpl
import net.bible.service.common.CommonUtils
import net.bible.sharedcore.ai.PromptService
import net.bible.sharedcore.platform.AppCoroutineScope
import net.bible.test.KoinFactorySnapshot
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The app's startup work must be finished when a test body starts.
 *
 * `CommonUtils.initializeApp()` loads the snapshot stores in the background, and that load is what first builds the
 * `PromptService` single, whose constructor launches three never-ending event collectors on the Koin
 * `AppCoroutineScope`. Tests swap that binding for a scope they own and join (`WorkspaceOverrideOrderingTest`); when
 * the load reached the constructor only after the swap (a loaded machine), the collectors became children of the
 * test's scope and its join waited for them until the 30 s `runTest` timeout.
 *
 * [GatedStartupApplication] holds the load at that constructor until this test body has swapped the scope, which
 * makes the race certain: the collectors must still land on the fixture's own scope, because the fixture waits for the
 * load before the test starts (the gate opens on its own after [GATE_TIMEOUT_SECONDS] for that wait).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplicationStartupLoadTest.GatedStartupApplication::class, sdk = [TEST_SDK])
class TestBibleApplicationStartupLoadTest {
    class GatedStartupApplication : TestBibleApplication() {
        override fun beforeAppStartup() {
            val gate = CountDownLatch(1).also { promptServiceGate = it }
            GlobalContext.get().loadModules(listOf(module {
                single<PromptService> {
                    gate.await(GATE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    PromptServiceImpl(get())
                }
            }))
        }
    }

    @Test fun startupWorkDoesNotResolveBindingsTheTestBodySwaps() {
        val koin = GlobalContext.get()
        val scopeFactory = KoinFactorySnapshot.of<AppCoroutineScope>(koin)
        val testScope = AppCoroutineScope()
        try {
            koin.loadModules(listOf(module { single { testScope } }))
            promptServiceGate!!.countDown()
            CommonUtils.awaitSnapshotLoadsAtEdge()
            koin.get<PromptService>()
            assertEquals(emptyList<Job>(), testScope.coroutineContext[Job]!!.children.toList())
        } finally {
            testScope.coroutineContext[Job]!!.cancel()
            scopeFactory.restore()
        }
    }

    private companion object {
        const val GATE_TIMEOUT_SECONDS = 5L
        @Volatile var promptServiceGate: CountDownLatch? = null
    }
}

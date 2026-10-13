package net.bible.service.sword.mydocument

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.sharedcore.platform.AppCoroutineScope
import net.bible.sharedcore.platform.OrderedLauncher
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Bounded diagnostic of the actual startup registration tail; does not change fixture ownership. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class MyDocumentFixtureOwnerProbeTest {
    @Test fun failedPostSuperDrainReleasesOwnPresenterButRetainsRealRegistration() {
        val app = ApplicationProvider.getApplicationContext<TestBibleApplication>()
        app.onTerminate()
        val graph = GlobalContext.get()
        val oldLauncher = graph.get<OrderedLauncher>()
        val priorScope = graph.get<AppCoroutineScope>()
        val originalDispatcher = MyDocumentBookManager.mainDispatcher
        val scheduler = TestCoroutineScheduler()
        val held = StandardTestDispatcher(scheduler)
        val reached = CompletableDeferred<Unit>()
        val startupFailure = IllegalStateException("post-super startup pump failed")
        val retryFailure = IllegalStateException("post-super termination pump failed")
        val container = net.bible.service.db.DatabaseContainer.instance
        val containerField = net.bible.service.db.DatabaseContainer.Companion::class.java.enclosingClass
            .getDeclaredField("_instance").apply { isAccessible = true }
        val subscription = net.bible.android.BibleApplication::class.java.getDeclaredField("userMessagesSubscription")
            .apply { isAccessible = true }
        val released = TestBibleApplication::class.java.getDeclaredField("presenterReleased")
            .apply { isAccessible = true }
        var tail: Job? = null
        var installedPresenter: Any? = null
        val retainedLauncher = OrderedLauncher(priorScope)
        MyDocumentBookManager.mainDispatcher = object : CoroutineDispatcher() {
            override fun dispatch(context: kotlin.coroutines.CoroutineContext, block: Runnable) {
                reached.complete(Unit)
                held.dispatch(context, block)
            }
        }
        org.koin.core.context.loadKoinModules(org.koin.dsl.module {
            single<OrderedLauncher> {
                // This factory is first resolved by real BibleApplication.onCreate after subscribing.
                installedPresenter = subscription.get(app)
                tail = retainedLauncher.launch("mydocument-sync") { MyDocumentBookManager.registerAllDocuments() }
                runBlocking { withTimeout(5_000) { reached.await() } }
                android.os.Handler(android.os.Looper.getMainLooper()).post { throw startupFailure }
                retainedLauncher
            }
        })
        try {
            assertSame(startupFailure, runCatching { app.onCreate() }.exceptionOrNull())
            assertNotNull("actual super startup installed presenter before held drain", installedPresenter)
            assertSame(installedPresenter, subscription.get(app))
            assertSame(retainedLauncher, MyDocumentBookManager.launcher)
            assertFalse(tail!!.isCompleted)
            android.os.Handler(android.os.Looper.getMainLooper()).post { throw retryFailure }
            assertSame(retryFailure, runCatching { app.onTerminate() }.exceptionOrNull())
            assertFalse(tail!!.isCompleted)
            assertSame(retainedLauncher, MyDocumentBookManager.launcher)
            assertSame(graph, GlobalContext.get())
            assertSame(container, containerField.get(null))
            assertTrue(runCatching { app.onCreate() }.exceptionOrNull() is IllegalStateException)
            assertNull("incomplete registration must not retain independent presenter", subscription.get(app))
            assertTrue("presenter safe cleanup recorded once", released.getBoolean(app))
            scheduler.advanceUntilIdle()
            runBlocking { withTimeout(5_000) { tail!!.join() } }
            MyDocumentBookManager.mainDispatcher = originalDispatcher
            app.onTerminate()
            assertTrue(tail!!.isCompleted)
            assertNull(subscription.get(app))
            assertTrue(released.getBoolean(app))
            assertNull(containerField.get(null))
        } finally {
            scheduler.advanceUntilIdle()
            runBlocking { withTimeout(5_000) { tail?.join() } }
            MyDocumentBookManager.mainDispatcher = originalDispatcher
            app.onTerminate()
            org.koin.core.context.loadKoinModules(org.koin.dsl.module { single<OrderedLauncher> { oldLauncher } })
            if (MyDocumentBookManager.launcher === retainedLauncher) MyDocumentBookManager.launcher = oldLauncher
        }
    }

    @Test fun predecessorResetAndGraphStopDoNotAbandonRealRegistration() {
        val app = ApplicationProvider.getApplicationContext<TestBibleApplication>()
        val graph = GlobalContext.get()
        val fixtureScope = graph.get<AppCoroutineScope>()
        val launcher = MyDocumentBookManager.launcher
        val scope = OrderedLauncher::class.java.getDeclaredField("scope").apply { isAccessible = true }
            .get(launcher) as CoroutineScope
        val parent = scope.coroutineContext[Job]!!
        val originalDispatcher = MyDocumentBookManager.mainDispatcher
        val scheduler = TestCoroutineScheduler()
        val held = StandardTestDispatcher(scheduler)
        val reached = CompletableDeferred<Unit>()
        MyDocumentBookManager.mainDispatcher = object : CoroutineDispatcher() {
            override fun dispatch(context: kotlin.coroutines.CoroutineContext, block: Runnable) {
                reached.complete(Unit)
                held.dispatch(context, block)
            }
        }
        var tail: Job? = null
        try {
            net.bible.service.common.CommonUtils.destroy()
            net.bible.service.common.CommonUtils.initializeApp()
            runBlocking { withTimeout(5_000) { reached.await() } }
            @Suppress("UNCHECKED_CAST")
            val tails = OrderedLauncher::class.java.getDeclaredField("tails").apply { isAccessible = true }
                .get(launcher) as Map<Any, Job>
            tail = checkNotNull(tails["mydocument-sync"])
            val oldContainer = net.bible.service.db.DatabaseContainer.instance
            android.os.Handler(android.os.Looper.getMainLooper()).post { scheduler.advanceUntilIdle() }
            net.bible.test.DatabaseResetter.resetDatabase()
            val resetContainer = net.bible.service.db.DatabaseContainer.instance
            val incompleteAfterReset = !tail.isCompleted
            org.koin.core.context.stopKoin()
            val incompleteAfterStop = !tail.isCompleted
            println("predecessor launcher=${System.identityHashCode(launcher)} tail=${System.identityHashCode(tail)} scope=${System.identityHashCode(scope)} fixtureScope=${System.identityHashCode(fixtureScope)} parentOwns=${parent.children.any { it === tail }} incompleteAfterReset=$incompleteAfterReset incompleteAfterStop=$incompleteAfterStop oldDb=${System.identityHashCode(oldContainer)} resetDb=${System.identityHashCode(resetContainer)} dispatcher=$originalDispatcher heldMainReached=${reached.isCompleted}")
            // Release the actual dispatch before fresh startup; never abandon prior outstanding work.
            runBlocking {
                withTimeout(5_000) {
                    while (!tail.isCompleted) { scheduler.advanceUntilIdle(); delay(10) }
                }
            }
            MyDocumentBookManager.mainDispatcher = originalDispatcher
            app.onTerminate()
            app.onCreate()
            assertNotSame(graph, GlobalContext.get())
            assertTrue("fresh startup completed after exact old registration release", tail.isCompleted)
            assertFalse("reset must complete real registration before database replacement", incompleteAfterReset)
            assertFalse("graph stop must not abandon the old registration", incompleteAfterStop)
        } finally {
            runBlocking {
                withTimeout(5_000) {
                    while (tail?.isCompleted == false) { scheduler.advanceUntilIdle(); delay(10) }
                }
            }
            MyDocumentBookManager.mainDispatcher = originalDispatcher
        }
    }

    @Test fun failedStartupRetainsExactPriorTailAndBlocksRestartUntilTerminationRetry() {
        val app = ApplicationProvider.getApplicationContext<TestBibleApplication>()
        app.onTerminate()
        val graph = GlobalContext.get()
        val prior = MyDocumentBookManager.launcher
        val container = net.bible.service.db.DatabaseContainer.instance
        val containerField = net.bible.service.db.DatabaseContainer.Companion::class.java.enclosingClass
            .getDeclaredField("_instance").apply { isAccessible = true }
        val previousDispatcher = MyDocumentBookManager.mainDispatcher
        val scheduler = TestCoroutineScheduler()
        val held = StandardTestDispatcher(scheduler)
        val reached = CompletableDeferred<Unit>()
        MyDocumentBookManager.mainDispatcher = object : CoroutineDispatcher() {
            override fun dispatch(context: kotlin.coroutines.CoroutineContext, block: Runnable) {
                reached.complete(Unit)
                held.dispatch(context, block)
            }
        }
        val tail = prior.launch("mydocument-sync") { MyDocumentBookManager.registerAllDocuments() }
        val sentinel = IllegalStateException("startup Main pump failed")
        try {
            runBlocking { withTimeout(5_000) { reached.await() } }
            android.os.Handler(android.os.Looper.getMainLooper()).post { throw sentinel }
            assertSame(sentinel, runCatching { app.onCreate() }.exceptionOrNull())
            assertFalse(tail.isCompleted)
            assertSame(prior, MyDocumentBookManager.launcher)
            assertSame(graph, GlobalContext.get())
            assertSame(container, containerField.get(null))
            assertTrue(runCatching { app.onCreate() }.exceptionOrNull() is IllegalStateException)
            android.os.Handler(android.os.Looper.getMainLooper()).post { throw sentinel }
            assertSame(sentinel, runCatching { app.onTerminate() }.exceptionOrNull())
            assertSame(container, containerField.get(null))
            assertSame(prior, MyDocumentBookManager.launcher)
            scheduler.advanceUntilIdle()
            runBlocking { withTimeout(5_000) { tail.join() } }
            MyDocumentBookManager.mainDispatcher = previousDispatcher
            app.onTerminate()
            assertTrue(tail.isCompleted)
            app.onCreate()
            assertSame(GlobalContext.get().get<OrderedLauncher>(), MyDocumentBookManager.launcher)
        } finally {
            scheduler.advanceUntilIdle()
            runBlocking { withTimeout(5_000) { tail.join() } }
            MyDocumentBookManager.mainDispatcher = previousDispatcher
            app.onTerminate()
        }
    }

    @Test fun realRegistrationTailIsTrackedByCurrentFixtureOwner() {
        val app = ApplicationProvider.getApplicationContext<TestBibleApplication>()
        val graph = GlobalContext.get()
        val fixtureScope = graph.get<AppCoroutineScope>()
        val graphLauncher = graph.get<OrderedLauncher>()
        val managerLauncher = MyDocumentBookManager.launcher
        val scopeField = OrderedLauncher::class.java.getDeclaredField("scope").apply { isAccessible = true }
        val managerScope = scopeField.get(managerLauncher) as CoroutineScope
        val managerJob = managerScope.coroutineContext[Job]!!
        val fixtureJob = fixtureScope.coroutineContext[Job]!!
        val previousDispatcher = MyDocumentBookManager.mainDispatcher
        val scheduler = TestCoroutineScheduler()
        val heldDispatcher = StandardTestDispatcher(scheduler)
        val reachedMain = CompletableDeferred<Unit>()
        val dispatcher = object : CoroutineDispatcher() {
            override fun dispatch(context: kotlin.coroutines.CoroutineContext, block: Runnable) {
                reachedMain.complete(Unit)
                heldDispatcher.dispatch(context, block)
            }
        }
        MyDocumentBookManager.mainDispatcher = dispatcher
        val tail = managerLauncher.launch("mydocument-sync") { MyDocumentBookManager.registerAllDocuments() }
        try {
            runBlocking { withTimeout(5_000) { reachedMain.await() } }
            println("managerLauncher=${System.identityHashCode(managerLauncher)} graphLauncher=${System.identityHashCode(graphLauncher)} managerScope=${System.identityHashCode(managerScope)} fixtureScope=${System.identityHashCode(fixtureScope)} tail=${System.identityHashCode(tail)} managerParentOwns=${managerJob.children.any { it === tail }} fixtureParentOwns=${fixtureJob.children.any { it === tail }} originalDispatcher=$previousDispatcher heldDispatcher=$dispatcher tailActive=${tail.isActive} tailCompleted=${tail.isCompleted}")
            assertFalse("real DAO-read then Main registration boundary held", tail.isCompleted)
            assertTrue(managerJob.children.any { it === tail })
            assertSame("registration launcher must be owned by current fixture", graphLauncher, managerLauncher)
            assertTrue("fixture completion must include exact registration tail", fixtureJob.children.any { it === tail })
        } finally {
            runBlocking {
                withTimeout(5_000) {
                    while (!tail.isCompleted) {
                        scheduler.advanceUntilIdle()
                        delay(10)
                    }
                }
            }
            MyDocumentBookManager.mainDispatcher = previousDispatcher
            assertTrue(tail.isCompleted)
            assertSame(app, ApplicationProvider.getApplicationContext<TestBibleApplication>())
        }
    }
}

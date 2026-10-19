@file:Suppress("DEPRECATION_ERROR")

package net.bible.test

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.ChildHandle
import kotlinx.coroutines.ChildJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Pins the race behind the `NullPointerException ... coroutineName` seen in forked suite runs:
 * the reset snapshots `appScope`'s children while a producer is still being constructed on another
 * thread. `AbstractCoroutine` attaches to its parent before assigning its `context`, so in that
 * window the child's `toString()` throws. Forced here by blocking the parent's `attachChild`
 * after it has registered the child.
 */
class DatabaseResetterJobStateTest {
    private companion object {
        val DEBUG_MODE: Boolean = run {
            // kotlinx enables debug mode when assertions are on (`kotlinx.coroutines.DEBUG_PROPERTY_VALUE_AUTO`).
            var enabled = false
            assert(true.also { enabled = true })
            enabled
        }
    }

    @OptIn(InternalCoroutinesApi::class)
    @Test
    fun describingAParentWhoseChildIsStillBeingConstructedDoesNotThrow() {
        // The NPE in toString() only occurs in kotlinx coroutines debug mode (enabled by -ea); without it the test would pass vacuously.
        assumeTrue("needs kotlinx coroutines debug mode (-ea)", DEBUG_MODE)
        val real: Job = SupervisorJob()
        val registered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val parent = object : Job by real {
            // `by real` would hand out `real` itself as the context's Job element and bypass this class.
            @Suppress("UNCHECKED_CAST")
            override fun <E : kotlin.coroutines.CoroutineContext.Element> get(key: kotlin.coroutines.CoroutineContext.Key<E>): E? =
                if (key === Job) this as E else null
            override fun <R> fold(initial: R, operation: (R, kotlin.coroutines.CoroutineContext.Element) -> R): R = operation(initial, this)
            override fun minusKey(key: kotlin.coroutines.CoroutineContext.Key<*>): kotlin.coroutines.CoroutineContext =
                if (key === Job) kotlin.coroutines.EmptyCoroutineContext else this
            override fun attachChild(child: ChildJob): ChildHandle {
                val handle = real.attachChild(child)
                registered.countDown()
                release.await(10, TimeUnit.SECONDS)
                return handle
            }
        }
        val scope = CoroutineScope(Dispatchers.Default + parent)
        var starter: Thread? = null
        try {
            // Construction happens on the caller thread, so the producer is launched from another one.
            starter = Thread { scope.launch { } }.apply { isDaemon = true; start() }
            assertTrue("child must have registered with its parent", registered.await(10, TimeUnit.SECONDS))
            val half = real.children.toList()
            assertTrue("the half-constructed child is visible in children", half.size == 1)
            // Both views the reset takes: the parent (children listed) and the child itself.
            DatabaseResetter.jobState(real)
            DatabaseResetter.jobState(half.single())
        } finally {
            release.countDown()
            real.cancel()
            starter?.join(10_000)
        }
    }
}

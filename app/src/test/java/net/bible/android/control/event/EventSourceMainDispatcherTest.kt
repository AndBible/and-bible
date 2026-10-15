package net.bible.android.control.event

import android.app.Application
import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import net.bible.android.TEST_SDK
import net.bible.sharedcore.event.EventSource
import net.bible.sharedcore.event.Subscriptions
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import kotlin.test.assertEquals
import kotlin.test.assertSame

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [TEST_SDK])
@LooperMode(LooperMode.Mode.PAUSED)
@OptIn(ExperimentalCoroutinesApi::class)
class EventSourceMainDispatcherTest {
    @Test
    fun mainDeliveryFollowsDispatcherReplacementWithoutLosingImmediateOrBackgroundSemantics() {
        val source = EventSource<Int>()
        val mainSeen = mutableListOf<Int>()
        val syncSeen = mutableListOf<Int>()
        val deliveryThreads = mutableListOf<Thread>()
        val subscriptions = Subscriptions()
        subscriptions.add(source.subscribe { syncSeen += it })
        subscriptions.add(source.subscribeOnMain {
            deliveryThreads += Thread.currentThread()
            mainSeen += it
        })
        try {
            // First use is the real Android Main.immediate, not an installed test dispatcher.
            source.emit(1)
            assertEquals(listOf(1), mainSeen, "Main.immediate must run before emit returns")

            val first = StandardTestDispatcher()
            Dispatchers.setMain(first)
            Thread { source.emit(2) }.apply { start(); join() }
            assertEquals(listOf(1, 2), syncSeen)
            assertEquals(listOf(1), mainSeen, "subscribeOnMain must not become synchronous subscribe")
            first.scheduler.runCurrent()
            assertEquals(listOf(1, 2), mainSeen, "delivery must use the currently installed Main")

            val second = StandardTestDispatcher()
            Dispatchers.setMain(second)
            source.emit(3)
            second.scheduler.runCurrent()
            assertEquals(listOf(1, 2, 3), mainSeen, "replacement Main owns subsequent delivery")

            Dispatchers.resetMain()
            source.emit(4)
            assertEquals(listOf(1, 2, 3, 4), mainSeen, "reset restores synchronous Main.immediate")

            Thread { source.emit(5) }.apply { start(); join() }
            assertEquals(listOf(1, 2, 3, 4, 5), syncSeen)
            assertEquals(listOf(1, 2, 3, 4), mainSeen, "background emission must dispatch to Main")
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(listOf(1, 2, 3, 4, 5), mainSeen)
            deliveryThreads.forEach { assertSame(Looper.getMainLooper().thread, it) }

            subscriptions.cancelAll()
            source.emit(6)
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(listOf(1, 2, 3, 4, 5), syncSeen)
            assertEquals(listOf(1, 2, 3, 4, 5), mainSeen)
        } finally {
            subscriptions.cancelAll()
            Dispatchers.resetMain()
            shadowOf(Looper.getMainLooper()).idle()
        }
    }
}

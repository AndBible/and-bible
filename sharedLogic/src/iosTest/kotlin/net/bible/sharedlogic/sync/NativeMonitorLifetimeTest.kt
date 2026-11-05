package net.bible.sharedlogic.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class NativeMonitorLifetimeTest {
    @Test fun thousandsOfTemporaryTargetsLeaveNoRegistryEntries() {
        val baseline = activeNativeMonitorCount()
        repeat(10_000) { withMonitor(Any()) { } }
        assertEquals(baseline, activeNativeMonitorCount())
    }

    @Test fun contendedEnterExitCannotDestroyAMonitorStillInUse() = runTest {
        val target = Any()
        val baseline = activeNativeMonitorCount()
        var counter = 0
        List(4) {
            async(Dispatchers.Default) {
                repeat(5_000) {
                    withMonitor(target) {
                        withMonitor(target) { counter++ }
                    }
                }
            }
        }.awaitAll()
        assertEquals(20_000, counter)
        assertEquals(baseline, activeNativeMonitorCount())
    }

    @Test fun conditionWaitKeepsItsMonitorAlive() = runTest {
        val target = Any()
        val baseline = activeNativeMonitorCount()
        var ready = false
        var signalled = false
        val waiter = async(Dispatchers.Default) {
            withMonitor(target) {
                ready = true
                while (!signalled) monitorWait(target, 1000)
            }
        }
        val notifier = async(Dispatchers.Default) {
            while (!withMonitor(target) { ready }) { }
            withMonitor(target) {
                assertEquals(baseline + 1, activeNativeMonitorCount())
                signalled = true
                monitorNotifyAll(target)
            }
        }
        awaitAll(waiter, notifier)
        assertEquals(baseline, activeNativeMonitorCount())
    }
}

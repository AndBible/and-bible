package net.bible.android.control.download

import kotlinx.atomicfu.atomic
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import net.bible.sharedcore.settings.DeviceLocalPrefs
import net.bible.sharedlogic.sync.withMonitor
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * #3973 follow-up seen on the iOS simulator: `withMonitor` is ONE process-wide mutex on Kotlin/Native, and an
 * NSUserDefaults write runs foreign observers synchronously (NSUserDefaultsDidChangeNotification). Holding the monitor
 * across the prefs write let the main thread (blocked on `withMonitor` in `DownloadQueue.isInQueue`) and the writer
 * wait on each other: the onboarding download screen froze. The store must not hold the shared monitor while it
 * writes; here the write waits for another thread's `withMonitor` and must not time out.
 */
class PendingRepoInstallsLockTest {
    @OptIn(DelicateCoroutinesApi::class)
    private class OtherMonitorUserDuringWrite : DeviceLocalPrefs {
        val otherThreadGotTheMonitor = atomic(false)
        private val map = mutableMapOf<String, String>()
        override fun getString(key: String, default: String?) = map[key] ?: default
        override fun getBoolean(key: String, default: Boolean) = default
        override fun setBoolean(key: String, value: Boolean) = Unit
        override fun remove(key: String) { map.remove(key) }
        override fun setString(key: String, value: String) {
            val got = atomic(false)
            GlobalScope.launch(Dispatchers.Default) { withMonitor(Any()) { got.value = true } }
            val start = TimeSource.Monotonic.markNow()
            while (!got.value && start.elapsedNow() < 5.seconds) { /* spin: no blocking sleep in common code */ }
            otherThreadGotTheMonitor.value = got.value
            map[key] = value
        }
    }

    @Test
    fun prefsWriteDoesNotHoldTheSharedMonitor() = runTest {
        val prefs = OtherMonitorUserDuringWrite()
        withContext(Dispatchers.Default) { PendingRepoInstalls(prefs).add(PendingRepoInstall("CrossWire", "KJV")) }
        assertTrue(prefs.otherThreadGotTheMonitor.value, "another thread's withMonitor waited for the prefs write")
    }
}

package net.bible.service.cloudsync.ios

import kotlinx.coroutines.CancellationException
import net.bible.service.cloudsync.BackgroundSyncPolicy
import net.bible.sharedcore.log.Log
import net.bible.sharedcore.platform.sync.BackgroundRefresh
import org.koin.core.Koin
import org.koin.mp.KoinPlatform

private const val TAG = "IosBackgroundSync"

/** One BGTask run: sync if due, and always ask for the next wake-up. */
class IosBackgroundSync(
    private val policy: BackgroundSyncPolicy,
    private val refresh: BackgroundRefresh,
) {
    /** Runs [BackgroundSyncPolicy.runOnce]; the next refresh is scheduled in `finally`, so a failing sync still reschedules. */
    suspend fun runAndReschedule(): Boolean = try {
        policy.runOnce()
    } finally {
        safeSchedule()
    }

    /** Requests the first (or next) background refresh, e.g. after startup. Failures are logged. */
    fun schedule() = safeSchedule()

    /** A failing reschedule must not mask the outcome (or exception) of the run; cancellation still propagates. */
    private fun safeSchedule() {
        try {
            refresh.schedule(policy.nextEarliestSeconds())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "rescheduling background refresh failed", e)
        }
    }
}

/**
 * Handler for the registered BGTask. When Koin is not started (the OS launched the app only for the
 * task) or no [IosBackgroundSync] is bound, it returns true without touching any database; the
 * next wake-up is then requested by the normal startup path.
 * I2-pending (phase 2): decide Koin/database readiness on a background-only launch and then reschedule
 * from this path, so such a launch cannot leave no refresh scheduled. A failure inside the run is logged and
 * reported as false (the run itself has already rescheduled); cancellation is rethrown.
 */
suspend fun iosBackgroundRefreshEntry(koin: Koin? = KoinPlatform.getKoinOrNull()): Boolean {
    val sync = koin?.getOrNull<IosBackgroundSync>()
    if (sync == null) {
        Log.i(TAG, "background refresh: app not started")
        return true
    }
    return try {
        sync.runAndReschedule()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(TAG, "background refresh failed", e)
        false
    }
}

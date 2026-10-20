package net.bible.service.cloudsync.ios

import kotlinx.coroutines.CancellationException
import net.bible.service.cloudsync.BackgroundSyncPolicy
import net.bible.sharedcore.log.Log
import net.bible.sharedcore.platform.sync.BackgroundRefresh
import org.koin.core.Koin
import org.koin.mp.KoinPlatform

private const val TAG = "IosBackgroundSync"
private const val DEFAULT_BACKGROUND_RETRY_SECONDS = 900L

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

    /** Entry-level orchestration owns rescheduling when readiness can fail before this instance exists. */
    internal suspend fun run(): Boolean = policy.runOnce()

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
 * BGTask entry. The host supplies cold-start/database readiness (no dependency on sharedUi here).
 * Protected-data unavailability skips every startup/keychain/database operation; this entry alone
 * owns the next refresh request, including startup failure and cancellation.
 */
suspend fun iosBackgroundRefreshEntry(
    koin: Koin? = KoinPlatform.getKoinOrNull(),
    protectedDataAvailable: suspend () -> Boolean = { platform.UIKit.UIApplication.sharedApplication.protectedDataAvailable },
    start: suspend () -> Unit = {},
    openDatabases: suspend () -> Unit = {},
    refresh: BackgroundRefresh,
): Boolean {
    fun currentKoin() = koin ?: KoinPlatform.getKoinOrNull()
    return runIosBackgroundRefresh(
        protectedDataAvailable = protectedDataAvailable,
        started = { false }, // Always join host readiness; Koin can exist before initialization completes.
        start = start,
        openDatabases = openDatabases,
        sync = { currentKoin()?.getOrNull<IosBackgroundSync>()?.run() ?: true },
        reschedule = {
            // The host fallback works even when startup failed halfway through publishing Koin.
            refresh.schedule(DEFAULT_BACKGROUND_RETRY_SECONDS)
        },
    )
}

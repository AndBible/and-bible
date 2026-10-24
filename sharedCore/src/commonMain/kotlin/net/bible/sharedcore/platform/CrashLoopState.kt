package net.bible.sharedcore.platform

/** Device-local crash keys shared by the pre-database tracker and the reporting environment. */
object CrashPreferences {
    const val RUNNING = "app-running"
    const val COUNT = "app-crashed-count"
    const val TIME = "app-crashed-time"
}

/** Storage is device-local: implementing this must never open the application databases. */
interface CrashPreferencesStore {
    var running: Boolean
    var count: Int
    var time: Long
}

/** Android's one-hour crash window, independent of the platform's crash hook. */
class CrashLoopState(private val store: CrashPreferencesStore, private val clock: () -> Long) {
    fun onLaunch() {
        if (store.running) {
            if (store.count == 0) store.time = clock()
            store.count++
        }
        store.running = true
    }

    fun onCleanExit() { store.running = false }

    /** Non-mutating admission check before the UI's launch accounting. */
    fun wouldOfferRecoveryAfterLaunch(launchRecorded: Boolean): Boolean {
        val dirtyLaunch = !launchRecorded && store.running
        val count = store.count + if (dirtyLaunch) 1 else 0
        val time = if (dirtyLaunch && store.count == 0) clock() else store.time
        return needsRecovery(count, time)
    }

    private fun needsRecovery(count: Int, time: Long): Boolean =
        count > 1 && time != 0L && clock() - time < ONE_HOUR

    fun shouldOfferRecovery(): Boolean {
        val age = clock() - store.time
        val offer = needsRecovery(store.count, store.time)
        if (store.time != 0L && (age > ONE_HOUR || offer)) {
            store.count = 0
            store.time = 0L
        }
        // Recovery is a safe holding screen; killing it must not perpetuate the crash loop.
        if (offer) onCleanExit()
        return offer
    }

    private companion object { const val ONE_HOUR = 3_600_000L }
}

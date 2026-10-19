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

    fun shouldOfferRecovery(): Boolean {
        val age = clock() - store.time
        val offer = store.count > 1 && store.time != 0L && age < ONE_HOUR
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

package net.bible.android.control.download

import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import net.bible.sharedcore.platform.AppJson
import net.bible.sharedcore.settings.DeviceLocalPrefs
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock

/** A repository install the user requested: [initials] from the repository named [repository]. */
@Serializable
data class PendingRepoInstall(val repository: String, val initials: String)

/**
 * The repository installs that were requested but have not reached an outcome the user has seen (#3973).
 *
 * [DownloadQueue] adds a request before its transfer starts and removes it once the install finished, failed with
 * an error shown, or was cancelled by the user. A request whose transfer was stopped with the app (iOS suspends or
 * terminates a backgrounded app; any platform can kill the process) therefore stays here, and the next launch reports
 * it as not installed instead of the module silently missing. Device-local, like the modules it describes: it is
 * never synced or restored onto another device.
 */
class PendingRepoInstalls(private val prefs: DeviceLocalPrefs) {
    /**
     * This store's own lock, never `withMonitor`: on Kotlin/Native that is ONE process-wide mutex, and the prefs write
     * below runs foreign code (NSUserDefaults posts its change notification synchronously). Holding the shared
     * monitor there deadlocked the main thread waiting in `DownloadQueue.isInQueue` (#3973, iOS simulator).
     */
    private val lock = reentrantLock()

    /** Written durably (see [DeviceLocalPrefs.setStringDurably]): a process killed right after this must still find it. */
    fun add(request: PendingRepoInstall) = update(durable = true) { if (request in it) it else it + request }

    fun forget(requests: Collection<PendingRepoInstall>) = update { it - requests.toSet() }

    /**
     * Forgets the [requested] installs that a successfully loaded [catalogue] no longer offers (module removed, or its
     * repository renamed): "Download again" could never succeed, so the notice must not come back at every launch.
     * An empty [catalogue] means it did not load (offline, error): nothing is forgotten, so the user can retry later
     * or press Cancel. Returns the forgotten requests.
     */
    fun forgetNotInCatalogue(requested: Collection<PendingRepoInstall>, catalogue: Collection<PendingRepoInstall>): List<PendingRepoInstall> {
        if (catalogue.isEmpty()) return emptyList()
        val offered = catalogue.toSet()
        val gone = requested.filter { it !in offered }
        if (gone.isNotEmpty()) forget(gone)
        return gone
    }

    /**
     * The requests whose module is still not installed according to [isInstalled] (called with the initials).
     * Requests whose module did get installed (killed after the module was registered, or installed again since) are
     * forgotten for good.
     */
    fun interrupted(isInstalled: (String) -> Boolean): List<PendingRepoInstall> = lock.withLock {
        val all = read()
        val (installed, missing) = all.partition { isInstalled(it.initials) }
        if (installed.isNotEmpty()) write(missing)
        missing
    }

    private fun update(durable: Boolean = false, change: (List<PendingRepoInstall>) -> List<PendingRepoInstall>) = lock.withLock {
        val before = read()
        val after = change(before)
        if (after != before) write(after, durable)
    }

    private fun read(): List<PendingRepoInstall> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return try {
            AppJson.decodeFromString(SERIALIZER, raw)
        } catch (e: SerializationException) {
            emptyList()
        } catch (e: IllegalArgumentException) {
            emptyList()
        }
    }

    private fun write(requests: List<PendingRepoInstall>, durable: Boolean = false) {
        if (requests.isEmpty()) {
            prefs.remove(KEY)
        } else {
            val json = AppJson.encodeToString(SERIALIZER, requests)
            if (durable) prefs.setStringDurably(KEY, json) else prefs.setString(KEY, json)
        }
    }

    companion object {
        const val KEY = "pending_repo_installs"
        private val SERIALIZER = ListSerializer(PendingRepoInstall.serializer())
    }
}

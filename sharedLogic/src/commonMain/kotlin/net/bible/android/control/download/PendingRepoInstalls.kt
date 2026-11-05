package net.bible.android.control.download

import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import net.bible.sharedcore.platform.AppJson
import net.bible.sharedcore.settings.DeviceLocalPrefs
import net.bible.sharedlogic.sync.withMonitor

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
    private val lock = Any()

    fun add(request: PendingRepoInstall) = update { if (request in it) it else it + request }

    fun forget(requests: Collection<PendingRepoInstall>) = update { it - requests.toSet() }

    /**
     * The requests whose module is still not installed according to [isInstalled] (called with the initials).
     * Requests whose module did get installed (killed after the module was registered, or installed again since) are
     * forgotten for good.
     */
    fun interrupted(isInstalled: (String) -> Boolean): List<PendingRepoInstall> = withMonitor(lock) {
        val all = read()
        val (installed, missing) = all.partition { isInstalled(it.initials) }
        if (installed.isNotEmpty()) write(missing)
        missing
    }

    private fun update(change: (List<PendingRepoInstall>) -> List<PendingRepoInstall>) = withMonitor(lock) {
        val before = read()
        val after = change(before)
        if (after != before) write(after)
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

    private fun write(requests: List<PendingRepoInstall>) {
        if (requests.isEmpty()) prefs.remove(KEY) else prefs.setString(KEY, AppJson.encodeToString(SERIALIZER, requests))
    }

    companion object {
        const val KEY = "pending_repo_installs"
        private val SERIALIZER = ListSerializer(PendingRepoInstall.serializer())
    }
}

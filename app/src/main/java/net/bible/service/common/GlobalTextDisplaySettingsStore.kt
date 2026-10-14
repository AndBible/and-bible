package net.bible.service.common

import net.bible.android.database.GlobalTextDisplaySettings
import net.bible.android.database.GlobalTextDisplaySettingsDao
import net.bible.android.database.WorkspaceEntities.TextDisplaySettings
import net.bible.sharedcore.log.Log
import net.bible.sharedcore.platform.OrderedLauncher

/**
 * Snapshot of the global text display settings row (L1a snapshot pattern). [load] reads the row once at app
 * start; [current] is then a plain field read, and [set] updates the snapshot synchronously and writes the row
 * through [launcher] in submission order (a must-complete write, so it does not belong to any screen scope).
 *
 * Before [load] [current] is the factory default, which is also what an absent row means; such reads are logged once.
 */
class GlobalTextDisplaySettingsStore(
    private val launcher: OrderedLauncher,
    private val dao: () -> GlobalTextDisplaySettingsDao,
) {
    @Volatile private var snapshot: TextDisplaySettings? = null
    @Volatile private var loaded = false
    @Volatile private var warned = false

    suspend fun load() {
        val row = dao().get()?.textDisplaySettings
        // A set() that raced the read is newer than the row just read.
        if (snapshot == null) snapshot = row
        loaded = true
    }

    /** A copy: callers always received a fresh object when this was a database read, so none can alias the snapshot. */
    val current: TextDisplaySettings
        get() {
            if (!loaded && !warned) {
                warned = true
                Log.w(TAG, "Global text display settings read before load(); using the default")
            }
            return (snapshot ?: TextDisplaySettings()).copy()
        }

    fun set(value: TextDisplaySettings) {
        val copy = value.copy()
        snapshot = copy
        launcher.launch(KEY) { dao().set(GlobalTextDisplaySettings(textDisplaySettings = copy)) }
    }

    companion object {
        const val KEY = "global-tds"
        private const val TAG = "GlobalTdsStore"
    }
}

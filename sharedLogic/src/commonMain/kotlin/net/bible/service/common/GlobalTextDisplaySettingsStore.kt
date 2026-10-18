package net.bible.service.common

import net.bible.android.database.GlobalTextDisplaySettings
import net.bible.android.database.GlobalTextDisplaySettingsDao
import net.bible.android.database.WorkspaceEntities.TextDisplaySettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import net.bible.sharedcore.event.EventSource
import net.bible.sharedcore.event.Events
import net.bible.sharedcore.log.Log
import net.bible.sharedcore.platform.OrderedLauncher

/**
 * Snapshot of the global text display settings row (L1a snapshot pattern). [load] reads the row once at app
 * start; [current] is then a plain field read, and [set] updates the snapshot synchronously and writes the row
 * through [launcher] in submission order (a must-complete write, so it does not belong to any screen scope).
 *
 * The row can change below the store: cloud sync writes the table directly and an in-app restore swaps the
 * database. So the snapshot reloads on [replacements] (restore), [syncReplacements] (sync refresh) and on table
 * [invalidations]. Reloads run on the same ordered key as the writes, so they see every earlier write, and a
 * reload never overwrites the snapshot while a local write is still pending. A write queued before a restore is
 * dropped: it must not land in the restored database.
 *
 * Before [load] [current] is the factory default, which is also what an absent row means; such reads are logged once.
 */
class GlobalTextDisplaySettingsStore(
    private val launcher: OrderedLauncher,
    private val dao: () -> GlobalTextDisplaySettingsDao,
    private val replacements: Events<Unit> = EventSource(),
    private val syncReplacements: Events<Unit> = EventSource(),
    private val appScope: kotlinx.coroutines.CoroutineScope? = null,
    private val invalidations: (() -> kotlinx.coroutines.flow.Flow<Unit>)? = null,
) {
    private val lock = SynchronizedObject()
    @kotlin.concurrent.Volatile private var snapshot: TextDisplaySettings? = null
    @kotlin.concurrent.Volatile private var loaded = false
    @kotlin.concurrent.Volatile private var warned = false
    private var pendingWrites = 0
    private val preLoad = mutableListOf<(TextDisplaySettings) -> TextDisplaySettings>()
    private var replacementEpoch = 0L
    private var observing = false
    private val generation = kotlinx.coroutines.flow.MutableStateFlow(0L)

    /**
     * Reads the row once. Writes made before this ran ([set], [update]) are held as transforms and applied here to the
     * real row, so a one-field setter on a not-yet-loaded store cannot persist defaults over the other fields.
     * Idempotent: later calls only make sure observation is running.
     */
    suspend fun load() {
        val row = dao().get()?.textDisplaySettings
        synchronized(lock) { // L1-common-ok: atomicfu lock
            if (!loaded) {
                loaded = true
                if (preLoad.isNotEmpty()) {
                    val applied = preLoad.fold(row ?: TextDisplaySettings()) { acc, t -> t(acc) }
                    preLoad.clear()
                    commit(applied)
                } else snapshot = row
            }
        }
        startObserving()
    }

    private fun startObserving() {
        synchronized(lock) { if (observing) return; observing = true } // L1-common-ok: atomicfu lock
        val restoreSubscription = replacements.subscribe {
            synchronized(lock) { replacementEpoch++ } // L1-common-ok: atomicfu lock
            generation.value++
            reload()
        }
        val syncSubscription = syncReplacements.subscribe {
            generation.value++
            reload()
        }
        val scope = appScope
        val flow = invalidations
        if (scope == null || flow == null) return
        val job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            // The flow belongs to the database that was current when it was created, so each replacement
            // re-creates it (as AiSettingsStore does); collectLatest drops the one on the closed database.
            generation.collectLatest {
                try {
                    flow().collect { reload() }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // A closed pre-restore database can fail here; the next replacement signal re-arms.
                    Log.e(TAG, "Observation ended; awaiting database replacement", e)
                }
            }
        }
        job.invokeOnCompletion { restoreSubscription.cancel(); syncSubscription.cancel() }
    }

    private fun reload() {
        launcher.launch(KEY) {
            val row = dao().get()?.textDisplaySettings ?: TextDisplaySettings()
            synchronized(lock) { if (pendingWrites == 0) snapshot = row } // L1-common-ok: atomicfu lock
        }
    }

    /** A copy of the snapshot, so callers cannot replace it; nested objects are shared (callers do not mutate them). */
    val current: TextDisplaySettings
        get() {
            if (!loaded && !warned) {
                warned = true
                Log.w(TAG, "Global text display settings read before load(); using the default")
            }
            return (snapshot ?: TextDisplaySettings()).copy()
        }

    fun set(value: TextDisplaySettings) { val copy = value.copy(); update { copy.copy() } }

    /**
     * Applies [transform] to the snapshot and persists the result in submission order. Before [load] the transform is
     * queued and applied to the stored row by [load]; the snapshot meanwhile shows it applied to the defaults.
     */
    fun update(transform: TextDisplaySettings.() -> TextDisplaySettings) {
        synchronized(lock) { // L1-common-ok: atomicfu lock
            if (!loaded) preLoad.add(transform)
            commit((snapshot ?: TextDisplaySettings()).copy().transform(), persist = loaded)
        }
    }

    /** Publishes [copy] as the snapshot and, when [persist], queues its write. Caller holds [lock]. */
    private fun commit(copy: TextDisplaySettings, persist: Boolean = true) {
        snapshot = copy
        if (!persist) return
        pendingWrites++
        val epoch = replacementEpoch
        launcher.launch(KEY) {
            try {
                if (synchronized(lock) { epoch == replacementEpoch }) dao().set(GlobalTextDisplaySettings(textDisplaySettings = copy)) // L1-common-ok: atomicfu lock
            } finally {
                synchronized(lock) { pendingWrites-- } // L1-common-ok: atomicfu lock
            }
        }
    }

    companion object {
        const val KEY = "global-tds"
        private const val TAG = "GlobalTdsStore"
    }
}

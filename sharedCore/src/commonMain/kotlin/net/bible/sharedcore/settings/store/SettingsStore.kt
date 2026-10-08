package net.bible.sharedcore.settings.store

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

/**
 * Typed app settings held in memory: reads are synchronous, writes update memory at once and are
 * written through [SettingsBackend] by one serial writer, in call order. Replaces synchronous DAO
 * reads so that DAOs can be `suspend` (D1 spec R4). Owned by one database-container instance: when
 * the files are replaced (restore/import) a new container builds a new store, so nothing is stale.
 */
class SettingsStore(private val backend: SettingsBackend, scope: CoroutineScope) {
    private val lock = SynchronizedObject()
    private val booleans = HashMap<String, Boolean>()
    private val longs = HashMap<String, Long>()
    private val strings = HashMap<String, String>()
    private val doubles = HashMap<String, Double>()

    private sealed interface Job {
        class Write(val w: SettingWrite) : Job
        class Flush(val done: CompletableDeferred<Unit>) : Job
    }

    private val queue = Channel<Job>(Channel.UNLIMITED)
    private val _changes = MutableSharedFlow<String>(extraBufferCapacity = 64)

    /** Emits the key of every set call (value or null-delete), after memory is updated. */
    val changes: SharedFlow<String> = _changes

    init {
        // A single consumer drains the queue, so backend writes never run concurrently and keep call order.
        scope.launch {
            for (job in queue) when (job) {
                is Job.Write -> backend.write(job.w)
                is Job.Flush -> job.done.complete(Unit)
            }
        }
    }

    /** Replaces the in-memory state with the backend's contents. */
    suspend fun load() {
        val s = backend.loadAll()
        synchronized(lock) {
            booleans.clear(); booleans.putAll(s.booleans)
            longs.clear(); longs.putAll(s.longs)
            strings.clear(); strings.putAll(s.strings)
            doubles.clear(); doubles.putAll(s.doubles)
        }
    }

    fun getBoolean(key: String, default: Boolean): Boolean = synchronized(lock) { booleans[key] } ?: default
    fun getLong(key: String, default: Long): Long = synchronized(lock) { longs[key] } ?: default
    fun getString(key: String, default: String?): String? = synchronized(lock) { strings[key] } ?: default
    fun getDouble(key: String, default: Double): Double = synchronized(lock) { doubles[key] } ?: default

    fun setBoolean(key: String, value: Boolean?) =
        put(SettingWrite.Bool(key, value)) { if (value == null) booleans.remove(key) else booleans[key] = value }

    fun setLong(key: String, value: Long?) =
        put(SettingWrite.Lng(key, value)) { if (value == null) longs.remove(key) else longs[key] = value }

    fun setString(key: String, value: String?) =
        put(SettingWrite.Str(key, value)) { if (value == null) strings.remove(key) else strings[key] = value }

    fun setDouble(key: String, value: Double?) =
        put(SettingWrite.Dbl(key, value)) { if (value == null) doubles.remove(key) else doubles[key] = value }

    /** Memory update and enqueue under one lock, so memory order == write order (Review Focus 5). */
    private inline fun put(write: SettingWrite, update: () -> Unit) {
        synchronized(lock) { update(); queue.trySend(Job.Write(write)) }
        _changes.tryEmit(write.key)
    }

    /** Suspends until every write enqueued before this call has reached the backend. */
    suspend fun flush() {
        val done = CompletableDeferred<Unit>()
        queue.send(Job.Flush(done))
        done.await()
    }
}

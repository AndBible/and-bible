/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */
package net.bible.android.control.widget

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.bible.sharedcore.log.Log

/** Which widget timelines a write makes stale. */
enum class WidgetReload {
    /** Every widget: the pools, the labels, the Bibles or the strings changed. */
    ALL,

    /** Only the widgets showing the speak state (Speak, Lock Screen, Listening list): a speak update. */
    SPEAK,
}

/**
 * Keeps the iOS widget snapshot current: rebuilds it on every [state] change (the producer already coalesces them),
 * hands it to [write] and asks the platform to reload the widget timelines. The verse pools are the expensive part
 * (labels x Bibles x [WidgetSnapshotBuilder.POOL_CAP] SWORD reads, up to ~2 MB of JSON), so they are cached, rebuilt only
 * when the labels, the Bibles, the requested Bibles, the language or the rotation week change, or [poolInvalidations]
 * fires (a bookmark or label changed), and written to their own file ([writePools]) only when they differ from the last
 * written pools. A speak update therefore writes the small state file and reloads [WidgetReload.SPEAK] only.
 * Writes are at least [minIntervalMs] apart; a snapshot equal to the last written one (ignoring
 * [WidgetSnapshot.generatedAt]) is not written again.
 */
class WidgetSnapshotPublisher(
    private val scope: CoroutineScope,
    private val state: StateFlow<SpeakWidgetState>,
    private val candidates: VerseCandidates,
    private val strings: WidgetStrings,
    private val requestedBibles: () -> Set<String>,
    private val zone: LocalZone,
    private val nowMs: () -> Long,
    private val poolInvalidations: Flow<Unit>,
    private val write: suspend (WidgetSnapshot) -> Unit,
    private val writePools: suspend (WidgetPools) -> Unit,
    private val reload: (WidgetReload) -> Unit,
    private val minIntervalMs: Long = MIN_INTERVAL_MS,
) {
    private val lock = Mutex()
    private val startLock = SynchronizedObject()
    private var job: Job? = null

    private var poolKey: PoolKey? = null
    private var cachedPools: List<VersePool> = emptyList()
    private var dirty = true
    private var lastWritten: WidgetSnapshot? = null
    private var lastWrittenPools: List<VersePool>? = null
    private var reloadAllPending = true

    /** How many times the pools were rebuilt; for tests. */
    var poolBuilds: Int = 0
        private set

    private data class PoolKey(
        val bibles: List<String>, val default: String?, val requested: Set<String>, val labels: List<Pair<String, String>>,
        val week: Long, val language: Int,
    )

    /** Starts following [state] and [poolInvalidations]; idempotent while running and safe to call concurrently. */
    fun start() {
        synchronized(startLock) {
            if (job?.isActive == true) return
            job = scope.launch {
                launch { poolInvalidations.collect { lock.withLock { dirty = true } } }
                launch {
                    state.collect {
                        publish()
                        delay(minIntervalMs)
                    }
                }
            }
        }
    }

    fun stop() {
        synchronized(startLock) {
            job?.cancel()
            job = null
        }
    }

    /** Publishes now (a foreground return, a changed widget request); the pools are rebuilt only when stale. */
    fun writeNow() {
        scope.launch { publish() }
    }

    private suspend fun publish() = lock.withLock {
        try {
            val now = nowMs()
            val pools = poolsFor(now)
            val snapshot = WidgetSnapshotBuilder.assemble(state.value, candidates, strings, now)
            if (pools != lastWrittenPools) {
                // Pools first: a snapshot never refers to pools that are not on disk yet.
                writePools(WidgetPools(pools = pools))
                lastWrittenPools = pools
                reloadAllPending = true
            }
            val last = lastWritten
            if (last != null && last.copy(generatedAt = snapshot.generatedAt) == snapshot && !reloadAllPending) return@withLock
            write(snapshot)
            val all = reloadAllPending || last == null || last.nonSpeakPart() != snapshot.nonSpeakPart()
            lastWritten = snapshot
            if (all) reloadAllPending = false
            reload(if (all) WidgetReload.ALL else WidgetReload.SPEAK)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A failing read or write keeps the previous snapshot on disk; the next event retries.
            Log.w(TAG, "widget snapshot not published", e)
        }
    }

    /** What the non-speak widgets read from the snapshot; a change there needs [WidgetReload.ALL]. */
    private fun WidgetSnapshot.nonSpeakPart() = listOf(labels, bibles, defaultBible, strings, version)

    private suspend fun poolsFor(now: Long): List<VersePool> {
        val requested = requestedBibles()
        val key = PoolKey(
            bibles = candidates.bibles().map { it.initials },
            default = candidates.defaultBible()?.initials,
            requested = requested,
            labels = candidates.labels().map { (id, name) -> id.toString() to name },
            week = VerseSelector.weekIndex(now, zone),
            language = strings.asMap().hashCode(),
        )
        if (dirty || key != poolKey) {
            cachedPools = WidgetSnapshotBuilder.pools(candidates, requested, now, zone)
            poolKey = key
            dirty = false
            poolBuilds++
        }
        return cachedPools
    }

    companion object {
        const val MIN_INTERVAL_MS = 1_000L
        private const val TAG = "WidgetSnapshotPublisher"
    }
}

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

import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Raw speak facts the producer turns into a [SpeakWidgetState]; everything platform specific lives behind this. */
data class SpeakFacts(
    val phase: SpeakPhase,
    /** Title of the passage being read (the TITLE text command); null for none. */
    val title: String?,
    /** Position being read, or the one Speak resumes from when paused or stopped; null when there is none yet. */
    val reference: String?,
    val translation: String?,
    val percent: Int?,
    val verseText: String,
    val nextVerseText: String?,
    val autoBookmarkOn: Boolean,
    /** Speak bookmark rows with `current = false`. */
    val rows: List<ListeningRow>,
    /** Id of the row being read, if any. */
    val currentBookmarkId: String?,
)

/** Where [SpeakWidgetStateProducer] reads from: the speak services on every platform. */
interface SpeakWidgetSource {
    /**
     * Fires when something a widget shows may have changed: speak state or progress, speak settings, speak bookmarks.
     * Bursts are fine, the producer coalesces them.
     */
    val changes: Flow<Unit>

    /** Epoch ms at which the sleep timer fires, or null when it is off. Cheap, it is polled by the ticker. */
    fun sleepActivationTimeMs(): Long?

    fun nowMs(): Long

    suspend fun facts(): SpeakFacts

    /** Forgets what an earlier run tracked from events. Not called on producer start: a paused session keeps its text. */
    fun reset() {}
}

/**
 * Turns the speak services into one [state] for the Speak, Speak-with-text and Listening-list widgets. A burst of
 * events yields one emission ([COALESCE_MS]); while a sleep timer runs a one-minute ticker keeps its remaining time
 * fresh. Never updates per word.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SpeakWidgetStateProducer(
    private val scope: CoroutineScope,
    private val source: SpeakWidgetSource,
    private val strings: WidgetStrings,
) {
    private val _state = MutableStateFlow(emptyState())
    val state: StateFlow<SpeakWidgetState> = _state.asStateFlow()

    private val job = atomic<Job?>(null)
    private val rebuildLock = Mutex()

    /**
     * Builds the first state and starts following [SpeakWidgetSource.changes]. Idempotent while running; starts again
     * when the previous run ended ([stop], or [scope]'s children cancelled by a database reset).
     */
    fun start() {
        val fresh = scope.launch(start = CoroutineStart.LAZY) {
            launch { rebuild() }
            launch { source.changes.debounce(COALESCE_MS).collect { rebuild() } }
            // The ticker only runs while a sleep timer is set; a timer change arrives as a change event.
            launch {
                source.changes.onStart { emit(Unit) }.collectLatest {
                    while (source.sleepActivationTimeMs() != null) {
                        delay(TICK_MS)
                        rebuild()
                    }
                }
            }
        }
        while (true) {
            val current = job.value
            if (current != null && !current.isCompleted) {
                fresh.cancel()
                return
            }
            if (job.compareAndSet(current, fresh)) {
                fresh.start()
                return
            }
        }
    }

    /** Stops following the source; [state] keeps its last value. [start] may be called again. */
    fun stop() {
        job.getAndSet(null)?.cancel()
    }

    /** Same as [stop]; the Koin `onClose` hook. */
    fun close() = stop()

    /** Rebuilds the state now (texts are locale dependent) after an in-app language change. */
    fun refreshForLocale() {
        scope.launch { rebuild() }
    }

    /** Serialised so an older result never overwrites a newer one; a failing read keeps the previous state. */
    private suspend fun rebuild() = rebuildLock.withLock {
        try {
            _state.value = build(source.facts())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // keep the last good state; the next event retries
        }
    }

    private fun build(f: SpeakFacts): SpeakWidgetState {
        val reference = f.reference
        val translation = f.translation.orEmpty()
        val statusLine = when {
            f.phase == SpeakPhase.SPEAKING && reference != null ->
                if (f.percent != null) strings.statusLine(reference, f.percent, translation)
                else if (translation.isEmpty()) reference else "$reference · $translation"
            reference != null -> strings.continueFrom(reference, translation)
            else -> strings.continueListening
        }
        val activation = source.sleepActivationTimeMs()
        val minutes = activation?.let { ((it - source.nowMs() + 59_999L) / 60_000L).toInt().coerceAtLeast(1) }
        return SpeakWidgetState(
            phase = f.phase,
            title = f.title,
            reference = reference,
            translation = f.translation,
            percent = f.percent,
            verseText = f.verseText,
            nextVerseText = f.nextVerseText,
            statusLine = statusLine,
            sleepMinutesLeft = minutes,
            listening = f.rows.map { it.copy(current = it.bookmarkId == f.currentBookmarkId) },
            autoBookmarkOn = f.autoBookmarkOn,
        )
    }

    private fun emptyState() = SpeakWidgetState(
        phase = SpeakPhase.STOPPED, title = null, reference = null, translation = null, percent = null,
        verseText = "", nextVerseText = null, statusLine = strings.continueListening, sleepMinutesLeft = null,
        listening = emptyList(), autoBookmarkOn = false,
    )

    companion object {
        const val COALESCE_MS = 500L
        const val TICK_MS = 60_000L
    }
}

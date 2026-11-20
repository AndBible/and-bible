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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

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

    private var started = false

    /** Builds the first state and starts following [SpeakWidgetSource.changes]. Idempotent. */
    fun start() {
        if (started) return
        started = true
        scope.launch { rebuild() }
        scope.launch {
            merge(source.changes, sleepTicker()).debounce(COALESCE_MS).collect { rebuild() }
        }
    }

    /** Rebuilds the state now (texts are locale dependent) after an in-app language change. */
    fun refreshForLocale() {
        scope.launch { rebuild() }
    }

    private fun sleepTicker(): Flow<Unit> = flow {
        while (true) {
            delay(TICK_MS)
            if (source.sleepActivationTimeMs() != null) emit(Unit)
        }
    }

    private suspend fun rebuild() {
        _state.value = build(source.facts())
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

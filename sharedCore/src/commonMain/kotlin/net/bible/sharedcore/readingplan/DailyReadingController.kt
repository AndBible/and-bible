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
package net.bible.sharedcore.readingplan

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One reading row: [passage] is the already-formatted key name; [isRead] mirrors ReadingStatus. */
data class ReadingItem(val readingNo: Int, val passage: String, val isRead: Boolean)

/** Whole-screen snapshot the host pushes on load/refresh. */
data class DailyReadingUi(
    val planName: String,
    val dayDesc: String,
    val dateString: String,
    val readings: List<ReadingItem>,
    val showSpeakAll: Boolean,
    val allRead: Boolean,
    val isDateBasedPlan: Boolean,
)

enum class SpeakState { NONE, SPEAKING, PAUSED }
enum class ConfirmKind { RESET, SET_CURRENT_DAY }

/**
 * Framework-free controller for the daily-reading screen. The host owns the truth (ReadingStatus,
 * ReadingPlanControl, SpeakControl) and pushes snapshots via [setUi]/[pushSpeakState]; every user
 * action forwards through a lambda seam. Reset & set-current-day are gated behind a confirm dialog.
 */
class DailyReadingController(
    private val onToggleRead: (Int) -> Unit,
    private val onRead: (Int) -> Unit,
    private val onSpeak: (Int) -> Unit,
    private val onSpeakAll: () -> Unit,
    private val onDone: () -> Unit,
    private val onPauseSpeak: () -> Unit,
    private val onStopSpeak: () -> Unit,
    private val onChangePlan: () -> Unit,
    private val onChangeDay: () -> Unit,
    private val onSetCurrentDay: () -> Unit,
    private val onReset: () -> Unit,
    private val onSetStartDate: () -> Unit,
    private val onImportPlan: () -> Unit,
) {
    private val _ui = MutableStateFlow(
        DailyReadingUi("", "", "", emptyList(), showSpeakAll = false, allRead = false, isDateBasedPlan = false)
    )
    val ui: StateFlow<DailyReadingUi> = _ui.asStateFlow()

    private val _speakState = MutableStateFlow(SpeakState.NONE)
    val speakState: StateFlow<SpeakState> = _speakState.asStateFlow()

    private val _confirm = MutableStateFlow<ConfirmKind?>(null)
    val confirm: StateFlow<ConfirmKind?> = _confirm.asStateFlow()

    private val _error = MutableStateFlow<ReadingPlanError?>(null)
    val error: StateFlow<ReadingPlanError?> = _error.asStateFlow()

    fun setUi(ui: DailyReadingUi) { _ui.value = ui }
    fun pushSpeakState(state: SpeakState) { _speakState.value = state }

    fun toggleRead(readingNo: Int) = onToggleRead(readingNo)
    fun read(readingNo: Int) = onRead(readingNo)
    fun speak(readingNo: Int) = onSpeak(readingNo)
    fun speakAll() = onSpeakAll()
    fun done() = onDone()
    fun pauseSpeak() = onPauseSpeak()
    fun stopSpeak() = onStopSpeak()
    fun changePlan() = onChangePlan()
    fun changeDay() = onChangeDay()
    fun setStartDate() = onSetStartDate()
    fun importPlan() = onImportPlan()

    fun requestReset() { _confirm.value = ConfirmKind.RESET }
    fun requestSetCurrentDay() { _confirm.value = ConfirmKind.SET_CURRENT_DAY }

    fun confirm() {
        val kind = _confirm.value
        _confirm.value = null
        when (kind) {
            ConfirmKind.RESET -> onReset()
            ConfirmKind.SET_CURRENT_DAY -> onSetCurrentDay()
            null -> {}
        }
    }

    fun dismissConfirm() { _confirm.value = null }
    fun showError() { _error.value = ReadingPlanError.FAILED }
    fun dismissError() { _error.value = null }
}

package net.bible.sharedcore.speak

import kotlinx.coroutines.flow.StateFlow

/**
 * Drives the main Speak screen. Speed + the three playback switches go straight to the service. The
 * sleep-timer toggle and the repeat-range choice are Android-owned (number picker / verse picker), so
 * they are host lambda seams: [onSleepTimerToggle] and [onChooseRepeatRange]. [toggleRepeatRange]
 * clears an existing range through the service (pure) or defers to the host to choose a new one.
 */
class BibleSpeakSettingsController(
    private val service: SpeakSettingsService,
    private val onSleepTimerToggle: (enabled: Boolean) -> Unit,
    private val onChooseRepeatRange: () -> Unit,
) {
    val playback: StateFlow<SpeakPlaybackVd> get() = service.playback

    fun setSpeed(percent: Int) = service.setSpeed(percent)
    fun setSpeakChapterChanges(on: Boolean) = service.setSpeakChapterChanges(on)
    fun setSpeakTitles(on: Boolean) = service.setSpeakTitles(on)
    fun setSpeakFootnotes(on: Boolean) = service.setSpeakFootnotes(on)

    fun setSleepTimerEnabled(enabled: Boolean) = onSleepTimerToggle(enabled)

    fun toggleRepeatRange() {
        if (service.playback.value.repeatRangeName != null) service.clearRepeatRange()
        else onChooseRepeatRange()
    }
}

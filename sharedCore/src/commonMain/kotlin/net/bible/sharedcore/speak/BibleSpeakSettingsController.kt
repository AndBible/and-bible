package net.bible.sharedcore.speak

import kotlinx.coroutines.flow.StateFlow

/**
 * Drives the main Speak screen. Speed + the three playback switches go straight to the service, as
 * do the sleep timer and the repeat verse-range (round 13a: both are now written straight through
 * [SpeakSettingsService] from shared UI, rather than via Android-owned host dialogs).
 */
class BibleSpeakSettingsController(private val service: SpeakSettingsService) {
    val playback: StateFlow<SpeakPlaybackVd> get() = service.playback

    fun setSpeed(percent: Int) = service.setSpeed(percent)
    fun setSpeakChapterChanges(on: Boolean) = service.setSpeakChapterChanges(on)
    fun setSpeakTitles(on: Boolean) = service.setSpeakTitles(on)
    fun setSpeakFootnotes(on: Boolean) = service.setSpeakFootnotes(on)

    fun setSleepTimerMinutes(minutes: Int) = service.setSleepTimerMinutes(minutes)
    fun setRepeatRange(startOsisId: String, endOsisId: String) = service.setRepeatRange(startOsisId, endOsisId)
    fun clearRepeatRange() = service.clearRepeatRange()
}

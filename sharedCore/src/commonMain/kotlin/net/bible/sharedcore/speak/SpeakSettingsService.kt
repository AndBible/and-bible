package net.bible.sharedcore.speak

import kotlinx.coroutines.flow.StateFlow

/**
 * Host seam over the DB-backed SpeakSettings + global AdvancedSpeakSettings. Keeps the shared layer
 * free of the Android/Room settings types. The impl saves through the classic
 * SpeakSettings.save(updateBookmark=true) path (so the SpeakSettingsChangedEvent broadcast is
 * unchanged) and re-emits [playback]/[advanced] when the settings change. Sleep-timer minutes and the
 * repeat verse-range are set by the host (Android dialogs); the service only reflects/clears them.
 */
interface SpeakSettingsService {
    val playback: StateFlow<SpeakPlaybackVd>
    val advanced: StateFlow<AdvancedSpeakVd>

    fun setSpeed(percent: Int)
    fun setSpeakChapterChanges(on: Boolean)
    fun setSpeakTitles(on: Boolean)
    fun setSpeakFootnotes(on: Boolean)
    fun clearRepeatRange()

    fun setSynchronize(on: Boolean)
    fun setReplaceDivineName(on: Boolean)
    fun setAutoBookmark(on: Boolean)
    fun setRestoreSettingsFromBookmarks(on: Boolean)
}

package net.bible.sharedcore.speak

import kotlinx.coroutines.flow.StateFlow

/**
 * Host seam over the DB-backed SpeakSettings + global AdvancedSpeakSettings. Keeps the shared layer
 * free of the Android/Room settings types. The impl saves through the classic
 * SpeakSettings.save(updateBookmark=true) path (so the SpeakSettingsChangedEvent broadcast is
 * unchanged) and re-emits [playback]/[advanced] when the settings change. Round 13a: the sleep timer
 * and the repeat verse-range are now written straight through this seam (from shared UI), not by an
 * Android-owned dialog.
 */
interface SpeakSettingsService {
    val playback: StateFlow<SpeakPlaybackVd>
    val advanced: StateFlow<AdvancedSpeakVd>

    fun setSpeed(percent: Int)
    fun setSpeakChapterChanges(on: Boolean)
    fun setSpeakTitles(on: Boolean)
    fun setSpeakFootnotes(on: Boolean)
    fun clearRepeatRange()

    /** 0 = off. A non-zero value is also remembered as the "last" timer for the next open. */
    fun setSleepTimerMinutes(minutes: Int)

    /** Both endpoints as OSIS ids. Ordering is validated by the caller (see `SpeakRangeEditor`);
     *  this seam only writes. */
    fun setRepeatRange(startOsisId: String, endOsisId: String)

    fun setSynchronize(on: Boolean)
    fun setReplaceDivineName(on: Boolean)
    fun setAutoBookmark(on: Boolean)
    fun setRestoreSettingsFromBookmarks(on: Boolean)
}

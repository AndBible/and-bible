package net.bible.sharedcore.settings

import kotlinx.coroutines.flow.StateFlow

/** Immutable snapshot the reading-progress/memorization settings screen renders from. */
data class ReadingProgressSettingsSnapshot(
    /** The GLOBAL (application-wide default) value of the AUTO_TRACK_READING text display setting. */
    val autoTrackReading: Boolean,
    val autoMarkMemorized: Boolean,
    val memorizeTypeFullWords: Boolean,
    val memorizeWordVisibility: String,
    val memorizeWordVisibilityChoices: List<Choice2>,   // Choice2 from AppSettingsService.kt (10a)
    val memorizeErrorHeatmap: Boolean,
    val memorizeScrambleHideUsed: Boolean,
    val memorizeIncludeReference: Boolean,
)

interface ReadingProgressSettingsService {
    val snapshot: StateFlow<ReadingProgressSettingsSnapshot>
    fun setBool(key: String, value: Boolean)
    fun setString(key: String, value: String)
    fun refresh()
}

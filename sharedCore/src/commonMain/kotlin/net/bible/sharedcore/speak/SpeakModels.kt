package net.bible.sharedcore.speak

/** Playback settings shown on the main Speak screen. Mirrors SpeakSettings.playbackSettings + sleepTimer. */
data class SpeakPlaybackVd(
    val speedPercent: Int,
    val speakChapterChanges: Boolean,
    val speakTitles: Boolean,
    val speakFootnotes: Boolean,
    val sleepTimerMinutes: Int,      // 0 = off
    val repeatRangeName: String?,    // null = no repeat range
)

/** Advanced (rarely-changed) speak settings. Mirrors AdvancedSpeakSettings. */
data class AdvancedSpeakVd(
    val synchronize: Boolean,
    val replaceDivineName: Boolean,
    val autoBookmark: Boolean,
    val restoreSettingsFromBookmarks: Boolean,
)

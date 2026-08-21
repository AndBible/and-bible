package net.bible.sharedcore.speak

/** A verse the user picked, carrying everything the pure range editor needs.
 *  [ordinal] is supplied by the host (JSword `Verse.ordinal` is JVM-only) specifically so the
 *  start-before-end rule stays pure Kotlin and unit-testable. */
data class PickedVerse(val osisId: String, val label: String, val ordinal: Int)

/** Playback settings shown on the main Speak screen. Mirrors SpeakSettings.playbackSettings + sleepTimer. */
data class SpeakPlaybackVd(
    val speedPercent: Int,
    val speakChapterChanges: Boolean,
    val speakTitles: Boolean,
    val speakFootnotes: Boolean,
    val sleepTimerMinutes: Int,          // 0 = off
    val repeatRangeName: String?,        // null = no repeat range
    val lastSleepTimerMinutes: Int = 10, // seeds the sleep-timer page's custom slider
    val repeatRangeStart: PickedVerse? = null,
    val repeatRangeEnd: PickedVerse? = null,
)

/** Advanced (rarely-changed) speak settings. Mirrors AdvancedSpeakSettings. */
data class AdvancedSpeakVd(
    val synchronize: Boolean,
    val replaceDivineName: Boolean,
    val autoBookmark: Boolean,
    val restoreSettingsFromBookmarks: Boolean,
)

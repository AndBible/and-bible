package net.bible.sharedcore.speak

/** Row for the speak-from-bookmark chooser. id = IdType.toString(); addressing key, never a list index. */
data class SpeakBookmarkRowVd(val id: String, val label: String)

/** Seam-level snapshot bridged from SpeakControl + the visibility SSOT (MainBibleActivity.transportBarVisible). */
data class SpeakTransportStateVd(
    val visible: Boolean = false,
    val speaking: Boolean = false,
    val paused: Boolean = false,
    val stopped: Boolean = true,
    val statusText: String = "",
    val bookmarkButtonVisible: Boolean = false,
)

/** Full bar UI state = seam state + the Batch-6 speed. */
data class SpeakTransportVd(
    val visible: Boolean = false,
    val playing: Boolean = false,       // true ⇒ show the pause icon
    val paused: Boolean = false,
    val stopped: Boolean = true,
    val statusText: String = "",
    val speedPercent: Int = 100,
    val bookmarkButtonVisible: Boolean = false,
)

sealed interface SpeakTransportDialog {
    data object None : SpeakTransportDialog
    data class ChooseSpeakBookmark(val rows: List<SpeakBookmarkRowVd>) : SpeakTransportDialog
}

package net.bible.sharedcore.speak

import kotlinx.coroutines.flow.StateFlow

/**
 * Host seam over the Android SpeakControl transport + the reading-view visibility SSOT. The impl
 * bridges SpeakChanges / SpeakSettingsChanges and setTransportVisible into [state].
 * Granular play/pause/continue so the branch is
 * decided (and tested) in the controller.
 */
interface SpeakTransportService {
    val state: StateFlow<SpeakTransportStateVd>
    fun speakAny()              // else-branch: start speaking (+ classic AdvancedSpeakSettings.synchronize side-effect)
    fun pause()
    fun continueAfterPause()
    fun stop()                  // isStopped ⇒ setTransportVisible(false) (hide) else SpeakControl.stop()
    fun rewind()
    fun forward()
    fun prevVerse()             // rewind(ONE_VERSE)
    fun nextVerse()             // forward(ONE_VERSE)
    fun speakBookmarks(): List<SpeakBookmarkRowVd>
    fun speakFromBookmark(id: String)
}

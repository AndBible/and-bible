package net.bible.sharedcore.speak

import kotlinx.coroutines.flow.StateFlow

/** Drives the advanced Speak settings screen — four booleans, straight through to the service. */
class AdvancedSpeakSettingsController(private val service: SpeakSettingsService) {
    val advanced: StateFlow<AdvancedSpeakVd> get() = service.advanced

    fun setSynchronize(on: Boolean) = service.setSynchronize(on)
    fun setReplaceDivineName(on: Boolean) = service.setReplaceDivineName(on)
    fun setAutoBookmark(on: Boolean) = service.setAutoBookmark(on)
    fun setRestoreSettingsFromBookmarks(on: Boolean) = service.setRestoreSettingsFromBookmarks(on)
}

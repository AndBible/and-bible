package net.bible.android.control.speak

import net.bible.sharedcore.platform.AppSettings

class AdvancedSpeakSettings(private val settings: AppSettings) {
    var autoBookmark: Boolean
        get() = settings.getBoolean("speak_autoBookmark", false)
        set(value) {
            settings.setBoolean("speak_autoBookmark", value)
        }

    var synchronize: Boolean
        get() = settings.getBoolean("speak_synchronize", true)
        set(value) {
            settings.setBoolean("speak_synchronize", value)
        }

    var replaceDivineName: Boolean
        get() = settings.getBoolean("speak_replaceDivineName", false)
        set(value) {
            settings.setBoolean("speak_replaceDivineName", value)
        }

    var restoreSettingsFromBookmarks: Boolean
        get() = settings.getBoolean("speak_restoreSettingsFromBookmarks", false)
        set(value) {
            settings.setBoolean("speak_restoreSettingsFromBookmarks", value)
        }

    fun reset() {
        autoBookmark = false
        synchronize = true
        replaceDivineName = false
        restoreSettingsFromBookmarks = false
    }
}

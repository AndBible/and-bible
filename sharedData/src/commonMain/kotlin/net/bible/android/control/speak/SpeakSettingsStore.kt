package net.bible.android.control.speak

/** Raw JSON storage kept separate from the settings database for early widget startup. */
interface SpeakSettingsStore {
    fun load(): String?
    fun save(json: String)
}

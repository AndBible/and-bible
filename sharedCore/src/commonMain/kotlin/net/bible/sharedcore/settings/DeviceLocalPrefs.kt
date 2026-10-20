package net.bible.sharedcore.settings

/** Preferences that must remain on this device rather than in a synchronized database. */
interface DeviceLocalPrefs {
    fun getString(key: String, default: String?): String?
    fun getBoolean(key: String, default: Boolean): Boolean
    fun setString(key: String, value: String)
    fun setBoolean(key: String, value: Boolean)
    fun remove(key: String)
}

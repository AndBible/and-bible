package net.bible.sharedcore.settings

/** Preferences that must remain on this device rather than in a synchronized database. */
interface DeviceLocalPrefs {
    fun getString(key: String, default: String?): String?
    fun getBoolean(key: String, default: Boolean): Boolean
    fun setString(key: String, value: String)
    /**
     * Like [setString], but the value has reached durable storage when this returns (Android: `commit()` instead of
     * the asynchronous `apply()`). For a record that must survive the process dying right afterwards. Call it off the
     * main thread; platforms whose writes are already synchronous keep this default.
     */
    fun setStringDurably(key: String, value: String) = setString(key, value)
    fun setBoolean(key: String, value: Boolean)
    fun remove(key: String)
}

package net.bible.sharedui

import net.bible.sharedcore.platform.AppSettings

/** In-memory [AppSettings] for tests; [values] is keyed `"<Type>:<key>"` and only holds what was explicitly set. */
class MemoryAppSettings : AppSettings {
    val values = mutableMapOf<String, Any?>()
    override fun getString(key: String, default: String?) = values["String:$key"] as? String ?: default
    override fun getLong(key: String, default: Long) = values["Long:$key"] as? Long ?: default
    override fun getInt(key: String, default: Int) = values["Int:$key"] as? Int ?: default
    override fun getBoolean(key: String, default: Boolean) = values["Boolean:$key"] as? Boolean ?: default
    override fun getDouble(key: String, default: Double) = values["Double:$key"] as? Double ?: default
    override fun getFloat(key: String, default: Float) = values["Float:$key"] as? Float ?: default
    override fun setString(key: String, value: String?) { values["String:$key"] = value }
    override fun setLong(key: String, value: Long?) { values["Long:$key"] = value }
    override fun setInt(key: String, value: Int?) { values["Int:$key"] = value }
    override fun setBoolean(key: String, value: Boolean?) { values["Boolean:$key"] = value }
    override fun setDouble(key: String, value: Double?) { values["Double:$key"] = value }
    override fun setFloat(key: String, value: Float?) { values["Float:$key"] = value }
}

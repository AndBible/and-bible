package net.bible.sharedcore.platform

import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

/**
 * Typed key/value settings (Android: `CommonUtils.AndBibleSettings` over `SettingsStore`).
 * Reads before the backing store exists answer the default; that guard is the implementation's.
 */
interface AppSettings {
    fun getString(key: String, default: String? = null): String?
    fun getLong(key: String, default: Long): Long
    fun getInt(key: String, default: Int): Int
    fun getBoolean(key: String, default: Boolean): Boolean
    fun getDouble(key: String, default: Double): Double
    fun getFloat(key: String, default: Float): Float
    fun setString(key: String, value: String?)
    fun setLong(key: String, value: Long?)
    fun setInt(key: String, value: Int?)
    fun setBoolean(key: String, value: Boolean?)
    fun setDouble(key: String, value: Double?)
    fun setFloat(key: String, value: Float?)

    fun getStringSet(key: String, defValues: Set<String> = emptySet()): Set<String> {
        val s = getString(key, null) ?: return defValues
        return try { AppJson.decodeFromString(SetSerializer(String.serializer()), s) } catch (e: SerializationException) { defValues }
    }

    fun setStringSet(key: String, values: Set<String>?) {
        if (values == null) removeString(key)
        else setString(key, AppJson.encodeToString(SetSerializer(String.serializer()), values))
    }

    fun removeString(key: String) = setString(key, null)
    fun removeLong(key: String) = setLong(key, null)
    fun removeDouble(key: String) = setDouble(key, null)
    fun removeBoolean(key: String) = setBoolean(key, null)
}

inline fun <reified T> AppSettings.getEnumSet(key: String, defValues: Set<T> = emptySet()): Set<T> {
    val s = getString(key, null) ?: return defValues
    return try { AppJson.decodeFromString(s) } catch (e: SerializationException) { defValues } catch (e: IllegalArgumentException) { defValues }
}

inline fun <reified T> AppSettings.setEnumSet(key: String, values: Set<T>) {
    setString(key, AppJson.encodeToString(values))
}

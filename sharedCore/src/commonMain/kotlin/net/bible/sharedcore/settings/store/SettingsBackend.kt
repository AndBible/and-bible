package net.bible.sharedcore.settings.store

/** All settings as loaded from storage, grouped by value type (each type is its own key namespace). */
data class SettingsSnapshot(
    val booleans: Map<String, Boolean>,
    val longs: Map<String, Long>,
    val strings: Map<String, String>,
    val doubles: Map<String, Double>,
)

/** One write to storage. A `null` value means delete the key. */
sealed interface SettingWrite {
    val key: String

    data class Bool(override val key: String, val value: Boolean?) : SettingWrite
    data class Lng(override val key: String, val value: Long?) : SettingWrite
    data class Str(override val key: String, val value: String?) : SettingWrite
    data class Dbl(override val key: String, val value: Double?) : SettingWrite
}

/**
 * Storage behind [SettingsStore]; `null` in a write means delete.
 */
interface SettingsBackend {
    suspend fun loadAll(): SettingsSnapshot
    suspend fun write(write: SettingWrite)
}

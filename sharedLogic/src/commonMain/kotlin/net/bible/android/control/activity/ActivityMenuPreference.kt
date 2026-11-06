package net.bible.android.control.activity

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.bible.sharedcore.platform.ActivityMenuDefault
import net.bible.sharedcore.platform.AppSettings

/**
 * Whether background activity (downloads, sync, indexing) is shown in the overflow menu (#4007).
 *
 * Stored under [KEY] in the shared [AppSettings] store, the same store the settings screen row writes through
 * (`AppSettingsServiceImpl`, which routes that key via [set] so [enabled] emits). Unset means the platform
 * [default]; reading never persists the default, so a later change of the default still reaches users who never
 * touched the switch.
 *
 * The backing store may answer the default until the settings database is open (`DatabaseAppSettings.getBoolean`),
 * so the value read at construction can be stale. [enabled] is a plain flow; the owner of the startup calls [refresh]
 * once the settings are readable: desktop and iOS when their databases open, Android when the reading app has been
 * initialised (`ReadingAppBootstrap.showFirstRunNotices`, where `CommonUtils.settings` starts answering stored values).
 */
class ActivityMenuPreference(
    private val settings: AppSettings,
    private val default: ActivityMenuDefault,
) {
    private val _enabled = MutableStateFlow(read())

    /** Current value; emits when [set] or [refresh] changes it (including from the settings screen). */
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    /** Re-reads the store; call once the settings database is ready. */
    fun refresh() { _enabled.value = read() }

    /** Stores [enabled] explicitly (it then wins over the platform default) and publishes it. */
    fun set(enabled: Boolean) {
        settings.setBoolean(KEY, enabled)
        _enabled.value = enabled
    }

    private fun read(): Boolean = settings.getBoolean(KEY, default.enabledByDefault())

    companion object {
        const val KEY = "show_activity_in_menu"
    }
}

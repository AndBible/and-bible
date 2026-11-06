package net.bible.android.control.activity

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.bible.sharedcore.platform.AppSettings

/**
 * Whether background activity is also reported through native system notifications (#4012).
 *
 * Stored under [KEY], separate from the in-app [ActivityMenuPreference]. Unset means [defaultEnabled]; reading never
 * persists the default. [enabled] does not re-read the store on access: the backing store may answer the default until
 * the settings database is open, so call [refresh] when the databases open (desktop startup). [set] writes through
 * and publishes, and is what the settings row uses.
 *
 * Bound in Koin only on platforms that have a native notifier; elsewhere the settings row is hidden.
 */
class SystemNotificationsPreference(
    private val settings: AppSettings,
    private val defaultEnabled: Boolean,
) {
    private val _enabled = MutableStateFlow(read())

    /** Current value; emits when [set] or [refresh] changes it. */
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    /** Re-reads the store; call once the settings database is ready. */
    fun refresh() { _enabled.value = read() }

    /** Stores [enabled] explicitly (it then wins over the default) and publishes it. */
    fun set(enabled: Boolean) {
        settings.setBoolean(KEY, enabled)
        _enabled.value = enabled
    }

    private fun read(): Boolean = settings.getBoolean(KEY, defaultEnabled)

    companion object {
        const val KEY = "system_notifications"
    }
}

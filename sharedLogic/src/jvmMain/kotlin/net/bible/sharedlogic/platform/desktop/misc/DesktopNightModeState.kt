package net.bible.sharedlogic.platform.desktop.misc

import net.bible.sharedcore.platform.desktop.DesktopDeviceSettings
import net.bible.sharedlogic.platform.NightModeState
import net.bible.sharedlogic.platform.resolveNightMode

class DesktopNightModeState(private val settings: DesktopDeviceSettings) : NightModeState {
    val mode: String get() = settings.getString("night_mode_pref3", "system") ?: "system"
    val manual: Boolean get() = mode == "manual"
    override val nightMode: Boolean get() = resolveNightMode(mode, settings.getBoolean("night_mode_pref", false), systemDark = false, autoReading = null)

    fun observeChanges(onChange: () -> Unit): () -> Unit {
        val lock = Any()
        var previous = snapshot()
        return settings.observeChanges {
            val changed = synchronized(lock) { snapshot().let { now -> (now != previous).also { previous = now } } }
            if (changed) onChange()
        }
    }

    private fun snapshot() = listOf(settings.getString("night_mode_pref3"), settings.getString("night_mode_pref"))
}

package net.bible.sharedui.desktop.settings

import net.bible.sharedcore.platform.desktop.DesktopDeviceSettings
import net.bible.sharedui.settings.DESKTOP_UI_SCALE_DEFAULT
import net.bible.sharedui.settings.DESKTOP_UI_SCALE_KEY
import net.bible.sharedui.settings.DESKTOP_UI_SCALE_PRESETS
import net.bible.sharedui.settings.DesktopUiScaleSetting

class PersistentDesktopUiScaleSetting(private val settings: DesktopDeviceSettings) : DesktopUiScaleSetting {
    override val percent: Int
        get() = settings.getInt(DESKTOP_UI_SCALE_KEY, DESKTOP_UI_SCALE_DEFAULT)
            .takeIf { it in DESKTOP_UI_SCALE_PRESETS } ?: DESKTOP_UI_SCALE_DEFAULT

    override fun setPercent(percent: Int) {
        require(percent in DESKTOP_UI_SCALE_PRESETS)
        settings.putInt(DESKTOP_UI_SCALE_KEY, percent)
    }

    override fun observeChanges(onChange: () -> Unit): () -> Unit = settings.observeChanges(onChange)
}

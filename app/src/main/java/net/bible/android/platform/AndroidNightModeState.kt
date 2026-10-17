package net.bible.android.platform

import net.bible.service.device.ScreenSettings
import net.bible.sharedlogic.platform.NightModeState

object AndroidNightModeState : NightModeState {
    override val nightMode: Boolean get() = ScreenSettings.nightMode
}

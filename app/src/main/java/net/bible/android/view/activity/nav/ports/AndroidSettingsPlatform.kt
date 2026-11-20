package net.bible.android.view.activity.nav.ports

import net.bible.android.view.activity.settings.SettingsReset
import net.bible.service.common.BuildVariant
import net.bible.sharedui.app.ports.SettingsPlatform

class AndroidSettingsPlatform() : SettingsPlatform {
    override fun resetAllSettings() = SettingsReset.performReset()

    override val isDiscreteBuild: Boolean get() = BuildVariant.Appearance.isDiscrete
}

package net.bible.android.view.activity.nav.ports

import android.content.Context
import net.bible.android.view.activity.settings.SettingsReset
import net.bible.service.common.BuildVariant
import net.bible.sharedui.app.ports.SettingsPlatform

class AndroidSettingsPlatform(private val context: Context) : SettingsPlatform {
    override fun resetAllSettings() = SettingsReset.performReset()

    override val isDiscreteBuild: Boolean get() = BuildVariant.Appearance.isDiscrete

    // Classic's android.R.string.ok / cancel: the SYSTEM strings, localized by the platform.
    override val confirmText: String get() = context.getString(android.R.string.ok)
    override val dismissText: String get() = context.getString(android.R.string.cancel)
}

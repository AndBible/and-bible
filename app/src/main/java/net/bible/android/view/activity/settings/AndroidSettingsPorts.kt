package net.bible.android.view.activity.settings

import android.os.Build
import net.bible.android.activity.BuildConfig
import net.bible.android.view.activity.nav.SystemBarSettingChanges
import net.bible.service.common.BuildVariant
import net.bible.service.common.CommonUtils
import net.bible.service.device.ScreenSettings
import net.bible.sharedcore.settings.DeviceLocalPrefs
import net.bible.sharedcore.settings.SettingsPlatformFlags

class AndroidDeviceLocalPrefs : DeviceLocalPrefs {
    override fun getString(key: String, default: String?) = CommonUtils.realSharedPreferences.getString(key, default)
    override fun getBoolean(key: String, default: Boolean) = CommonUtils.realSharedPreferences.getBoolean(key, default)
    override fun setString(key: String, value: String) { CommonUtils.realSharedPreferences.edit().putString(key, value).apply() }
    override fun setBoolean(key: String, value: Boolean) { CommonUtils.realSharedPreferences.edit().putBoolean(key, value).apply() }
    override fun remove(key: String) { CommonUtils.realSharedPreferences.edit().remove(key).apply() }
}

class AndroidSettingsPlatformFlags : SettingsPlatformFlags {
    override val sdcardPermissionVisible get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
    override val openLinksVisible get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    override val debugToolsVisible get() = BuildConfig.DEBUG
    override val autoNightModeAvailable get() = ScreenSettings.autoModeAvailable
    override val isOnyx get() = CommonUtils.isOnyxDevice
    override val isDiscreteBuild get() = BuildVariant.Appearance.isDiscrete
    override val isBeta get() = CommonUtils.isBeta
    override fun onSystemBarSettingChanged() = SystemBarSettingChanges.notifyChanged()
}

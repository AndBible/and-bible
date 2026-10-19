package net.bible.sharedcore.settings

/** Host capabilities used when constructing an app-settings snapshot. */
interface SettingsPlatformFlags {
    val sdcardPermissionVisible: Boolean
    val openLinksVisible: Boolean
    val debugToolsVisible: Boolean
    val autoNightModeAvailable: Boolean
    val isOnyx: Boolean
    val isDiscreteBuild: Boolean get() = false
    val isBeta: Boolean get() = false
    fun onSystemBarSettingChanged()
}

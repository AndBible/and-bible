package net.bible.sharedcore.settings

/** Host capabilities used when constructing an app-settings snapshot. */
interface SettingsPlatformFlags {
    val sdcardPermissionVisible: Boolean
    val openLinksVisible: Boolean
    val debugToolsVisible: Boolean
    val autoNightModeAvailable: Boolean
    val isOnyx: Boolean
    val doubleTapFullscreenAvailable: Boolean get() = true
    val isDiscreteBuild: Boolean get() = false
    val isBeta: Boolean get() = false
    /** Whether the "import data from the previous app version" row is offered (iOS only; false elsewhere). */
    val legacyImportVisible: Boolean get() = false
    /** Called after an app-settings write so hosts can refresh consumers of live settings. Android keeps its own lifecycle refresh. */
    fun onSettingChanged(key: String) = Unit
    fun onSystemBarSettingChanged()
}

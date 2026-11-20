@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.app.ports

/** What the settings cluster needs from the platform beyond the generic ports. */
@kotlin.native.HiddenFromObjC
interface SettingsPlatform {
    /** Clears the app's preferences to their defaults (Android: `SettingsReset.performReset()`). */
    fun resetAllSettings()

    /** Whether this is the discrete (calculator-disguise) build (Android: `BuildVariant.Appearance.isDiscrete`). */
    val isDiscreteBuild: Boolean
}

package net.bible.sharedui.app.ports

/** What the settings cluster needs from the platform beyond the generic ports. */
interface SettingsPlatform {
    /** Clears the app's preferences to their defaults (Android: `SettingsReset.performReset()`). */
    fun resetAllSettings()

    /** Whether this is the discrete (calculator-disguise) build (Android: `BuildVariant.Appearance.isDiscrete`). */
    val isDiscreteBuild: Boolean

    /**
     * The platform's own OK / Cancel words (Android: `android.R.string.ok` / `cancel`, localized by the system,
     * not the app's `okay` / `cancel`). Not `Strings` members: the `:strings-gen` mapping only knows `R.string`.
     */
    val confirmText: String
    val dismissText: String
}

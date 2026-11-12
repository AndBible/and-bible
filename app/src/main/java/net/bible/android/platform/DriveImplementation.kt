package net.bible.android.platform

/**
 * Chooses between the shared Drive REST adapter and the legacy Play Services adapter.
 * No Play Services imports: this compiles on F-Droid, where the provider class does not exist.
 */
object DriveImplementation {
    /** Hidden preference (no UI) in the real shared preferences. */
    const val PREF_KEY = "google_drive_implementation"
    const val SHARED = "shared"
    const val LEGACY = "legacy"

    /** [SHARED] wins, [LEGACY] loses, anything else (including null) falls back to [isBeta]. */
    fun useShared(isBeta: Boolean, override: String?): Boolean = when (override) {
        SHARED -> true
        LEGACY -> false
        else -> isBeta
    }
}

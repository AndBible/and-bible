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

    /**
     * The shared adapter is the default on every Android channel; only an explicit [LEGACY] keeps the old adapter
     * (kept until the old adapter and the Java Drive libraries are removed).
     */
    fun useShared(override: String?): Boolean = override != LEGACY
}

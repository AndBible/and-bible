package net.bible.android.platform

import net.bible.service.common.BuildVariant

/** How this build signs in to Google Drive. */
enum class AndroidDriveSignIn {
    /** The reflectively loaded Play provider; it falls back to the browser at runtime without Play services. */
    PLAY_PROVIDER,

    /** F-Droid: the browser sign-in only, no Play class is ever loaded. */
    BROWSER,

    /** No Drive: discrete F-Droid has no App Link, so the browser redirect could never come back. */
    NONE;

    companion object {
        fun current(): AndroidDriveSignIn = when {
            !BuildVariant.DistributionChannel.isFdroid -> PLAY_PROVIDER
            BuildVariant.Appearance.isDiscrete -> NONE
            else -> BROWSER
        }
    }
}

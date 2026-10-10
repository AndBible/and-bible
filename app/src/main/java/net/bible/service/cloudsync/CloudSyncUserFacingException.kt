package net.bible.service.cloudsync

/**
 * A sync failure the user must see and act on (wrong credentials, changed certificate, full
 * storage). Deliberately not an IOException, which the sync layer would retry silently.
 * [requiresReconnect]: the adapter must be dropped until the user signs in again.
 */
class CloudSyncUserFacingException(message: String, val requiresReconnect: Boolean, cause: Throwable? = null) :
    Exception(message, cause)

/**
 * Thrown by [CloudAdapter.signIn] when the user themselves declined to continue (for example refused a
 * certificate trust prompt). [CloudSync.signIn] treats it as a quiet failed sign-in: no "Sign in failed" dialog.
 */
class CloudSyncUserCancelledException : Exception("Sign-in cancelled by the user")

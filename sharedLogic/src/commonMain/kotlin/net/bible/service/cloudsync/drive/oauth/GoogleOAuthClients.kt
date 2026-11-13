package net.bible.service.cloudsync.drive.oauth

/**
 * The OAuth clients of the iOS and desktop Drive sign-in. A `null` client means Google Drive is not offered on that
 * platform. They are filled with the maintainer's client ids (same Google Cloud project as the Android client) in
 * phase 3; the desktop also honours the JVM properties read by `desktopClient()` (jvmMain) for testing.
 */
object GoogleOAuthClients {
    /** The iOS client: no secret, redirect is the reversed client id scheme. */
    val ios: GoogleOAuthClient? = null

    /** The Desktop client; Google treats its secret as non-confidential. */
    val desktop: GoogleOAuthClient? = null
}

package net.bible.service.cloudsync.drive.oauth

/** Opens Google's consent page for the user and hands back the redirect Google sent. */
interface OAuthBrowser {
    /**
     * Picks the redirect URI (desktop: binds the loopback port first), opens `buildUrl(redirectUri)` and returns the
     * full redirect URL Google sent back. Resources are released on return, failure and cancellation.
     *
     * @throws net.bible.service.cloudsync.CloudSyncUserCancelledException when the user cancels or the wait times out.
     */
    suspend fun authorize(buildUrl: (redirectUri: String) -> String): String
}

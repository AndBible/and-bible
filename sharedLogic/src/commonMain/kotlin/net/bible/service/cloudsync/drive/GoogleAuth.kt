package net.bible.service.cloudsync.drive

import okio.IOException

/**
 * Token-only Google auth. PKCE, refresh-token storage and redirects belong to implementations, never here.
 *
 * The Drive adapter asks for access tokens only; it never sees client secrets, refresh tokens or
 * authorization codes. Platform implementations decide how a token is obtained and cached.
 */
interface GoogleAuth {
    /** Whether a usable sign-in is currently held (it may still need a token refresh). */
    val signedIn: Boolean

    /**
     * Interactive. Returns true when the user signed in, false when sign-in failed.
     *
     * @throws CloudSyncUserCancelledException when the user cancels the sign-in flow.
     */
    suspend fun signIn(): Boolean

    /**
     * Non-interactive. Returns a valid access token, refreshing it silently when possible.
     *
     * @throws GoogleAuthRequiredException when only an interactive sign-in can help.
     */
    suspend fun accessToken(): String

    /** Drops [token] from any cache so the next [accessToken] call returns a fresh one. */
    suspend fun invalidate(token: String)

    /** Forgets the signed-in account and any cached tokens. */
    suspend fun signOut()
}

/**
 * Thrown by [GoogleAuth.accessToken] when only an interactive sign-in can produce a token
 * (for example, the refresh token was revoked or never existed).
 */
class GoogleAuthRequiredException(message: String, cause: Throwable? = null) : IOException(message, cause)

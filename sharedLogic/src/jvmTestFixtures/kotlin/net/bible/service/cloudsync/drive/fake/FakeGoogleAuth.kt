package net.bible.service.cloudsync.drive.fake

import net.bible.service.cloudsync.CloudSyncUserCancelledException
import net.bible.service.cloudsync.drive.GoogleAuth
import net.bible.service.cloudsync.drive.GoogleAuthRequiredException

/**
 * Test double for [GoogleAuth]. Hands out "t1", "t2", ... : [accessToken] keeps returning the current token until it
 * is [invalidate]d, after which the next call mints the next one.
 */
class FakeGoogleAuth(
    override var signedIn: Boolean = true,
    /** What [signIn] returns (and sets [signedIn] to). */
    var signInResult: Boolean = true,
    /** When true, [signIn] throws [CloudSyncUserCancelledException]. */
    var cancelSignIn: Boolean = false,
    /** When set, [accessToken] throws it instead of returning a token. */
    var accessTokenFailure: Throwable? = null,
) : GoogleAuth {
    private val lock = Any()
    private var counter = 0
    private val issued = ArrayList<String>()
    private val invalidated = ArrayList<String>()

    /** The token [accessToken] returns now, or null when the next call would mint a new one. */
    var currentToken: String? = null
        private set

    /** Every token handed out so far, in order. */
    val issuedTokens: List<String> get() = synchronized(lock) { issued.toList() }

    /** Tokens passed to [invalidate], in order. */
    val invalidateCalls: List<String> get() = synchronized(lock) { invalidated.toList() }
    var signInCalls = 0
        private set
    var signOutCalls = 0
        private set

    override suspend fun signIn(): Boolean = synchronized(lock) {
        signInCalls++
        if (cancelSignIn) throw CloudSyncUserCancelledException()
        signedIn = signInResult
        signInResult
    }

    override suspend fun accessToken(): String = synchronized(lock) {
        accessTokenFailure?.let { throw it }
        if (!signedIn) throw GoogleAuthRequiredException("Not signed in")
        currentToken ?: "t${++counter}".also { currentToken = it; issued += it }
    }

    override suspend fun invalidate(token: String) = synchronized(lock) {
        invalidated += token
        if (token == currentToken) currentToken = null
    }

    override suspend fun signOut() = synchronized(lock) {
        signOutCalls++
        signedIn = false
        currentToken = null
    }
}

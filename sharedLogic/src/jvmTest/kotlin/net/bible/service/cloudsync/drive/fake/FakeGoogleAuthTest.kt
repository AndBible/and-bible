package net.bible.service.cloudsync.drive.fake

import kotlinx.coroutines.runBlocking
import net.bible.service.cloudsync.drive.GoogleAuthRequiredException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FakeGoogleAuthTest {
    @Test
    fun handsOutNewTokenOnlyAfterInvalidate() = runBlocking<Unit> {
        val auth = FakeGoogleAuth()
        assertEquals("t1", auth.accessToken())
        assertEquals("t1", auth.accessToken())
        auth.invalidate("stale") // not the current token: no effect
        assertEquals("t1", auth.accessToken())
        auth.invalidate("t1")
        assertEquals("t2", auth.accessToken())
        assertEquals(listOf("stale", "t1"), auth.invalidateCalls)
        assertEquals(listOf("t1", "t2"), auth.issuedTokens)
    }

    @Test
    fun signedOutRequiresInteractiveSignIn() = runBlocking<Unit> {
        val auth = FakeGoogleAuth(signedIn = false)
        assertFailsWith<GoogleAuthRequiredException> { auth.accessToken() }
        assertEquals(true, auth.signIn())
        assertEquals("t1", auth.accessToken())
        auth.signOut()
        assertFailsWith<GoogleAuthRequiredException> { auth.accessToken() }
    }
}

package net.bible.service.cloudsync.googledrive

import android.accounts.Account
import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import androidx.activity.result.ActivityResult
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Status
import kotlinx.coroutines.runBlocking
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.view.activity.base.ActivityBase
import net.bible.service.cloudsync.CloudSyncUserCancelledException
import net.bible.service.cloudsync.drive.GoogleAuthRequiredException
import net.bible.service.common.CommonUtils
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class AndroidGoogleAuthTest {
    class AuthActivity : ActivityBase() {
        override val doNotInitializeApp = true
        override val allowThemeChange = false
        override val disableBaseSetupUi = true
    }

    /** Scripted [DriveAuthorizer]: answers [authorize] calls in order and records the requested accounts. */
    private class FakeAuthorizer(
        vararg answers: () -> DriveAuthorization,
        val fromIntent: () -> DriveAuthorization = { fail("no resolution expected"); throw AssertionError() },
    ) : DriveAuthorizer {
        private val queue = ArrayDeque(answers.toList())
        val requested = mutableListOf<Account?>()
        val cleared = mutableListOf<String>()
        override suspend fun authorize(account: Account?): DriveAuthorization {
            requested += account
            val next = queue.removeFirstOrNull() ?: throw AssertionError("unexpected authorize($account)")
            return next()
        }
        override fun resultFromIntent(data: Intent?) = fromIntent()
        override suspend fun clearToken(token: String) { cleared += token }
    }

    private val key = "cloud_sync_last_account"
    private val prefs get() = CommonUtils.realSharedPreferences
    private var oldAccount: String? = null
    private val launched = mutableListOf<PendingIntent>()

    @Before fun saveState() {
        oldAccount = prefs.getString(key, null)
        prefs.edit().remove(key).commit()
    }

    @After fun restoreState() {
        prefs.edit().apply { if (oldAccount == null) remove(key) else putString(key, oldAccount) }.commit()
    }

    private fun storeAccount(name: String, type: String = "com.google") {
        val parcel = android.os.Parcel.obtain()
        Account(name, type).writeToParcel(parcel, 0)
        val encoded = android.util.Base64.encodeToString(parcel.marshall(), android.util.Base64.DEFAULT)
        parcel.recycle()
        prefs.edit().putString(key, encoded).commit()
    }

    private fun storedAccountName(): String? = prefs.getString(key, null)?.let {
        val bytes = android.util.Base64.decode(it, android.util.Base64.DEFAULT)
        val p = android.os.Parcel.obtain()
        p.unmarshall(bytes, 0, bytes.size)
        p.setDataPosition(0)
        Account(p).name.also { p.recycle() }
    }

    private fun pendingIntent(): PendingIntent = PendingIntent.getActivity(
        RuntimeEnvironment.getApplication(), 0, Intent("test.google.authorize"), PendingIntent.FLAG_IMMUTABLE,
    )

    private fun granted(token: String, account: String? = null): () -> DriveAuthorization =
        { DriveAuthorization.Granted(token, account?.let { Account(it, "com.google") }) }

    private fun needsResolution(): () -> DriveAuthorization = { DriveAuthorization.NeedsResolution(pendingIntent()) }

    private fun apiError(code: Int): () -> DriveAuthorization = { throw ApiException(Status(code)) }

    private fun auth(fake: FakeAuthorizer, resultCode: Int = Activity.RESULT_OK) =
        AndroidGoogleAuth(fake) { _, intent -> launched += intent; ActivityResult(resultCode, Intent()) }

    /** Runs [block] with a resumed [ActivityBase] as the current activity (interactive sign-in). */
    private fun <T> withActivity(block: suspend () -> T): T {
        val controller = Robolectric.buildActivity(AuthActivity::class.java).setup()
        try {
            return runBlocking { block() }
        } finally {
            controller.pause().stop().destroy()
        }
    }

    private inline fun <reified E : Throwable> assertThrows(block: () -> Unit): E {
        try {
            block()
        } catch (e: Throwable) {
            if (e is E) return e
            throw AssertionError("expected ${E::class.simpleName}, got $e", e)
        }
        throw AssertionError("expected ${E::class.simpleName}")
    }

    // (a) granted result -> token, no UI
    @Test fun grantedTokenInBackgroundUsesStoredAccountWithoutUi() = runBlocking {
        storeAccount("stored@example.com")
        val fake = FakeAuthorizer(granted("tok-1"))
        val auth = auth(fake)
        assertEquals("tok-1", auth.accessToken())
        assertEquals(Account("stored@example.com", "com.google"), fake.requested.single())
        assertTrue(auth.signedIn)
        assertTrue(launched.isEmpty())
    }

    @Test fun signedInIsInMemoryOnly() = runBlocking {
        storeAccount("stored@example.com")
        val auth = auth(FakeAuthorizer(granted("tok")))
        assertFalse("a stored pref alone must not count as signed in (consent check must run)", auth.signedIn)
        auth.accessToken()
        assertTrue(auth.signedIn)
    }

    // (e) upgrade from the One Tap builds (Review Focus 1-2): their stored account had the package name as type
    @Test fun upgradeFromOneTapReusesStoredAccountWithoutPrompt() {
        storeAccount("old@example.com", type = "net.bible.android.activity")
        val fake = FakeAuthorizer(granted("tok", account = "old@example.com"))
        val auth = auth(fake)
        assertTrue(withActivity { auth.signIn() })
        assertEquals(Account("old@example.com", "com.google"), fake.requested.single())
        assertTrue("no chooser or consent sheet", launched.isEmpty())
        assertTrue(auth.signedIn)
        assertEquals("old@example.com", storedAccountName())
    }

    // Review Focus 6: several accounts on the device; the stored one is passed, so no chooser
    @Test fun upgradeWithStoredAccountAndGrantedResultWithoutAccountKeepsStoredAccount() {
        storeAccount("second@example.com")
        val fake = FakeAuthorizer(granted("tok", account = null))
        val auth = auth(fake)
        assertTrue(withActivity { auth.signIn() })
        assertEquals("second@example.com", fake.requested.single()?.name)
        assertTrue(launched.isEmpty())
        assertEquals("second@example.com", storedAccountName())
    }

    // (b) resolution needed while interactive -> launched, token from the intent result
    @Test fun firstSignInLaunchesResolutionAndStoresChosenAccount() {
        val fake = FakeAuthorizer(needsResolution(), fromIntent = granted("tok", account = "picked@example.com"))
        val auth = auth(fake)
        assertTrue(withActivity { auth.signIn() })
        assertEquals(listOf<Account?>(null), fake.requested)
        assertEquals(1, launched.size)
        assertEquals("picked@example.com", storedAccountName())
        assertTrue(auth.signedIn)
    }

    // Review Focus 6: the stored account was removed from the device -> one chooser is acceptable
    @Test fun storedAccountGoneFromDeviceFallsBackToOneChooser() {
        storeAccount("removed@example.com")
        val fake = FakeAuthorizer(
            apiError(CommonStatusCodes.INVALID_ACCOUNT),
            needsResolution(),
            fromIntent = granted("tok", account = "other@example.com"),
        )
        val auth = auth(fake)
        assertTrue(withActivity { auth.signIn() })
        assertEquals(listOf("removed@example.com", null), fake.requested.map { it?.name })
        assertEquals(1, launched.size)
        assertEquals("other@example.com", storedAccountName())
    }

    // (c) resolution needed non-interactively -> sign in again
    @Test fun resolutionNeededInBackgroundRequiresSignIn() = runBlocking {
        storeAccount("stored@example.com")
        val auth = auth(FakeAuthorizer(needsResolution()))
        assertThrows<GoogleAuthRequiredException> { auth.accessToken() }
        assertTrue("background never shows UI", launched.isEmpty())
        Unit
    }

    @Test fun signInRequiredStatusInBackgroundRequiresSignIn() = runBlocking {
        storeAccount("stored@example.com")
        val auth = auth(FakeAuthorizer(apiError(CommonStatusCodes.SIGN_IN_REQUIRED)))
        assertThrows<GoogleAuthRequiredException> { auth.accessToken() }
        Unit
    }

    @Test fun accessTokenWithoutAccountRequiresSignInWithoutAskingPlayServices() = runBlocking {
        val fake = FakeAuthorizer()
        val auth = auth(fake)
        assertThrows<GoogleAuthRequiredException> { auth.accessToken() }
        assertTrue(fake.requested.isEmpty())
        assertFalse(auth.signedIn)
    }

    // (d) user cancels the resolution
    @Test fun cancelledResolutionClearsAccount() {
        storeAccount("stored@example.com")
        val auth = auth(FakeAuthorizer(needsResolution()), resultCode = Activity.RESULT_CANCELED)
        val e = withActivity { runCatching { auth.signIn() }.exceptionOrNull() }
        assertTrue("got $e", e is CloudSyncUserCancelledException)
        assertFalse(auth.signedIn)
        assertNull(prefs.getString(key, null))
    }

    @Test fun cancelledStatusFromResolutionResultIsCancel() {
        val auth = auth(FakeAuthorizer(needsResolution(), fromIntent = apiError(CommonStatusCodes.CANCELED)))
        val e = withActivity { runCatching { auth.signIn() }.exceptionOrNull() }
        assertTrue("got $e", e is CloudSyncUserCancelledException)
        assertFalse(auth.signedIn)
    }

    // (f) transient Play services / network failures stay retryable I/O (phase-2 fix, Review Focus 4)
    @Test fun transientFailuresInBackgroundAreIOExceptions() = runBlocking {
        for (code in listOf(
            CommonStatusCodes.NETWORK_ERROR,
            CommonStatusCodes.SERVICE_VERSION_UPDATE_REQUIRED,
            CommonStatusCodes.SERVICE_DISABLED,
            CommonStatusCodes.API_NOT_CONNECTED,
            CommonStatusCodes.TIMEOUT,
            CommonStatusCodes.INTERNAL_ERROR,
        )) {
            storeAccount("stored@example.com")
            val auth = auth(FakeAuthorizer(apiError(code)))
            val e = runCatching { auth.accessToken() }.exceptionOrNull()
            assertFalse("status $code must not drop the sign-in: $e", e is GoogleAuthRequiredException)
            assertTrue("status $code: got $e", e is IOException)
            assertNotNull("account kept", prefs.getString(key, null))
        }
    }

    @Test fun transientFailureDuringSignInKeepsStoredAccount() {
        storeAccount("offline@example.com")
        val auth = auth(FakeAuthorizer(apiError(CommonStatusCodes.NETWORK_ERROR)))
        assertTrue(withActivity { auth.signIn() })
        assertTrue(auth.signedIn)
        assertEquals("offline@example.com", storedAccountName())
    }

    @Test fun transientFailureOnFirstSignInIsIOException() {
        val auth = auth(FakeAuthorizer(apiError(CommonStatusCodes.NETWORK_ERROR)))
        val e = withActivity { runCatching { auth.signIn() }.exceptionOrNull() }
        assertTrue("got $e", e is IOException)
        assertFalse(auth.signedIn)
    }

    @Test fun signInWithoutActivityReturnsFalse() = runBlocking {
        storeAccount("stored@example.com")
        val fake = FakeAuthorizer()
        assertFalse(auth(fake).signIn())
        assertNotNull("pref untouched", prefs.getString(key, null))
        assertTrue(fake.requested.isEmpty())
    }

    @Test fun invalidateClearsThatToken() = runBlocking {
        val fake = FakeAuthorizer()
        auth(fake).invalidate("old-token")
        assertEquals(listOf("old-token"), fake.cleared)
    }

    @Test fun signOutForgetsAccount() = runBlocking {
        storeAccount("stored@example.com")
        val auth = auth(FakeAuthorizer(granted("tok")))
        auth.accessToken()
        auth.signOut()
        assertFalse(auth.signedIn)
        assertNull(prefs.getString(key, null))
        assertThrows<GoogleAuthRequiredException> { auth.accessToken() }
        Unit
    }

    @Test fun signOutDuringTokenRequestStaysSignedOut() = runBlocking {
        storeAccount("stored@example.com")
        lateinit var auth: AndroidGoogleAuth
        val fake = FakeAuthorizer({
            runBlocking { auth.signOut() } // the user signs out while Play services is answering
            DriveAuthorization.Granted("tok", Account("stored@example.com", "com.google"))
        })
        auth = auth(fake)
        assertEquals("tok", auth.accessToken())
        assertFalse("an in-flight token request must not sign the user back in", auth.signedIn)
        assertNull(prefs.getString(key, null))
    }

    @Test fun permanentFailuresInBackgroundRequireSignIn() = runBlocking {
        for (code in listOf(CommonStatusCodes.DEVELOPER_ERROR, 12500)) {
            storeAccount("stored@example.com")
            val auth = auth(FakeAuthorizer(apiError(code)))
            assertThrows<GoogleAuthRequiredException> { auth.accessToken() }
        }
    }

    @Test fun nonApiFailureInBackgroundIsIOException() = runBlocking {
        storeAccount("stored@example.com")
        val auth = auth(FakeAuthorizer({ throw SecurityException("Play services disabled for this app") }))
        val e = runCatching { auth.accessToken() }.exceptionOrNull()
        assertTrue("got $e", e is IOException && e !is GoogleAuthRequiredException)
    }

    @Test fun grantWithoutAccountOnFirstSignInKeepsSessionSignedIn() {
        val fake = FakeAuthorizer(granted("tok-1", account = null), granted("tok-2", account = null))
        val auth = auth(fake)
        assertTrue(withActivity { auth.signIn() })
        assertTrue(auth.signedIn)
        assertEquals("tok-2", runBlocking { auth.accessToken() })
        assertEquals(listOf<Account?>(null, null), fake.requested)
    }
}

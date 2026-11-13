package net.bible.service.cloudsync.googledrive

import android.accounts.Account
import android.app.Activity
import android.content.Intent
import android.os.Looper
import com.google.android.gms.auth.GoogleAuthException
import com.google.android.gms.auth.GooglePlayServicesAvailabilityException
import com.google.android.gms.auth.UserRecoverableAuthException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
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
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.IOException
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class AndroidGoogleAuthTest {
    class AuthActivity : ActivityBase() {
        override val doNotInitializeApp = true
        override val allowThemeChange = false
        override val disableBaseSetupUi = true
        fun answer(requestCode: Int, resultCode: Int) = onActivityResult(requestCode, resultCode, null)
    }

    private val key = "cloud_sync_last_account"
    private val prefs get() = CommonUtils.realSharedPreferences
    private var oldAccount: String? = null

    @Before fun saveState() {
        oldAccount = prefs.getString(key, null)
        prefs.edit().remove(key).commit()
    }

    @After fun restoreState() {
        prefs.edit().apply { if (oldAccount == null) remove(key) else putString(key, oldAccount) }.commit()
    }

    private fun storeAccount(name: String) {
        val parcel = android.os.Parcel.obtain()
        Account(name, "com.google").writeToParcel(parcel, 0)
        val encoded = android.util.Base64.encodeToString(parcel.marshall(), android.util.Base64.DEFAULT)
        parcel.recycle()
        prefs.edit().putString(key, encoded).commit()
    }

    private fun recoverable() = UserRecoverableAuthException("permission", Intent("test.google.permission"))

    @Test fun accessTokenUsesStoredAccountWithoutActivity() = runBlocking {
        storeAccount("stored")
        var seen: Account? = null
        val auth = AndroidGoogleAuth(tokenSource = { seen = it; "tok-1" }, clearToken = {})
        assertEquals("tok-1", auth.accessToken())
        assertEquals("stored", seen?.name)
        assertTrue(auth.signedIn)
    }

    @Test fun signedInIsInMemoryOnlyLikeTheOldAdapter() = runBlocking {
        storeAccount("stored")
        val auth = AndroidGoogleAuth(tokenSource = { "tok" }, clearToken = {})
        assertFalse("a stored pref alone must not count as signed in (consent check must run)", auth.signedIn)
        auth.accessToken()
        assertTrue(auth.signedIn)
    }

    @Test fun signInWithoutActivityReturnsFalse() = runBlocking {
        storeAccount("stored")
        val auth = AndroidGoogleAuth(tokenSource = { "tok" }, clearToken = {})
        assertFalse(auth.signIn())
        assertNotNull("pref untouched", prefs.getString(key, null))
    }

    @Test fun otherThrowableDuringSignInReturnsFalseAndClearsAccount() {
        val controller = Robolectric.buildActivity(AuthActivity::class.java).setup()
        storeAccount("broken")
        val auth = AndroidGoogleAuth(tokenSource = { throw IllegalStateException("boom") }, clearToken = {})
        try {
            assertFalse(runBlocking { auth.signIn() })
            assertFalse(auth.signedIn)
            assertNull(prefs.getString(key, null))
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test fun accessTokenWithoutAccountRequiresSignIn() = runBlocking {
        val auth = AndroidGoogleAuth(tokenSource = { "x" }, clearToken = {})
        try {
            auth.accessToken()
            fail("expected GoogleAuthRequiredException")
        } catch (_: GoogleAuthRequiredException) {
        }
        assertFalse(auth.signedIn)
    }

    @Test fun recoverableErrorInBackgroundRequiresSignIn() = runBlocking {
        storeAccount("stored")
        val auth = AndroidGoogleAuth(tokenSource = { throw recoverable() }, clearToken = {})
        try {
            auth.accessToken()
            fail("expected GoogleAuthRequiredException")
        } catch (_: GoogleAuthRequiredException) {
        }
    }

    @Test fun otherAuthExceptionInBackgroundRequiresSignIn() = runBlocking {
        storeAccount("stored")
        val auth = AndroidGoogleAuth(tokenSource = { throw GoogleAuthException("account removed") }, clearToken = {})
        try {
            auth.accessToken()
            fail("expected GoogleAuthRequiredException")
        } catch (e: GoogleAuthRequiredException) {
            // coroutine stack-trace recovery may re-wrap the cause, so check the message, not the cause chain
            assertTrue(e.message!!.contains("account removed"))
        }
    }

    @Test fun playServicesUnavailableInBackgroundIsTransientIOException() = runBlocking {
        storeAccount("stored")
        val transient = org.mockito.Mockito.mock(GooglePlayServicesAvailabilityException::class.java) // ctor is package-private
        val auth = AndroidGoogleAuth(tokenSource = { throw transient }, clearToken = {})
        try {
            auth.accessToken()
            fail("expected IOException")
        } catch (e: GoogleAuthRequiredException) {
            fail("a transient Play Services problem must not drop the adapter: $e")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("Play Services"))
        }
    }

    @Test fun ioErrorInBackgroundPropagatesUnchanged() = runBlocking {
        storeAccount("stored")
        val io = IOException("offline")
        val auth = AndroidGoogleAuth(tokenSource = { throw io }, clearToken = {})
        try {
            auth.accessToken()
            fail("expected IOException")
        } catch (e: IOException) {
            // coroutine stack-trace recovery may copy the instance: check type and message, not identity
            assertEquals(IOException::class.java, e.javaClass)
            assertEquals("offline", e.message)
        }
    }

    @Test fun invalidateClearsThatToken() = runBlocking {
        val cleared = mutableListOf<String>()
        val auth = AndroidGoogleAuth(tokenSource = { "x" }, clearToken = { cleared += it })
        auth.invalidate("old-token")
        assertEquals(listOf("old-token"), cleared)
    }

    @Test fun cancelledConsentClearsAccount() {
        val controller = Robolectric.buildActivity(AuthActivity::class.java).setup()
        val activity = controller.get()
        storeAccount("cancel-test")
        val auth = AndroidGoogleAuth(tokenSource = { throw recoverable() }, clearToken = {})
        try {
            runBlocking {
                val result = CoroutineScope(Dispatchers.Default).async {
                    try { auth.signIn(); null } catch (e: CloudSyncUserCancelledException) { e }
                }
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
                var started: org.robolectric.shadows.ShadowActivity.IntentForResult? = null
                while (started == null && System.nanoTime() < deadline) {
                    shadowOf(Looper.getMainLooper()).idle()
                    started = shadowOf(activity).nextStartedActivityForResult
                    if (started == null) Thread.sleep(10)
                }
                assertNotNull("consent intent must be launched", started)
                activity.answer(started!!.requestCode, Activity.RESULT_CANCELED)
                val done = CompletableDeferred<Unit>()
                val waiter = CoroutineScope(Dispatchers.Default).async { result.await().also { done.complete(Unit) } }
                val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
                while (!done.isCompleted && System.nanoTime() < end) {
                    shadowOf(Looper.getMainLooper()).idle()
                    Thread.sleep(10)
                }
                assertNotNull("cancel must surface as CloudSyncUserCancelledException", waiter.await())
            }
            assertFalse(auth.signedIn)
            assertNull(prefs.getString(key, null))
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test fun networkErrorDuringSignInKeepsAccount() {
        val controller = Robolectric.buildActivity(AuthActivity::class.java).setup()
        storeAccount("offline")
        val auth = AndroidGoogleAuth(tokenSource = { throw IOException("offline") }, clearToken = {})
        try {
            assertTrue(runBlocking { auth.signIn() })
            assertTrue(auth.signedIn)
            assertNotNull(prefs.getString(key, null))
        } finally {
            controller.pause().stop().destroy()
        }
    }
}

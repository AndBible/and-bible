package net.bible.service.cloudsync.drivebrowser

import android.net.Uri
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.service.cloudsync.CloudSyncUserCancelledException
import net.bible.service.cloudsync.drive.oauth.GoogleOAuthClients
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class AppLinkOAuthBrowserTest {
    private val redirect = GoogleOAuthClients.ANDROID_WEB_REDIRECT_URI
    private val opened = mutableListOf<String>()
    private fun browser(onOpen: (String) -> Unit = {}) = AppLinkOAuthBrowser(
        openUrl = { opened += it; onOpen(it) },
        timeoutMs = 60_000,
        closedGraceMs = 3_000,
    )
    private fun buildUrl(uri: String) = "https://accounts.google.com/o/oauth2/v2/auth?redirect_uri=${Uri.encode(uri)}"

    @After fun noPendingLeft() {
        assertFalse("an attempt leaked into the next test", OAuthRedirectInbox.hasPending)
    }

    private fun TestScope.start(b: AppLinkOAuthBrowser = browser()) = async { runCatching { b.authorize(::buildUrl) } }

    private fun assertCancelled(result: Result<String>) {
        assertTrue("expected cancel, got $result", result.exceptionOrNull() is CloudSyncUserCancelledException)
    }

    @Test fun deliveredRedirectCompletesAuthorize() = runTest {
        val r = start()
        runCurrent()
        assertTrue(opened.single().contains(Uri.encode(redirect)))
        assertTrue(OAuthRedirectInbox.deliver("$redirect?code=c&state=s"))
        assertEquals("$redirect?code=c&state=s", r.await().getOrThrow())
    }

    @Test fun deliverWithoutPendingAttemptReturnsFalse() {
        assertFalse(OAuthRedirectInbox.deliver("$redirect?code=c&state=s"))
    }

    @Test fun errorRedirectIsDeliveredToo() = runTest {
        val r = start()
        runCurrent()
        assertTrue(OAuthRedirectInbox.deliver("$redirect?error=access_denied&state=s"))
        assertEquals("$redirect?error=access_denied&state=s", r.await().getOrThrow())
    }

    @Test fun timeoutCancels() = runTest {
        val r = start()
        runCurrent()
        advanceTimeBy(60_001)
        assertCancelled(r.await())
        assertFalse(OAuthRedirectInbox.hasPending)
        assertFalse(OAuthRedirectInbox.deliver("$redirect?code=late&state=s"))
    }

    @Test fun secondAuthorizeSupersedesFirst() = runTest {
        val first = start()
        runCurrent()
        val second = start()
        runCurrent()
        assertCancelled(first.await())
        assertTrue(OAuthRedirectInbox.deliver("$redirect?code=2&state=s"))
        assertEquals("$redirect?code=2&state=s", second.await().getOrThrow())
    }

    @Test fun coroutineCancellationClearsAttempt() = runTest {
        val job = launch { browser().authorize(::buildUrl) }
        runCurrent()
        assertTrue(OAuthRedirectInbox.hasPending)
        job.cancel()
        runCurrent()
        assertFalse(OAuthRedirectInbox.hasPending)
    }

    @Test fun hostResumedWithoutRedirectCancelsAfterGrace() = runTest {
        val r = start()
        runCurrent()
        OAuthRedirectInbox.hostResumed()
        advanceTimeBy(2_999)
        runCurrent()
        assertFalse(r.isCompleted)
        advanceTimeBy(2)
        assertCancelled(r.await())
        assertTrue("well before the 60 s timeout", currentTime < 10_000)
    }

    @Test fun redirectWithinGraceWins() = runTest {
        val r = start()
        runCurrent()
        OAuthRedirectInbox.hostResumed()
        advanceTimeBy(1_000)
        assertTrue(OAuthRedirectInbox.deliver("$redirect?code=c&state=s"))
        assertEquals("$redirect?code=c&state=s", r.await().getOrThrow())
    }

    @Test fun hostResumedBeforeBrowserOpenedIsIgnored() = runTest {
        // The resume caused by (or racing with) the open itself must not arm the grace timer.
        val r = start(browser(onOpen = { OAuthRedirectInbox.hostResumed() }))
        runCurrent()
        advanceTimeBy(30_000)
        assertFalse(r.isCompleted)
        assertTrue(OAuthRedirectInbox.deliver("$redirect?code=c&state=s"))
        assertEquals("$redirect?code=c&state=s", r.await().getOrThrow())
    }

    @Test fun isOAuthRedirect() {
        assertTrue(OAuthRedirectInbox.isOAuthRedirect(Uri.parse("https://read.andbible.org/oauth2/drive?code=x")))
        assertTrue(OAuthRedirectInbox.isOAuthRedirect(Uri.parse("https://read.andbible.org/oauth2/drive?error=access_denied")))
        assertFalse(OAuthRedirectInbox.isOAuthRedirect(Uri.parse("https://read.andbible.org/oauth2/driveX?code=x")))
        assertFalse(OAuthRedirectInbox.isOAuthRedirect(Uri.parse("https://read.andbible.org/oauth2")))
        assertFalse(OAuthRedirectInbox.isOAuthRedirect(Uri.parse("https://read.andbible.org/John.3.16")))
        assertFalse(OAuthRedirectInbox.isOAuthRedirect(Uri.parse("https://evil.example/oauth2/drive?code=x")))
        assertFalse(OAuthRedirectInbox.isOAuthRedirect(Uri.parse("http://read.andbible.org/oauth2/drive?code=x")))
        assertFalse(OAuthRedirectInbox.isOAuthRedirect(null))
    }
}

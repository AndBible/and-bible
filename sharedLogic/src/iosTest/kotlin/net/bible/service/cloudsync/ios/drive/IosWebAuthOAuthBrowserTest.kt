package net.bible.service.cloudsync.ios.drive

import kotlinx.coroutines.test.runTest
import net.bible.service.cloudsync.CloudSyncUserCancelledException
import net.bible.service.cloudsync.drive.oauth.GoogleOAuthClient
import okio.IOException
import platform.AuthenticationServices.ASWebAuthenticationSessionErrorCodeCanceledLogin
import platform.AuthenticationServices.ASWebAuthenticationSessionErrorDomain
import platform.Foundation.NSError
import platform.Foundation.NSURL
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class IosWebAuthOAuthBrowserTest {
    private val client = GoogleOAuthClient("123-abc.apps.googleusercontent.com")

    private fun browser(
        onStart: (NSURL, String, (NSURL?, NSError?) -> Unit) -> Unit,
    ) = IosWebAuthOAuthBrowser(client) { url, scheme, completion ->
        onStart(url, scheme, completion)
        val noCancel: () -> Unit = {}
        noCancel
    }

    @Test fun redirectUriIsReversedClientId() = runTest {
        var built: String? = null
        var scheme: String? = null
        val b = browser { _, s, completion ->
            scheme = s
            completion(NSURL.URLWithString("com.googleusercontent.apps.123-abc:/oauth2redirect?code=x"), null)
        }
        b.authorize { redirect -> built = redirect; "https://accounts.example/auth?r=$redirect" }
        assertEquals("com.googleusercontent.apps.123-abc:/oauth2redirect", built)
        assertEquals("com.googleusercontent.apps.123-abc", scheme)
    }

    @Test fun callbackUrlReturned() = runTest {
        val callback = "com.googleusercontent.apps.123-abc:/oauth2redirect?code=abc&state=s"
        var opened: String? = null
        val b = browser { url, _, completion ->
            opened = url.absoluteString
            completion(NSURL.URLWithString(callback), null)
        }
        assertEquals(callback, b.authorize { "https://accounts.example/auth" })
        assertEquals("https://accounts.example/auth", opened)
    }

    @Test fun cancelErrorMapsToCancelled() = runTest {
        val b = browser { _, _, completion ->
            completion(
                null,
                NSError.errorWithDomain(
                    ASWebAuthenticationSessionErrorDomain, ASWebAuthenticationSessionErrorCodeCanceledLogin, null,
                ),
            )
        }
        assertFailsWith<CloudSyncUserCancelledException> { b.authorize { "https://accounts.example/auth" } }
    }

    @Test fun nonCancelErrorFails() = runTest {
        val b = browser { _, _, completion ->
            completion(null, NSError.errorWithDomain("some.domain", 7, null))
        }
        val e = assertFailsWith<IOException> { b.authorize { "https://accounts.example/auth" } }
        assertTrue(e.message!!.contains("some.domain"))
    }

    @Test fun nilUrlAndNilErrorFails() = runTest {
        val b = browser { _, _, completion -> completion(null, null) }
        assertFailsWith<IOException> { b.authorize { "https://accounts.example/auth" } }
    }
}

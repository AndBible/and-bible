package net.bible.android.platform

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.service.cloudsync.CloudAdapter
import net.bible.service.cloudsync.drive.GoogleAuth
import net.bible.service.cloudsync.drive.GoogleDriveRestAdapter
import net.bible.service.cloudsync.drive.oauth.GoogleDriveSecretKeys
import net.bible.service.cloudsync.drive.oauth.PkceGoogleAuth
import net.bible.service.cloudsync.drivebrowser.BrowserDriveAdapterProvider
import net.bible.service.cloudsync.drivebrowser.FakeGoogle
import net.bible.service.cloudsync.drivebrowser.MapSecretStore
import net.bible.service.cloudsync.drivebrowser.OAuthRedirectInbox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Reads the private auth of a [GoogleDriveRestAdapter]. */
internal fun CloudAdapter.driveAuth(): GoogleAuth {
    val field = GoogleDriveRestAdapter::class.java.getDeclaredField("auth").apply { isAccessible = true }
    return field.get(this as GoogleDriveRestAdapter) as GoogleAuth
}

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class AndroidCloudAdapterFactoryDriveSignInTest {
    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val google = FakeGoogle()
    private val secrets = MapSecretStore()
    private var playLoads = 0
    private val browserProvider = { BrowserDriveAdapterProvider(secrets, google.http, google.browser) }

    private fun factory(signIn: AndroidDriveSignIn) = AndroidCloudAdapterFactory(
        context,
        driveSignIn = signIn,
        loadPlayProvider = {
            playLoads++
            object : DriveAdapterProvider {
                override fun create(): CloudAdapter = BrowserDriveAdapterProvider(secrets, google.http, { fail("opened") }).createForBackground()
            }
        },
        browserProvider = browserProvider,
    )

    @Test fun fdroidBrowserOffersDriveAndBuildsRestAdapterWithPkce() {
        val f = factory(AndroidDriveSignIn.BROWSER)
        assertTrue("GOOGLE_DRIVE" in f.available)
        assertTrue(f.create("GOOGLE_DRIVE").driveAuth() is PkceGoogleAuth)
        assertTrue(f.createForBackground("GOOGLE_DRIVE").driveAuth() is PkceGoogleAuth)
    }

    @Test fun fdroidPathNeverLoadsPlayClasses() {
        val f = factory(AndroidDriveSignIn.BROWSER)
        f.create("GOOGLE_DRIVE")
        f.createForBackground("GOOGLE_DRIVE")
        assertEquals(0, playLoads)
    }

    @Test fun discreteFdroidDoesNotOfferDrive() {
        val f = factory(AndroidDriveSignIn.NONE)
        assertFalse("GOOGLE_DRIVE" in f.available)
        assertEquals(listOf("NEXT_CLOUD", "WEBDAV"), f.available)
        assertTrue(runCatching { f.create("GOOGLE_DRIVE") }.isFailure)
        assertEquals(0, playLoads)
    }

    @Test fun playServicesPathUnchanged() {
        val f = factory(AndroidDriveSignIn.PLAY_PROVIDER)
        assertEquals(listOf("GOOGLE_DRIVE", "NEXT_CLOUD", "WEBDAV"), f.available)
        f.create("GOOGLE_DRIVE")
        f.createForBackground("GOOGLE_DRIVE")
        assertEquals(2, playLoads)
    }

    @Test fun currentSignInOfThisTestBuildIsPlayProvider() {
        // The unit suite runs the standardGoogleplay variant.
        assertEquals(AndroidDriveSignIn.PLAY_PROVIDER, AndroidDriveSignIn.current())
    }

    @Test fun browserBackgroundAdapterNeverOpensBrowser() = runBlocking {
        var opened = 0
        val adapter = BrowserDriveAdapterProvider(secrets, google.http, { opened++ }).createForBackground()
        assertFalse(adapter.signIn())
        assertEquals(0, opened)
        assertFalse(OAuthRedirectInbox.hasPending)
    }

    @Test fun browserSignInStoresRefreshToken() = runBlocking {
        val adapter = browserProvider().create()
        assertFalse(adapter.signedIn)
        assertTrue(adapter.signIn())
        assertTrue(adapter.signedIn)
        assertEquals("refresh-1", secrets.get(GoogleDriveSecretKeys.REFRESH_TOKEN))
        val exchange = google.tokenForms.single()
        assertEquals("authorization_code", exchange["grant_type"])
        assertEquals("https://read.andbible.org/oauth2/drive", exchange["redirect_uri"])
        assertTrue(exchange["client_id"]!!.startsWith("533479479097-d64q"))
        assertTrue(!exchange["client_secret"].isNullOrBlank())
        // Signed in: Drive answers with the token just issued.
        adapter.createNewFolder("sync-folder", null)
        Unit
    }
}

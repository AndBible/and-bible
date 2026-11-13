package net.bible.service.cloudsync.googledrive

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.runBlocking
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.BibleApplication
import net.bible.android.activity.R
import net.bible.service.cloudsync.CloudSyncUserFacingException
import net.bible.service.cloudsync.drive.GoogleAuth
import net.bible.service.cloudsync.drive.GoogleAuthRequiredException
import com.google.android.gms.common.ConnectionResult
import net.bible.android.platform.driveAuth
import net.bible.service.cloudsync.drive.oauth.GoogleDriveSecretKeys
import net.bible.service.cloudsync.drive.oauth.PkceGoogleAuth
import net.bible.service.cloudsync.drivebrowser.BrowserDriveAdapterProvider
import net.bible.service.cloudsync.drivebrowser.FakeGoogle
import net.bible.service.cloudsync.drivebrowser.MapSecretStore
import net.bible.service.common.CommonUtils
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
class GoogleDriveAdapterProviderTest {
    private val lostAuth = object : GoogleAuth {
        override val signedIn = false
        override suspend fun signIn() = false
        override suspend fun accessToken(): String = throw GoogleAuthRequiredException("gone")
        override suspend fun invalidate(token: String) {}
        override suspend fun signOut() {}
    }

    @Test fun restAdapterUsesLocalizedSignInRequiredMessage() = runBlocking {
        val adapter = GoogleDriveAdapterProvider().createRest(lostAuth, HttpClient(MockEngine { error("no http") }))
        try {
            adapter.get("some-id")
            fail("expected CloudSyncUserFacingException")
        } catch (e: CloudSyncUserFacingException) {
            assertTrue(e.requiresReconnect)
            assertEquals(BibleApplication.application.getString(R.string.google_drive_signin_required), e.message)
        }
    }

    private val google = FakeGoogle()
    private val secrets = MapSecretStore()

    private fun provider(playStatus: Int) = GoogleDriveAdapterProvider(
        playServicesStatus = { playStatus },
        secrets = { secrets },
        browser = { BrowserDriveAdapterProvider(secrets, google.http, google.browser) },
        playAuth = { lostAuth },
    )

    @Test fun playProviderUsesBrowserWhenPlayServicesMissing() {
        val missing = provider(ConnectionResult.SERVICE_MISSING)
        assertTrue(missing.create().driveAuth() is PkceGoogleAuth)
        assertTrue(missing.createForBackground().driveAuth() is PkceGoogleAuth)
        val disabled = provider(ConnectionResult.SERVICE_DISABLED)
        assertTrue(disabled.create().driveAuth() is PkceGoogleAuth)

        val play = provider(ConnectionResult.SUCCESS)
        assertTrue(play.create().driveAuth() === lostAuth)
        assertTrue(play.createForBackground().driveAuth() === lostAuth)

        // Sticky: a stored browser sign-in keeps the browser path even with Play services back.
        secrets.put(GoogleDriveSecretKeys.REFRESH_TOKEN, "refresh-1")
        assertTrue(play.create().driveAuth() is PkceGoogleAuth)
        assertTrue(play.createForBackground().driveAuth() is PkceGoogleAuth)
    }

    @Test fun playToBrowserTransitionAsksSignInOnce() = runBlocking {
        val prefs = CommonUtils.realSharedPreferences
        val oldAccount = prefs.getString("cloud_sync_last_account", null)
        try {
            // A Play user's sync folder already exists on Drive (same Cloud project, same appDataFolder).
            val folderId = google.drive.putFile("sync-folder", "appDataFolder", ByteArray(0), 1_000, "application/vnd.google-apps.folder")
            prefs.edit().putString("cloud_sync_last_account", "user@example.com").commit()
            val p = provider(ConnectionResult.SERVICE_MISSING)

            // Background sync: no browser token yet -> "sign in again", once, without opening anything.
            val background = p.createForBackground()
            try {
                background.listFiles(null, "sync-folder", null, null)
                fail("expected a reconnect request")
            } catch (e: CloudSyncUserFacingException) {
                assertTrue(e.requiresReconnect)
            }
            assertTrue(google.consentUrls.isEmpty())

            // The user signs in once in the browser and finds the same folder.
            val interactive = p.create()
            assertTrue(interactive.signIn())
            assertEquals(1, google.consentUrls.size)
            assertEquals(listOf(folderId), interactive.listFiles(null, "sync-folder", null, null).map { it.id })

            // The next background sync is silent.
            val next = p.createForBackground()
            assertTrue(next.signIn())
            assertEquals(listOf(folderId), next.listFiles(null, "sync-folder", null, null).map { it.id })
            assertEquals(1, google.consentUrls.size)
            assertFalse(google.tokenForms.isEmpty())
        } finally {
            prefs.edit().apply { if (oldAccount == null) remove("cloud_sync_last_account") else putString("cloud_sync_last_account", oldAccount) }.commit()
        }
    }
}

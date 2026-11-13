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
import org.junit.Assert.assertEquals
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
}

package net.bible.service.cloudsync.nextcloud

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import net.bible.android.TestBibleApplication
import net.bible.android.platform.AndroidCloudAdapterFactory
import net.bible.service.cloudsync.CloudSyncUserFacingException
import net.bible.service.common.CommonUtils
import net.bible.test.DatabaseResetter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A saved plain-http Nextcloud address must never reach the network; the user is told to use https. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class)
class NextCloudHttpsOnlyTest {
    @After fun reset() { DatabaseResetter.resetDatabase() }

    private fun signInWithSaved(url: String): Throwable? {
        CommonUtils.realSharedPreferences.edit()
            .putString("cloud_sync_server_url", url)
            .putString("cloud_sync_username", "me")
            .putString("cloud_sync_password", "pw")
            .commit()
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val adapter = AndroidCloudAdapterFactory(context).create("NEXT_CLOUD")
        return runCatching { runBlocking { adapter.signIn() } }.exceptionOrNull()
    }

    @Test fun savedHttpAddressRefusedWithHttpsMessage_publicAndLocal() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        listOf("http://example.org/nc", "HTTP://NAS.local:8080", "http://127.0.0.1:1234").forEach { url ->
            val failure = signInWithSaved(url)
            assertTrue("$url: $failure", failure is CloudSyncUserFacingException)
            assertTrue((failure as CloudSyncUserFacingException).requiresReconnect)
            assertEquals(context.getString(net.bible.android.activity.R.string.webdav_https_required), failure.message)
        }
    }

    @Test fun savedAddressIsKeptWhenRefused() {
        signInWithSaved("http://example.org/nc")
        assertEquals("http://example.org/nc", CommonUtils.realSharedPreferences.getString("cloud_sync_server_url", null))
    }

    @Test fun httpsAddressIsNotRefusedByTheScheme() {
        // Without an activity sign-in returns false quietly; the point is that no https-required refusal is thrown.
        assertFalse(signInWithSaved("https://cloud.example.org") is CloudSyncUserFacingException)
    }
}

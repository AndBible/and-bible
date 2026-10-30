package net.bible.service.cloudsync.nextcloud

import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import net.bible.android.TestBibleApplication
import net.bible.android.platform.AndroidCloudAdapterFactory
import net.bible.android.view.activity.base.ActivityBase
import net.bible.service.common.CommonUtils
import net.bible.test.DatabaseResetter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class)
@LooperMode(LooperMode.Mode.LEGACY)
class NextCloudServerUrlTest {
    class SignInActivity : ActivityBase()

    @After fun reset() { DatabaseResetter.resetDatabase() }

    @Test fun alreadyStoredTrailingSlashSignsInAgainstCanonicalDavResponse() {
        val host = Robolectric.buildActivity(SignInActivity::class.java).setup()
        try {
            MockWebServer().use { server ->
                server.start()
                server.enqueue(MockResponse.Builder().code(207).body("""
                    <d:multistatus xmlns:d="DAV:">
                      <d:response>
                        <d:href>/remote.php/dav/files/me/</d:href>
                        <d:propstat><d:prop>
                          <d:resourcetype><d:collection/></d:resourcetype>
                          <d:getcontenttype>httpd/unix-directory</d:getcontenttype>
                        </d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat>
                      </d:response>
                    </d:multistatus>
                """.trimIndent()).build())
                // Bypass the settings screen, as an installation with old saved credentials does.
                CommonUtils.realSharedPreferences.edit()
                    .putString("cloud_sync_server_url", server.url("/").toString())
                    .putString("cloud_sync_username", "me")
                    .putString("cloud_sync_password", "pw")
                    .commit()
                val adapter = AndroidCloudAdapterFactory(host.get()).create("NEXT_CLOUD")
                assertTrue(runBlocking { adapter.signIn() } == true)
                assertEquals("/remote.php/dav/files/me/", server.takeRequest(5, TimeUnit.SECONDS)!!.target)
            }
        } finally {
            host.pause().stop().destroy()
        }
    }
}

package net.bible.service.cloudsync.webdav

import androidx.test.core.app.ApplicationProvider
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.mock
import kotlinx.coroutines.runBlocking
import net.bible.android.BibleApplication
import net.bible.android.TEST_SDK
import net.bible.android.view.activity.base.ActivityBase
import net.bible.service.cloudsync.CloudSyncUserFacingException
import net.bible.sharedcore.webdav.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [TEST_SDK])
class WebDavSignInTest {
    private val server = FakeDavServer(nowServer = 1_700_000_000_000L)
    private val state = object : WebDavStateStore { override var propagation = Propagation.UNKNOWN; override var certPin: CertPin? = null }
    private val ui = object : WebDavSignInUi {
        var trust = true; var asked = 0
        override suspend fun confirmCertificate(info: DavCertificateInfo): Boolean { asked++; return trust }
        override fun message(kind: WebDavMessage) = kind.name
    }
    private val info = DavCertificateInfo("h", "ABCDEF", "CN=h", "CN=ca", 0L, 1L)

    private val appField = BibleApplication::class.java.getDeclaredField("application").apply { isAccessible = true }
    private val realApp: Any? = appField.get(null)
    @Before fun installAppStandIn() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        appField.set(null, mock<BibleApplication> {
            on { applicationContext } doAnswer { ctx }
            on { contentResolver } doAnswer { ctx.contentResolver }
        })
    }
    @After fun restoreApp() { appField.set(null, realApp) }

    private fun adapter() = WebDavCloudAdapter(
        WebDavConfig("https://h/dav/", "me", "pw", "AndBible"), state, ui,
        clientFactory = { c, _ -> WebDavClient(createWebDavHttpClient(server.engine, c.username, c.password), c.serverUrl, c.username, c.password, { server.nowServer }) },
        nowMs = { server.nowServer },
    )

    private fun untrustedUntilPinned() {
        server.failWith = { if (state.certPin == null) IOException("handshake", DavUntrustedCertificateException(info)) else null }
    }

    @Test fun untrustedCertificate_trusted_pinsAndConnects() = runBlocking {
        untrustedUntilPinned()
        assertTrue(adapter().signIn(mock<ActivityBase>()))
        assertEquals(1, ui.asked)
        assertEquals(CertPin(info.host, info.sha256), state.certPin)
    }

    @Test fun untrustedCertificate_declined_returnsFalse_noPin() = runBlocking {
        untrustedUntilPinned()
        ui.trust = false
        assertFalse(adapter().signIn(mock<ActivityBase>()))
        assertEquals(1, ui.asked)
        assertNull(state.certPin)
    }

    @Test fun stillUntrustedAfterPin_asksOnlyOnce() = runBlocking {
        server.failWith = { IOException("handshake", DavUntrustedCertificateException(info)) }
        assertFalse(adapter().signIn(mock<ActivityBase>()))
        assertEquals(1, ui.asked)
    }

    @Test fun notAWebDavEndpoint_surfacesAsNotAFolderMessage() {
        for (status in listOf(405, 200)) {
            server.statusOverride = { status }
            val e = assertThrows(Exception::class.java) { runBlocking { adapter().signIn(mock<ActivityBase>()) } }
            assertEquals("status $status", WebDavMessage.NOT_A_FOLDER.name, e.message)
            assertTrue("status $status", e.cause is DavProtocolException)
        }
    }

    @Test fun formatFingerprint_groupsByTwo() {
        assertEquals("AB:CD:EF", formatFingerprint("ABCDEF"))
    }
}

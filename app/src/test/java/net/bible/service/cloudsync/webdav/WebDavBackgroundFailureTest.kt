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
class WebDavBackgroundFailureTest {
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

    private suspend fun signedIn() = adapter().also { assertTrue(it.signIn(mock<ActivityBase>())) }
    private fun tmp() = File.createTempFile("webdav-bg", ".gz").also { it.writeBytes(byteArrayOf(1)); it.deleteOnExit() }

    @Test fun certificateChangedDuringSync_isUserFacingReconnect() = runBlocking {
        val a = signedIn()
        server.failWith = { IOException("handshake", DavUntrustedCertificateException(info)) }
        val e = runCatching { a.listFiles(null, null, null, null) }.exceptionOrNull()
        assertTrue("was $e", e is CloudSyncUserFacingException)
        e as CloudSyncUserFacingException
        assertTrue(e.requiresReconnect)
        assertEquals("CERTIFICATE_CHANGED", e.message)
        assertFalse(e is IOException)
    }

    @Test fun bareUnauthorizedDuringSync_isUserFacingReconnect() = runBlocking {
        val a = signedIn()
        server.statusOverride = { 401 }
        val e = runCatching { a.listFiles(null, null, null, null) }.exceptionOrNull()
        assertTrue("was \$e", e is CloudSyncUserFacingException)
        e as CloudSyncUserFacingException
        assertTrue(e.requiresReconnect)
        assertEquals("WRONG_CREDENTIALS", e.message)
    }

    @Test fun authFailureDuringSync_isUserFacingReconnect() = runBlocking {
        // The fake server's auth mode (a real challenge) with an adapter whose password is wrong.
        val a = WebDavCloudAdapter(
            WebDavConfig("https://h/dav/", "me", "wrong", "AndBible"), state, ui,
            clientFactory = { c, _ -> WebDavClient(createWebDavHttpClient(server.engine, c.username, c.password), c.serverUrl, c.username, c.password, { server.nowServer }) },
            nowMs = { server.nowServer },
        )
        assertTrue(a.signIn(mock<ActivityBase>()))
        server.authRequired = true
        val e = runCatching { a.listFiles(null, null, null, null) }.exceptionOrNull()
        assertTrue("was $e", e is CloudSyncUserFacingException)
        e as CloudSyncUserFacingException
        assertTrue(e.requiresReconnect)
        assertEquals("WRONG_CREDENTIALS", e.message)
    }

    @Test fun quota_isUserFacing_noReconnect() = runBlocking {
        val a = signedIn()
        server.statusOverride = { if (it.method.value == "PUT") 507 else null }
        val e = runCatching { a.upload("x.gz", tmp(), "AndBible") }.exceptionOrNull()
        assertTrue("was $e", e is CloudSyncUserFacingException)
        e as CloudSyncUserFacingException
        assertFalse(e.requiresReconnect)
        assertEquals("STORAGE_FULL", e.message)
    }

    @Test fun transient_staysIOException() = runBlocking {
        val a = signedIn()
        server.statusOverride = { 503 }
        val e = runCatching { a.listFiles(null, null, null, null) }.exceptionOrNull()
        assertTrue("was $e", e is IOException)
    }
}

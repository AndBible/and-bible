package net.bible.service.cloudsync.webdav

import com.nhaarman.mockitokotlin2.mock
import kotlinx.coroutines.runBlocking
import net.bible.android.TEST_SDK
import net.bible.android.view.activity.base.ActivityBase
import net.bible.service.cloudsync.nextcloud.FOLDER_MIMETYPE
import net.bible.sharedcore.webdav.DavCertificateInfo
import net.bible.sharedcore.webdav.WebDavClient
import net.bible.sharedcore.webdav.createWebDavHttpClient
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Folder-mtime skipping, self-calibration and device/server clock normalisation of [WebDavCloudAdapter]. */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [TEST_SDK])
class WebDavChangeDetectionTest {
    private val server = FakeDavServer(nowServer = 1_700_000_000_000L)
    private val state = object : WebDavStateStore { override var propagation = Propagation.UNKNOWN; override var certPin: CertPin? = null }
    private val ui = object : WebDavSignInUi {
        override suspend fun confirmCertificate(info: DavCertificateInfo) = true
        override fun message(kind: WebDavMessage) = kind.name
    }
    private var deviceNow = server.nowServer

    private fun adapter() = WebDavCloudAdapter(
        WebDavConfig("https://h/dav/", "me", "pw", "AndBible"), state, ui,
        clientFactory = { c, _ -> WebDavClient(createWebDavHttpClient(server.engine, c.username, c.password), c.serverUrl, c.username, c.password, { deviceNow }) },
        nowMs = { deviceNow },
    )
    private suspend fun signedIn() = adapter().also { assertTrue(it.signIn(mock<ActivityBase>())) }
    private fun tmp() = File.createTempFile("webdav-test", ".gz").also { it.writeBytes(byteArrayOf(1, 2, 3)); it.deleteOnExit() }
    private fun bytes() = byteArrayOf(1)

    /** Both clocks tick together; the server's offset from the device stays constant. */
    private fun advance(ms: Long) { server.advance(ms); deviceNow += ms }
    private fun depth1Propfinds() = server.requests.filter { it.method.value == "PROPFIND" && it.headers["Depth"] == "1" }

    /** AndBible/s/d1 and d2 with one file each, then 10 minutes pass. */
    private suspend fun twoFolders(a: WebDavCloudAdapter) {
        a.createNewFolder("s")
        a.createNewFolder("d1", "AndBible/s"); a.createNewFolder("d2", "AndBible/s")
        server.put("AndBible/s/d1/a.gz", bytes()); server.put("AndBible/s/d2/b.gz", bytes())
        advance(600_000)
    }

    @Test fun calibratesYes_onPropagatingServer() = runBlocking {
        val a = signedIn(); a.createNewFolder("x"); advance(5_000)
        a.upload("1.1.sqlite3.gz", tmp(), "AndBible/x")
        assertEquals(Propagation.YES, state.propagation)
    }

    @Test fun calibratesNo_onNonPropagatingServer() = runBlocking {
        server.propagates = false
        val a = signedIn(); a.createNewFolder("x"); advance(5_000)
        a.upload("1.1.sqlite3.gz", tmp(), "AndBible/x")
        assertEquals(Propagation.NO, state.propagation)
    }

    @Test fun inconclusive_keepsPreviousState() = runBlocking {
        state.propagation = Propagation.NO
        val a = signedIn(); a.createNewFolder("x")
        a.upload("1.1.sqlite3.gz", tmp(), "AndBible/x")
        assertEquals(Propagation.NO, state.propagation)
    }

    @Test fun unchangedFolder_isNotListed_whenYes() = runBlocking {
        state.propagation = Propagation.YES
        val a = signedIn(); twoFolders(a)
        a.getFolders("AndBible/s"); server.clearRequests()
        val r = a.listFiles(listOf("AndBible/s/d1", "AndBible/s/d2"), createdTimeAtLeast = deviceNow - 60_000)
        assertTrue(r.isEmpty())
        assertEquals(0, server.requests.size)
    }

    @Test fun changedFolder_isListed_whenYes() = runBlocking {
        state.propagation = Propagation.YES
        val a = signedIn(); twoFolders(a)
        a.getFolders("AndBible/s")
        a.upload("new.gz", tmp(), "AndBible/s/d2")
        a.getFolders("AndBible/s"); server.clearRequests()
        val r = a.listFiles(listOf("AndBible/s/d1", "AndBible/s/d2"), createdTimeAtLeast = deviceNow - 60_000)
        assertEquals(listOf("AndBible/s/d2/new.gz"), r.map { it.id })
        assertEquals(1, server.requests.size)
        assertEquals(1, depth1Propfinds().size)
        assertEquals("/dav/AndBible/s/d2", depth1Propfinds().single().url.encodedPath.trimEnd('/'))
    }

    private fun nonPropagatingListsAll(p: Propagation) = runBlocking {
        state.propagation = p; server.propagates = false
        val a = signedIn(); twoFolders(a)
        a.getFolders("AndBible/s")
        server.put("AndBible/s/d1/new.gz", bytes()); server.clearRequests()
        val r = a.listFiles(listOf("AndBible/s/d1", "AndBible/s/d2"), createdTimeAtLeast = deviceNow - 60_000)
        assertEquals(listOf("AndBible/s/d1/new.gz"), r.map { it.id })
        assertEquals(2, depth1Propfinds().size)
    }
    @Test fun nonPropagatingServer_alwaysListsAllFolders() = nonPropagatingListsAll(Propagation.NO)
    @Test fun unknown_alwaysListsAllFolders() = nonPropagatingListsAll(Propagation.UNKNOWN)

    @Test fun staleCacheEntry_isRefetched() = runBlocking {
        state.propagation = Propagation.YES
        val a = signedIn(); twoFolders(a)
        a.getFolders("AndBible/s")
        advance(600_000)
        server.put("AndBible/s/d1/other-device.gz", bytes())
        val r = a.listFiles(listOf("AndBible/s/d1"), createdTimeAtLeast = deviceNow - 60_000)
        assertEquals(listOf("AndBible/s/d1/other-device.gz"), r.map { it.id })
    }

    /** Server clock [offset] ms from the device; a file from another device must be found, an old one must not. */
    private fun skew(offset: Long, p: Propagation) = runBlocking {
        deviceNow = server.nowServer - offset
        state.propagation = p
        val a = signedIn(); a.createNewFolder("d")
        server.put("AndBible/d/old.gz", bytes())
        advance(600_000)
        a.getFolders("AndBible")
        val first = deviceNow
        assertTrue("old file is outside the window", a.listFiles(listOf("AndBible/d"), createdTimeAtLeast = first).isEmpty())
        server.advance(30_000); server.put("AndBible/d/new.gz", bytes()); deviceNow += 30_000
        a.getFolders("AndBible")
        assertEquals(listOf("AndBible/d/new.gz"), a.listFiles(listOf("AndBible/d"), createdTimeAtLeast = first).map { it.id })
    }
    @Test fun clockSkew_serverAhead_noPatchLost() { skew(10 * 60_000L, Propagation.YES); }
    @Test fun clockSkew_serverAhead_noPatchLost_propagationNo() { skew(10 * 60_000L, Propagation.NO) }
    @Test fun clockSkew_serverBehind_noPatchLost() { skew(-10 * 60_000L, Propagation.YES) }
    @Test fun clockSkew_serverBehind_noPatchLost_propagationNo() { skew(-10 * 60_000L, Propagation.NO) }

    @Test fun documentMetaRewrite_bumpsFolder() = runBlocking {
        state.propagation = Propagation.YES
        val a = signedIn(); a.createNewFolder("docs"); a.createNewFolder("KJV", "AndBible/docs")
        server.put("AndBible/docs/KJV/meta.json", bytes())
        advance(5_000)
        a.getFolders("AndBible/docs")
        advance(5_000)
        a.delete("AndBible/docs/KJV/meta.json"); a.upload("meta.json", tmp(), "AndBible/docs/KJV")
        a.getFolders("AndBible/docs")
        assertEquals(1, a.listFiles(listOf("AndBible/docs/KJV"), name = "meta.json", createdTimeAtLeast = deviceNow - 1000).size)
    }

    /**
     * DocumentStore.writeMeta deletes then re-uploads. A server that bumps the folder mtime on DELETE but
     * not on creating a child must not be calibrated YES from a cached pre-delete mtime.
     */
    @Test fun deleteThenUpload_onServerThatBumpsOnlyOnDelete_isNotCalibratedYes() = runBlocking {
        server.propagatesOnCreate = false
        val a = signedIn(); a.createNewFolder("docs"); a.createNewFolder("KJV", "AndBible/docs")
        server.put("AndBible/docs/KJV/meta.json", bytes())
        advance(5_000)
        a.getFolders("AndBible/docs") // caches KJV's pre-delete mtime
        advance(5_000)
        a.delete("AndBible/docs/KJV/meta.json"); a.upload("meta.json", tmp(), "AndBible/docs/KJV")
        assertNotEquals(Propagation.YES, state.propagation)
    }

    /** The calibration PROPFINDs run after a successful PUT; their failure must not fail the upload. */
    private fun uploadSurvivesFailingCalibration(status: Int) = runBlocking {
        val a = signedIn(); a.createNewFolder("x"); advance(5_000)
        var put = false
        server.statusOverride = { req ->
            if (req.method.value == "PUT") put = true
            if (put && req.method.value == "PROPFIND") status else null
        }
        val f = a.upload("1.1.sqlite3.gz", tmp(), "AndBible/x")
        assertEquals("AndBible/x/1.1.sqlite3.gz", f.id)
        assertEquals(Propagation.UNKNOWN, state.propagation)
    }
    @Test fun upload_succeeds_whenPostPutPropfindIs404() = uploadSurvivesFailingCalibration(404)
    @Test fun upload_succeeds_whenPostPutPropfindIs503() = uploadSurvivesFailingCalibration(503)

    /** Pins the 120 s margin and the sign of the offset: server mtime == since + offset - margin is in, one second older is out. */
    @Test fun listFiles_boundary_withServerClockTenMinutesAhead() = runBlocking {
        val offset = 600_000L
        server.nowServer += offset; deviceNow = server.nowServer - offset
        val a = signedIn(); a.createNewFolder("d")
        val since = deviceNow
        val boundary = since + offset - WebDavCloudAdapter.CLOCK_MARGIN_MS
        val end = server.nowServer
        server.nowServer = boundary; server.put("AndBible/d/in.gz", bytes())
        server.nowServer = boundary - 1000; server.put("AndBible/d/out.gz", bytes())
        server.nowServer = end
        assertEquals(listOf("AndBible/d/in.gz"), a.listFiles(listOf("AndBible/d"), createdTimeAtLeast = since).map { it.id })
    }

    @Test fun listFiles_resourceWithoutLastModified_isIncluded() = runBlocking {
        state.propagation = Propagation.YES
        val a = signedIn(); a.createNewFolder("d")
        server.put("AndBible/d/f.gz", bytes())
        advance(600_000)
        server.omitLastModified = true
        assertEquals(listOf("AndBible/d/f.gz"), a.listFiles(listOf("AndBible/d"), createdTimeAtLeast = deviceNow - 60_000).map { it.id })
    }
}

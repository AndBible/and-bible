package net.bible.service.cloudsync.webdav

import com.nhaarman.mockitokotlin2.mock
import kotlinx.coroutines.runBlocking
import net.bible.android.TEST_SDK
import net.bible.android.view.activity.base.ActivityBase
import net.bible.service.cloudsync.CloudFile
import net.bible.sharedcore.webdav.DavCertificateInfo
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.util.UUID

/**
 * Opt-in test against a real WebDAV server (rclone via scripts/webdav-test-server.sh, or Nextcloud).
 * Skipped unless WEBDAV_IT_URL is set. Also reads WEBDAV_IT_USER, WEBDAV_IT_PASSWORD and the optional
 * WEBDAV_IT_EXPECT_PROPAGATION (YES/NO).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [TEST_SDK])
class WebDavIntegrationTest {
    private val url: String? = System.getenv("WEBDAV_IT_URL")
    private val user = System.getenv("WEBDAV_IT_USER") ?: ""
    private val password = System.getenv("WEBDAV_IT_PASSWORD") ?: ""
    private val base = "andbible-it-" + UUID.randomUUID().toString().take(8)

    private val state = memoryState()
    private val ui = CountingUi()
    private val activity = lazy { mock<ActivityBase>() }
    private var main: WebDavCloudAdapter? = null
    private val tmpFiles = mutableListOf<File>()

    private fun memoryState() = object : WebDavStateStore {
        override var propagation = Propagation.UNKNOWN
        override var certPin: CertPin? = null
    }

    private class CountingUi : WebDavSignInUi {
        var asked = 0
        override suspend fun confirmCertificate(info: DavCertificateInfo): Boolean { asked++; return true }
        override fun message(kind: WebDavMessage) = kind.name
    }

    @Before fun requireServer() {
        Assume.assumeTrue("WEBDAV_IT_URL not set: integration test skipped", url != null)
    }

    @After fun cleanUp() {
        runBlocking { try { main?.delete(base) } catch (e: Exception) { System.err.println("cleanup of $base failed: $e") } }
        tmpFiles.forEach { it.delete() }
    }

    private fun newAdapter(s: WebDavStateStore = state, u: WebDavSignInUi = ui) =
        WebDavCloudAdapter(WebDavConfig(url!!, user, password, base), s, u)

    private suspend fun signedIn(): WebDavCloudAdapter {
        val a = newAdapter()
        assertTrue(a.signIn(activity.value))
        main = a
        return a
    }

    private fun tmp(bytes: ByteArray): File = File.createTempFile("webdav-it", ".gz").also { it.writeBytes(bytes); tmpFiles += it }

    private suspend fun WebDavCloudAdapter.bytesOf(f: CloudFile) = ByteArrayOutputStream().also { download(f.id, it) }.toByteArray()

    @Test fun signIn_trustsSelfSignedOnce() = runBlocking {
        val a = newAdapter()
        assertTrue(a.signIn(activity.value))
        main = a
        assertEquals(1, ui.asked)
        val second = CountingUi()
        assertTrue(newAdapter(state, second).signIn(activity.value))
        assertEquals(0, second.asked)
    }

    @Test fun fullContract() = runBlocking {
        val a = signedIn()
        val folder = a.createNewFolder("contract")
        val big = ByteArray(3 * 1024 * 1024).also { java.util.Random(42).nextBytes(it) }
        val contents = mapOf("one.gz" to "first".toByteArray(), "two.gz" to "second".toByteArray(), "big.gz" to big)
        contents.forEach { (n, b) -> a.upload(n, tmp(b), folder.id) }

        contents.forEach { (n, b) ->
            val found = a.listFiles(parentsIds = listOf(folder.id), name = n)
            assertEquals(1, found.size)
            assertEquals(b.size.toLong(), found[0].size)
            assertArrayEquals(b, a.bytesOf(found[0]))
        }
        assertEquals(listOf("contract"), a.getFolders(base).map { it.name })

        val victim = a.listFiles(parentsIds = listOf(folder.id), name = "one.gz").single()
        a.delete(victim.id)
        try { a.get(victim.id); fail("expected FileNotFoundException") } catch (e: FileNotFoundException) { }
    }

    @Test fun calibration() = runBlocking {
        val a = signedIn()
        val folder = a.createNewFolder("calib")
        a.upload("a.gz", tmp(byteArrayOf(1)), folder.id)
        Thread.sleep(2000)
        a.upload("b.gz", tmp(byteArrayOf(2)), folder.id)
        val expected = System.getenv("WEBDAV_IT_EXPECT_PROPAGATION")
        if (expected != null) assertEquals(Propagation.valueOf(expected), state.propagation)
        else assertNotEquals(Propagation.UNKNOWN, state.propagation)
    }

    @Test fun incrementalListing() = runBlocking {
        val a = signedIn()
        val fa = a.createNewFolder("A")
        val fb = a.createNewFolder("B")
        assertEquals(setOf("A", "B"), a.getFolders(base).map { it.name }.toSet())
        Thread.sleep(2000)
        val beforeUpload = System.currentTimeMillis()
        val uploaded = a.upload("new.gz", tmp(byteArrayOf(7)), fb.id)
        a.getFolders(base)
        val newer = a.listFiles(parentsIds = listOf(fa.id, fb.id), createdTimeAtLeast = beforeUpload)
        assertEquals(listOf(uploaded.id), newer.map { it.id })
    }

    @Test fun specialCharacters() = runBlocking {
        val a = signedIn()
        val folder = a.createNewFolder("Käännös +1")
        val payload = "päivää + värit".toByteArray()
        a.upload("A B+C.gz", tmp(payload), folder.id)
        assertEquals(listOf("Käännös +1"), a.getFolders(base).map { it.name })
        val file = a.listFiles(parentsIds = listOf(folder.id), name = "A B+C.gz").single()
        assertArrayEquals(payload, a.bytesOf(file))
    }
}

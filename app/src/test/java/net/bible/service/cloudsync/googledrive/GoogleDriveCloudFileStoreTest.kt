package net.bible.service.cloudsync.googledrive

import android.accounts.Account
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.testing.http.MockHttpTransport
import com.google.api.client.testing.http.MockLowLevelHttpResponse
import com.google.api.services.drive.Drive
import kotlinx.coroutines.runBlocking
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.sharedcore.platform.cloud.CloudFileStore
import okio.Buffer
import okio.ForwardingSink
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class GoogleDriveCloudFileStoreTest {
    private fun store(content: ByteArray): CloudFileStore {
        val response = MockLowLevelHttpResponse()
            .setContent(content)
            .setContentType("application/octet-stream")
            .setStatusCode(206)
            .addHeader("Content-Range", "bytes 0-${content.lastIndex}/${content.size}")
        val service = Drive.Builder(
            MockHttpTransport.Builder().setLowLevelHttpResponse(response).build(),
            GsonFactory.getDefaultInstance(),
            null
        ).setApplicationName("test").build()
        return GoogleDriveCloudAdapter().apply {
            javaClass.getDeclaredField("account").apply { isAccessible = true }
                .set(this, Account("test", "com.google"))
            javaClass.getDeclaredField("_service").apply { isAccessible = true }
                .set(this, service)
        }
    }

    @Test
    fun downloadFlushesShortPayloadClosesSinkAndReportsCumulativeBytes() = runBlocking {
        val content = byteArrayOf(0, 1, 2, 3, -1)
        val buffer = Buffer()
        var closed = false
        val sink = object : ForwardingSink(buffer) {
            override fun close() {
                closed = true
                super.close()
            }
        }
        val progress = mutableListOf<Long>()

        store(content).download("file-id", sink) { progress.add(it) }

        assertArrayEquals(content, buffer.readByteArray())
        assertTrue(closed)
        assertTrue(progress.isNotEmpty())
        assertEquals(content.size.toLong(), progress.last())
        assertTrue(progress.zipWithNext().all { (a, b) -> a <= b })
    }

    @Test
    fun progressFailurePropagatesAndClosesSink() = runBlocking {
        val failure = IOException("abort download")
        var closed = false
        val sink = object : ForwardingSink(Buffer()) {
            override fun close() {
                closed = true
                super.close()
            }
        }
        try {
            store(byteArrayOf(1, 2, 3)).download("file-id", sink) { throw failure }
            throw AssertionError("Progress failure must abort the transfer")
        } catch (actual: IOException) {
            // Coroutine stack-trace recovery may copy an IOException across withContext.
            assertTrue(actual === failure || actual.cause === failure)
        }
        assertTrue(closed)
    }
}

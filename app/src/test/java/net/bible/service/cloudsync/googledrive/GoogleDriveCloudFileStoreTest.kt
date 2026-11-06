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

    class PermissionActivity : net.bible.android.view.activity.base.ActivityBase() {
        override val doNotInitializeApp = true
        override val allowThemeChange = false
        override val disableBaseSetupUi = true
        fun cancelPermission(requestCode: Int) = onActivityResult(requestCode, android.app.Activity.RESULT_CANCELED, null)
    }

    @Test
    fun cancelledDrivePermissionDoesNotCreateManualSyncFailure() {
        val controller = org.robolectric.Robolectric.buildActivity(PermissionActivity::class.java).setup()
        val activity = controller.get()
        val preferences = net.bible.service.common.CommonUtils.realSharedPreferences
        val oldAccount = preferences.getString("cloud_sync_last_account", null)
        val adapter = store(byteArrayOf(1)) as GoogleDriveCloudAdapter
        val account = Account("cancel-test", "com.google")
        val parcel = android.os.Parcel.obtain()
        account.writeToParcel(parcel, 0)
        val encoded = android.util.Base64.encodeToString(parcel.marshall(), android.util.Base64.DEFAULT)
        parcel.recycle()
        preferences.edit().putString("cloud_sync_last_account", encoded).commit()
        val permission = android.content.Intent("test.google.permission")
        val recoverable = com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException(
            com.google.android.gms.auth.UserRecoverableAuthException("permission", permission),
        )
        val transport = object : MockHttpTransport() {
            override fun buildRequest(method: String, url: String): com.google.api.client.http.LowLevelHttpRequest =
                object : com.google.api.client.testing.http.MockLowLevelHttpRequest(url) {
                    override fun execute(): com.google.api.client.http.LowLevelHttpResponse { throw recoverable }
                }
        }
        val service = Drive.Builder(transport, GsonFactory.getDefaultInstance(), null).setApplicationName("test").build()
        adapter.javaClass.getDeclaredField("_service").apply { isAccessible = true }.set(adapter, service)
        val koin = org.koin.mp.KoinPlatform.getKoin()
        val cloudDialog = net.bible.sharedcore.ui.dialog.AppDialogController()
        val cloud = net.bible.service.cloudsync.CloudSync(
            cloudDialog, koin.get(), koin.get(), koin.get(),
            object : net.bible.service.cloudsync.CloudAdapterFactory {
                override val available = listOf("GOOGLE_DRIVE")
                override fun displayName(id: String) = id
                override fun create(id: String) = adapter
            }, net.bible.sharedcore.platform.sync.SyncScheduler { error("cancelled sign-in must not schedule") },
            koin.get(), koin.get(), koin.get(),
        )
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)
        val center = net.bible.android.control.activity.ActivityCenter(scope) { 0L }
        val failures = net.bible.android.control.activity.SyncActivitySource(center, cloud.runningChanged) { "Syncing" }
        val sync = net.bible.service.cloudsync.SyncNow(
            net.bible.service.cloudsync.CloudSyncPort.of(cloud) {
                failures.reportFailure(net.bible.android.control.activity.ActivityKind.SYNC, it)
            }, net.bible.sharedcore.event.EventSource(), { true }, {}, { true }, { false }, {}, scope,
        )
        try {
            sync.start()
            val shadow = org.robolectric.Shadows.shadowOf(activity)
            val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10)
            var started: org.robolectric.shadows.ShadowActivity.IntentForResult? = null
            while (started == null && System.nanoTime() < deadline) {
                org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
                started = shadow.nextStartedActivityForResult
                if (started == null) Thread.sleep(10)
            }
            assertTrue("Drive permission intent must be launched", started != null)
            activity.cancelPermission(started!!.requestCode)
            val completionDeadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5)
            while (sync.running.value && center.items.value.isEmpty() && System.nanoTime() < completionDeadline) {
                org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
                Thread.sleep(10)
            }
            assertTrue("user cancellation must not create a failed row", center.items.value.isEmpty())
            assertTrue("cancelled sync must finish", !sync.running.value)
            assertTrue("cancelled account must be cleared", !adapter.signedIn)
            assertEquals(null, preferences.getString("cloud_sync_last_account", null))
            assertEquals(null, cloudDialog.pending.value)
        } finally {
            sync.close()
            failures.close()
            scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
            preferences.edit().putString("cloud_sync_last_account", oldAccount).commit()
            controller.pause().stop().destroy()
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

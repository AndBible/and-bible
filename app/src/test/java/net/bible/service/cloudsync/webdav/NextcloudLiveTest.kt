package net.bible.service.cloudsync.webdav

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.mock
import kotlinx.coroutines.runBlocking
import net.bible.android.TEST_SDK
import net.bible.android.database.SyncDao
import net.bible.service.cloudsync.SyncableDatabaseDefinition
import net.bible.android.database.SyncableRoomDatabase
import net.bible.service.cloudsync.SyncableDatabaseAccessor
import net.bible.sharedcore.webdav.DavCertificateInfo
import okio.Path.Companion.toOkioPath
import okio.sink
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

/**
 * Live round trip against a real Nextcloud through the NEXT_CLOUD preset URL (`nextcloudDavUrl`).
 * Skipped unless `I2_NEXTCLOUD_URL` is set, format `serverUrl|user|password`
 * (see scripts/nextcloud-local.sh).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [TEST_SDK])
class NextcloudLiveTest {
    private class RejectingUi : WebDavSignInUi {
        override suspend fun confirmCertificate(info: DavCertificateInfo) = false
        override fun message(kind: WebDavMessage) = kind.name
    }

    @Test fun nextcloudRoundTrip() = runBlocking {
        val spec = System.getenv("I2_NEXTCLOUD_URL")
        if (spec == null) println("NextcloudLiveTest skipped: I2_NEXTCLOUD_URL (url|user|password) not set")
        Assume.assumeTrue("I2_NEXTCLOUD_URL not set", spec != null)
        val (server, user, password) = spec!!.split("|").also { assertEquals("expected url|user|password", 3, it.size) }

        val base = "i2-live-" + UUID.randomUUID()
        val state = object : WebDavStateStore {
            override var propagation = Propagation.UNKNOWN
            override var certPin: CertPin? = null
        }
        val adapter = WebDavCloudAdapter(
            WebDavConfig(nextcloudDavUrl(server, user), user, password, base), state, RejectingUi(), "i2-live-device", ::createWebDavClient,
        )
        val tmp = File.createTempFile("i2-live", ".bin")
        try {
            assertTrue(adapter.signIn())
            val folder = adapter.createNewFolder("sync")

            val configs = mutableMapOf<String, String>()
            val dao = mock<SyncDao> {
                onBlocking { setConfig(any<String>(), any<String>()) } doAnswer { configs[it.arguments[0] as String] = it.arguments[1] as String; Unit }
            }
            val db = mock<SyncableRoomDatabase> { on { syncDao() } doAnswer { dao } }
            val dbDef = SyncableDatabaseAccessor(db, { db }, { db }, "unused", SyncableDatabaseDefinition.BOOKMARKS, null, "dev")
            adapter.makeSyncFolderKnown(dbDef, "n", folder.id)
            assertEquals(1, configs.size)

            val payload = ByteArray(64 * 1024).also { java.util.Random(7).nextBytes(it) }
            tmp.writeBytes(payload)
            val before = System.currentTimeMillis() - 60_000
            adapter.upload("payload.bin", tmp.toOkioPath(), folder.id)

            val found = adapter.listFiles(parentsIds = listOf(folder.id), name = "payload.bin", createdTimeAtLeast = before).single()
            assertEquals(payload.size.toLong(), found.size)
            val out = ByteArrayOutputStream()
            adapter.download(found.id, out.sink())
            assertArrayEquals(payload, out.toByteArray())
        } finally {
            tmp.delete()
            try { adapter.delete(base) } catch (e: Exception) { System.err.println("cleanup of $base failed: $e") }
        }
    }
}

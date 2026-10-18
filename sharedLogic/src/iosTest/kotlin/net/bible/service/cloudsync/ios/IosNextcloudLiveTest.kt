package net.bible.service.cloudsync.ios

import kotlinx.coroutines.test.runTest
import net.bible.service.cloudsync.webdav.WebDavCloudAdapter
import net.bible.service.cloudsync.webdav.WebDavConfig
import net.bible.service.cloudsync.webdav.WebDavMessage
import net.bible.service.cloudsync.webdav.WebDavSignInUi
import net.bible.service.cloudsync.webdav.nextcloudDavUrl
import net.bible.sharedcore.webdav.DavCertificateInfo
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.Buffer
import platform.Foundation.NSDate
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import platform.Foundation.NSUserDefaults
import platform.Foundation.timeIntervalSince1970
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Live round trip against a real Nextcloud (iOS Darwin engine). Skipped, with a printed reason, unless
 * `/tmp/andbible-i2-nextcloud.url` exists on the machine running the test; content `serverUrl|user|password`.
 * `makeSyncFolderKnown` is not covered here: it needs a Room database, which the JVM live test uses.
 */
class IosNextcloudLiveTest {
    private val urlFile = "/tmp/andbible-i2-nextcloud.url".toPath()

    @Test fun nextcloudRoundTrip() = runTest {
        if (!FileSystem.SYSTEM.exists(urlFile)) {
            println("IosNextcloudLiveTest skipped: $urlFile not found (content: url|user|password)")
            return@runTest
        }
        val parts = FileSystem.SYSTEM.read(urlFile) { readUtf8() }.trim().split("|")
        assertEquals(3, parts.size, "expected url|user|password")
        val (server, user, password) = parts

        val base = "i2-live-" + NSUUID().UUIDString
        val prefs = IosDevicePrefs(NSUserDefaults(suiteName = "i2-live-$base"))
        val state = IosWebDavStateStore(prefs)
        val ui = object : WebDavSignInUi {
            override suspend fun confirmCertificate(info: DavCertificateInfo) = false
            override fun message(kind: WebDavMessage) = kind.name
        }
        val nowMs = { (NSDate().timeIntervalSince1970 * 1000).toLong() }
        val adapter = WebDavCloudAdapter(
            WebDavConfig(nextcloudDavUrl(server, user), user, password, base), state, ui, "i2-live-device",
            { config, st -> createIosWebDavClient(config, st, nowMs) }, nowMs,
        )

        val tmp = (NSTemporaryDirectory().trimEnd('/') + "/i2-live-" + NSUUID().UUIDString + ".bin").toPath()
        try {
            assertTrue(adapter.signIn())
            val folder = adapter.createNewFolder("sync")
            val payload = Random(7).nextBytes(64 * 1024)
            FileSystem.SYSTEM.write(tmp) { write(payload) }
            val before = nowMs() - 60_000
            adapter.upload("payload.bin", tmp, folder.id)
            val found = adapter.listFiles(parentsIds = listOf(folder.id), name = "payload.bin", createdTimeAtLeast = before).single()
            assertEquals(payload.size.toLong(), found.size)
            val out = Buffer()
            adapter.download(found.id, out)
            assertContentEquals(payload, out.readByteArray())
        } finally {
            FileSystem.SYSTEM.delete(tmp)
            try { adapter.delete(base) } catch (e: Exception) { println("cleanup of $base failed: $e") }
        }
    }
}

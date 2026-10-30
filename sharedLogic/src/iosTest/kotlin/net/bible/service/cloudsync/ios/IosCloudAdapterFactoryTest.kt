package net.bible.service.cloudsync.ios

import kotlinx.coroutines.test.runTest
import net.bible.service.cloudsync.SyncSettingsPlatform
import net.bible.service.cloudsync.filetree.FileTreeCloudAdapter
import net.bible.service.cloudsync.webdav.WebDavConfig
import net.bible.service.cloudsync.webdav.WebDavMessage
import net.bible.service.cloudsync.webdav.WebDavSignInUi
import net.bible.service.cloudsync.webdav.WebDavStateStore
import net.bible.sharedcore.platform.cloud.CloudFileStore
import net.bible.sharedcore.platform.cloud.FileCoordination
import net.bible.sharedcore.platform.cloud.FileTreeCloudStore
import net.bible.sharedcore.platform.secrets.SecretStore
import net.bible.sharedcore.webdav.DavCertificateInfo
import net.bible.sharedcore.webdav.WebDavClient
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import platform.Foundation.NSUserDefaults
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import net.bible.service.cloudsync.host.HostCloudAdapterFactory
import net.bible.service.cloudsync.host.PlainHttpRefusedException

class IosCloudAdapterFactoryTest {
    private val suite = "i2-factory-${NSUUID().UUIDString}"
    private val defaults = NSUserDefaults(suiteName = suite)
    private val secrets = object : SecretStore {
        val map = mutableMapOf<String, String>()
        override fun get(key: String) = map[key]
        override fun put(key: String, value: String) { map[key] = value }
        override fun remove(key: String) { map.remove(key) }
    }
    private val platform = IosSyncSettingsPlatform(IosDevicePrefs(defaults), secrets) { "hint" }
    private val fs = FileSystem.SYSTEM
    private val tmpRoot: Path = (NSTemporaryDirectory().trimEnd('/') + "/i2-factory-" + NSUUID().UUIDString).toPath()
    private var clock = 1L
    private val recorded = mutableListOf<WebDavConfig>()

    private val ui = object : WebDavSignInUi {
        override suspend fun confirmCertificate(info: DavCertificateInfo) = false
        override fun message(kind: WebDavMessage) = kind.name
    }

    init { fs.createDirectories(tmpRoot) }

    @AfterTest
    fun cleanUp() {
        defaults.removePersistentDomainForName(suite)
        fs.deleteRecursively(tmpRoot)
    }

    private fun store(root: Path?): CloudFileStore =
        FileTreeCloudStore(fs, { root }, FileCoordination.PassThrough, now = { clock++ })

    private fun factory(
        iCloud: () -> CloudFileStore = { store(tmpRoot) },
        awaitReady: suspend () -> Unit = {},
    ) = HostCloudAdapterFactory(
        platform, "dev-1", { "name-$it" }, iCloud, ui, tmpRoot / "tmp",
        clientFactory = { c: WebDavConfig, _: WebDavStateStore ->
            recorded += c
            // Sign-in would hit the network; the recorded config is all these tests need.
            error("I2-test: no network")
        },
        awaitReady = awaitReady,
    )

    @Test fun availableOrder() {
        val f = factory()
        assertEquals(listOf("ICLOUD_DRIVE", "NEXT_CLOUD", "WEBDAV"), f.available)
        assertEquals("name-WEBDAV", f.displayName("WEBDAV"))
    }

    @Test fun iCloudAdapterRoundTrip() = runTest {
        val a = factory().create("ICLOUD_DRIVE")
        assertIs<FileTreeCloudAdapter>(a)
        assertTrue(a.signIn())
        val folder = a.createNewFolder("sync")
        val src = tmpRoot / "src.txt"
        fs.write(src) { writeUtf8("hello") }
        val up = a.upload("f.txt", src, folder.id)
        assertEquals(listOf("f.txt"), a.listFiles(listOf(folder.id)).map { it.name })
        val dst = tmpRoot / "dst.txt"
        a.download(up.id, fs.sink(dst))
        assertEquals("hello", fs.read(dst) { readUtf8() })
    }

    @Test fun iCloudAdapterWithoutAccountIsSignedOut() = runTest {
        val a = factory(iCloud = { store(null) }).create("ICLOUD_DRIVE")
        assertFalse(a.signedIn)
        assertFalse(a.signIn())
    }

    @Test fun iCloudSignInAwaitsContainerReadiness() = runTest {
        var ready = false
        val a = factory(
            iCloud = { FileTreeCloudStore(fs, { if (ready) tmpRoot else null }, FileCoordination.PassThrough, now = { clock++ }) },
            awaitReady = { ready = true },
        ).create("ICLOUD_DRIVE")
        assertTrue(a.signIn(), "sign-in must wait for the container lookup before reading the root")
        assertTrue(ready)
    }

    @Test fun nextCloudPresetBuildsDavConfig() = runTest {
        platform.setCredential("cloud_sync_server_url", "https://c.example/")
        platform.setCredential("cloud_sync_username", "anna")
        platform.setCredential("cloud_sync_password", "pw")
        platform.setCredential("cloud_sync_folder_path", "AndBible")
        runCatching { factory().create("NEXT_CLOUD").signIn() }
        assertEquals(
            listOf(WebDavConfig("https://c.example/remote.php/dav/files/anna", "anna", "pw", "AndBible")),
            recorded,
        )
    }

    @Test fun publicHttpNextcloudRefusedBeforeClientCreation() = runTest {
        platform.setCredential("cloud_sync_server_url", "http://example.org/nc")
        platform.setCredential("cloud_sync_username", "anna")
        val failure = runCatching { factory().create("NEXT_CLOUD").signIn() }.exceptionOrNull()
        assertTrue(failure != null, "stored public HTTP must not bypass entry-time policy")
        assertTrue(recorded.isEmpty(), "refuse before constructing a client or sending a request")
        val refusal = generateSequence(failure) { it.cause }.filterIsInstance<PlainHttpRefusedException>().firstOrNull()
        assertTrue(refusal != null, "retain a typed platform-policy cause")
        assertEquals("http://example.org/nc", refusal.url)
        assertEquals("HTTPS_REQUIRED", failure.message)
    }

    @Test fun plainHttpRefusalDiagnosticDoesNotExposeUrlSecrets() {
        val url = "http://alice:password-secret@example.org/nc?token=query-secret"
        val refusal = PlainHttpRefusedException(url)
        assertEquals(url, refusal.url, "typed contract retains the rejected URL")
        for (secret in listOf("alice", "password-secret", "query-secret", "token=")) {
            assertFalse(refusal.toString().contains(secret), "diagnostic must omit URL credentials")
        }
    }

    @Test fun localHttpNextcloudReachesClientFactory() = runTest {
        platform.setCredential("cloud_sync_server_url", "http://NAS.local:8080/")
        platform.setCredential("cloud_sync_username", "anna")
        runCatching { factory().create("NEXT_CLOUD").signIn() }
        assertEquals("http://NAS.local:8080/remote.php/dav/files/anna", recorded.single().serverUrl)
    }

    @Test fun webDavUsesWebdavKeys() = runTest {
        platform.setCredential("webdav_sync_server_url", "https://w.example/dav/")
        platform.setCredential("webdav_sync_username", "bob")
        platform.setCredential("webdav_sync_password", "secret")
        platform.setCredential("webdav_sync_folder_path", "Sync")
        runCatching { factory().create("WEBDAV").signIn() }
        assertEquals(listOf(WebDavConfig("https://w.example/dav", "bob", "secret", "Sync")), recorded)
    }

    @Test fun unknownIdThrows() {
        assertFailsWith<IllegalStateException> { factory().create("GOOGLE_DRIVE") }
    }

    /** The factory reads only the common [SyncSettingsPlatform] contract: credentials, HTTP policy and the state store. */
    @Test fun readsCredentialsPolicyAndStateFromAnySyncSettingsPlatform() = runTest {
        val state = platform.webDavState
        val seenStates = mutableListOf<WebDavStateStore>()
        val fake = object : SyncSettingsPlatform by platform {
            override fun credential(key: String) = when (key) {
                "webdav_sync_server_url" -> "https://fake.example/dav/"
                "webdav_sync_username" -> "u"
                "webdav_sync_password" -> "p"
                "webdav_sync_folder_path" -> "f"
                else -> ""
            }
            override val webDavState: WebDavStateStore = state
        }
        val f = HostCloudAdapterFactory(
            fake, "dev-1", { it }, { store(tmpRoot) }, ui, tmpRoot / "tmp",
            clientFactory = { c: WebDavConfig, s: WebDavStateStore -> recorded += c; seenStates += s; error("I2-test: no network") },
        )
        runCatching { f.create("WEBDAV").signIn() }
        assertEquals(listOf(WebDavConfig("https://fake.example/dav", "u", "p", "f")), recorded)
        assertTrue(seenStates.single() === state, "the client gets the platform's own state store")
    }
}

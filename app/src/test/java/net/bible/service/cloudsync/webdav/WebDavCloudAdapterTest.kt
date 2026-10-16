package net.bible.service.cloudsync.webdav

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.mock
import kotlinx.coroutines.runBlocking
import androidx.test.core.app.ApplicationProvider
import net.bible.android.BibleApplication
import net.bible.android.TEST_SDK
import org.junit.After
import org.junit.Before
import net.bible.android.database.SyncConfiguration
import net.bible.android.database.SyncDao
import net.bible.android.database.SyncableRoomDatabase
import net.bible.service.cloudsync.SyncableDatabaseDefinition
import net.bible.android.view.activity.base.ActivityBase
import net.bible.service.cloudsync.SyncableDatabaseAccessor
import net.bible.service.cloudsync.documents.isTransientNetworkError
import net.bible.service.cloudsync.nextcloud.FOLDER_MIMETYPE
import net.bible.sharedcore.webdav.DavCertificateInfo
import net.bible.sharedcore.webdav.WebDavClient
import net.bible.sharedcore.webdav.createWebDavHttpClient
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [TEST_SDK])
class WebDavCloudAdapterTest {
    private val server = FakeDavServer(nowServer = 1_700_000_000_000L)
    private val state = object : WebDavStateStore { override var propagation = Propagation.UNKNOWN; override var certPin: CertPin? = null }
    private val ui = object : WebDavSignInUi {
        var trust = true; var asked = 0
        override suspend fun confirmCertificate(info: DavCertificateInfo): Boolean { asked++; return trust }
        override fun message(kind: WebDavMessage) = kind.name
    }
    private var deviceNow = server.nowServer

    private val appField = BibleApplication::class.java.getDeclaredField("application").apply { isAccessible = true }
    private val realApp: Any? = appField.get(null)

    /** CommonUtils.deviceIdentifier (used for the secret file name) reads the global BibleApplication; give it a stand-in over the Robolectric context. */
    @Before fun installAppStandIn() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        appField.set(null, mock<BibleApplication> {
            on { applicationContext } doAnswer { ctx }
            on { contentResolver } doAnswer { ctx.contentResolver }
        })
    }
    @After fun restoreApp() { appField.set(null, realApp) }

    private fun adapter(folder: String = "AndBible", url: String = "https://h/dav/", password: String = "pw") = WebDavCloudAdapter(
        WebDavConfig(url, "me", password, folder), state, ui,
        clientFactory = { c, _ -> WebDavClient(createWebDavHttpClient(server.engine, c.username, c.password), c.serverUrl, c.username, c.password, { deviceNow }) },
        nowMs = { deviceNow },
    )
    private suspend fun signedIn(folder: String = "AndBible") = adapter(folder).also { assertTrue(it.signIn(mock<ActivityBase>())) }
    private fun tmp(bytes: ByteArray) = File.createTempFile("webdav-test", ".gz").also { it.writeBytes(bytes); it.deleteOnExit() }

    @Test fun signIn_createsBaseFolder_andIsSignedIn() = runBlocking {
        val a = adapter()
        assertFalse(a.signedIn)
        assertTrue(a.signIn(mock<ActivityBase>()))
        assertNotNull(server.mtime("AndBible"))
        assertTrue(a.signedIn)
    }

    @Test fun signIn_nestedBaseFolder() = runBlocking {
        signedIn("a/b c")
        assertNotNull(server.mtime("a")); assertNotNull(server.mtime("a/b c"))
    }

    @Test fun signIn_wrongCredentials_throwsLocalizedMessage() = runBlocking {
        server.authRequired = true
        val a = adapter(password = "bad")
        val e = runCatching { a.signIn(mock<ActivityBase>()) }.exceptionOrNull()
        assertEquals("WRONG_CREDENTIALS", e?.message)
        assertFalse(a.signedIn)
    }

    @Test fun signIn_notACollection() = runBlocking {
        server.put("file.txt", byteArrayOf(1))
        val a = adapter(folder = "", url = "https://h/dav/file.txt")
        val e = runCatching { a.signIn(mock<ActivityBase>()) }.exceptionOrNull()
        assertEquals("NOT_A_FOLDER", e?.message)
        assertFalse(a.signedIn)
    }

    @Test fun createNewFolder_defaultsToBaseFolder() = runBlocking {
        val f = signedIn().createNewFolder("x")
        assertEquals("AndBible/x", f.id); assertEquals("AndBible", f.parentId)
        assertNotNull(server.mtime("AndBible/x"))
    }

    @Test fun uploadDownloadRoundTrip() = runBlocking {
        val a = signedIn()
        val folder = a.createNewFolder("x")
        val bytes = ByteArray(200_000) { (it % 251).toByte() }
        val up = a.upload("f.gz", tmp(bytes), folder.id)
        assertEquals("AndBible/x/f.gz", up.id)
        val out = ByteArrayOutputStream(); var last = -1L
        a.download(up.id, out) { last = it }
        assertArrayEquals(bytes, out.toByteArray())
        assertEquals(bytes.size.toLong(), last)
    }

    @Test fun listFiles_byName_withoutParents_searchesBaseFolder() = runBlocking {
        val a = signedIn()
        a.createNewFolder("a"); a.createNewFolder("b")
        assertEquals("AndBible/b", a.listFiles(name = "b").single().id)
    }

    @Test fun getFolders_returnsOnlyCollections_excludingSelf() = runBlocking {
        val a = signedIn()
        val x = a.createNewFolder("x")
        a.createNewFolder("sub", x.id)
        a.upload("f.gz", tmp(byteArrayOf(1)), x.id)
        assertEquals(listOf("AndBible/x/sub"), a.getFolders(x.id).map { it.id })
    }

    @Test fun get_missing_throwsFileNotFoundException() = runBlocking {
        val a = signedIn()
        try { a.get("AndBible/nope"); fail() } catch (e: FileNotFoundException) { }
    }

    @Test fun delete_missing_isSilent() = runBlocking { signedIn().delete("AndBible/nope") }

    @Test fun specialCharacterNames_roundTrip() = runBlocking {
        val a = signedIn()
        val folder = a.createNewFolder("Käännös +1")
        a.upload("A B+C.abmd.zip", tmp(byteArrayOf(7, 8, 9)), folder.id)
        assertEquals(listOf("Käännös +1"), a.getFolders("AndBible").map { it.name })
        val file = a.listFiles(listOf(folder.id)).single()
        assertEquals("A B+C.abmd.zip", file.name)
        val out = ByteArrayOutputStream(); a.download(file.id, out)
        assertArrayEquals(byteArrayOf(7, 8, 9), out.toByteArray())
    }

    @Test fun cloudFileTimesAreDeviceClock() = runBlocking {
        deviceNow = server.nowServer - 600_000
        val a = signedIn()
        val x = a.createNewFolder("x")
        a.upload("f.gz", tmp(byteArrayOf(1)), x.id)
        val listed = a.listFiles(listOf(x.id)).single()
        val serverMtime = server.mtime("AndBible/x/f.gz")!!
        assertEquals((serverMtime - 600_000).toDouble(), listed.createdTime.toDouble(), 1000.0)
    }

    @Test fun secretFile_knownCycle() = runBlocking {
        val a = signedIn()
        val x = a.createNewFolder("x")
        val configs = mutableMapOf<String, String>()
        val dao = mock<SyncDao> {
            on { getString(any()) } doAnswer { configs[it.arguments[0] as String] }
            on { setConfig(any<String>(), any<String>()) } doAnswer { configs[it.arguments[0] as String] = it.arguments[1] as String; Unit }
            on { removeConfig(any()) } doAnswer { configs.remove(it.arguments[0] as String); Unit }
            on { getConfig(any()) } doAnswer { k -> configs[k.arguments[0] as String]?.let { SyncConfiguration(k.arguments[0] as String, stringValue = it) } }
        }
        val db = mock<SyncableRoomDatabase> { on { syncDao() } doAnswer { dao } }
        val dbDef = SyncableDatabaseAccessor(db, { db }, { db }, File("unused"), SyncableDatabaseDefinition.BOOKMARKS, null, "dev")

        assertFalse(a.isSyncFolderKnown(dbDef, "n", x.id))
        a.makeSyncFolderKnown(dbDef, "n", x.id)
        assertTrue(a.isSyncFolderKnown(dbDef, "n", x.id))
        assertEquals(1, a.getConfigs(dbDef).size)

        val secret = configs.getValue(WEBDAV_SECRET_FILE_NAME_KEY)
        a.delete("${x.id}/$secret")
        assertFalse(a.isSyncFolderKnown(dbDef, "n", x.id))
        assertTrue(configs.isEmpty())
    }

    @Test fun transientServerError_isIOException() = runBlocking {
        val a = signedIn()
        server.statusOverride = { if (it.method.value == "PROPFIND") 503 else null }
        val e = runCatching { a.listFiles() }.exceptionOrNull()
        assertTrue("was $e", e is IOException)
        assertTrue(isTransientNetworkError(e!!))
    }
}

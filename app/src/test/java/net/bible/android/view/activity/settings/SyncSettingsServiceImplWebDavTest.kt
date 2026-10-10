package net.bible.android.view.activity.settings

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.test.DatabaseResetter
import net.bible.service.cloudsync.CloudAdapters
import net.bible.service.cloudsync.webdav.CertPin
import net.bible.service.cloudsync.webdav.Propagation
import net.bible.service.cloudsync.webdav.PrefsWebDavStateStore
import net.bible.service.common.CommonUtils
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [SyncSettingsServiceImpl] key routing when WebDAV is the selected provider: the screen's
 * `cloud_sync_*` rows read and write `webdav_sync_*`, the URL must be https, and the certificate
 * row follows the TOFU pin.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class SyncSettingsServiceImplWebDavTest {
    private val prefs get() = CommonUtils.realSharedPreferences
    private var savedAdapter: String? = null

    @Before fun setUp() {
        savedAdapter = CommonUtils.settings.getString("sync_adapter")
        prefs.edit().clear().commit()
    }

    /** `CloudAdapters.current` is global state in a single-JVM suite: put it back. */
    @After fun tearDown() {
        val s = savedAdapter
        if (s == null) CommonUtils.settings.removeString("sync_adapter") else CommonUtils.settings.setString("sync_adapter", s)
        prefs.edit().clear().commit()
        DatabaseResetter.resetDatabase()
    }

    private fun impl() = SyncSettingsServiceImpl(CoroutineScope(Job()), { error("activity not needed") })

    @Test fun webDavSelected_readsAndWritesWebDavKeys() {
        CloudAdapters.current = CloudAdapters.WEBDAV
        val service = impl()
        assertTrue(service.setText("cloud_sync_username", "w"))
        assertEquals("w", prefs.getString("webdav_sync_username", null))
        assertNull(prefs.getString("cloud_sync_username", null))
        service.refresh()
        assertEquals("w", service.snapshot.value.username)
    }

    @Test fun nextCloudSelected_keepsCloudSyncKeys() {
        CloudAdapters.current = CloudAdapters.NEXT_CLOUD
        val service = impl()
        assertTrue(service.setText("cloud_sync_username", "n"))
        assertEquals("n", prefs.getString("cloud_sync_username", null))
        assertNull(prefs.getString("webdav_sync_username", null))
        service.refresh()
        assertEquals("n", service.snapshot.value.username)
    }

    @Test fun webDav_httpRejected_httpsAccepted() {
        CloudAdapters.current = CloudAdapters.WEBDAV
        val service = impl()
        assertFalse(service.setText("cloud_sync_server_url", "http://nas/"))
        assertNull(prefs.getString("webdav_sync_server_url", null))
        assertNull(prefs.getString("cloud_sync_server_url", null))
        assertTrue(service.setText("cloud_sync_server_url", "https://nas/dav/"))
        assertEquals("https://nas/dav/", prefs.getString("webdav_sync_server_url", null))
    }

    @Test fun webDav_url_isTrimmedBeforeValidationAndStorage() {
        CloudAdapters.current = CloudAdapters.WEBDAV
        val service = impl()
        assertTrue(service.setText("cloud_sync_server_url", " https://nas/dav/ \n"))
        assertEquals("https://nas/dav/", prefs.getString("webdav_sync_server_url", null))
        assertFalse(service.setText("cloud_sync_server_url", " http://nas/ "))
        assertEquals("https://nas/dav/", prefs.getString("webdav_sync_server_url", null))
    }

    @Test fun webDav_urlOrFolderChange_resetsPropagation() {
        CloudAdapters.current = CloudAdapters.WEBDAV
        val service = impl()
        for ((key, value) in listOf("cloud_sync_folder_path" to "x", "cloud_sync_server_url" to "https://nas/dav/")) {
            PrefsWebDavStateStore(prefs).propagation = Propagation.YES
            assertTrue(service.setText(key, value))
            assertEquals(key, Propagation.UNKNOWN, PrefsWebDavStateStore(prefs).propagation)
        }
        // Credentials do not affect the collection-mtime calibration.
        PrefsWebDavStateStore(prefs).propagation = Propagation.YES
        service.setText("cloud_sync_username", "u")
        assertEquals(Propagation.YES, PrefsWebDavStateStore(prefs).propagation)
    }

    @Test fun webDav_hostChange_clearsCertPin_pathChangeKeepsIt() {
        CloudAdapters.current = CloudAdapters.WEBDAV
        val service = impl()
        assertTrue(service.setText("cloud_sync_server_url", "https://nas/dav/"))
        PrefsWebDavStateStore(prefs).certPin = CertPin("nas", "ABCDEF0123456789ABCDEF0123456789")
        assertTrue(service.setText("cloud_sync_server_url", "https://nas/other/path/"))
        assertNotNull("same host keeps the pin", PrefsWebDavStateStore(prefs).certPin)
        assertFalse(service.setText("cloud_sync_server_url", "http://elsewhere/"))
        assertNotNull("rejected URL keeps the pin", PrefsWebDavStateStore(prefs).certPin)
        assertTrue(service.setText("cloud_sync_server_url", "https://other-host/dav/"))
        assertNull("new host clears the pin", PrefsWebDavStateStore(prefs).certPin)
    }

    @Test fun nextCloud_urlChange_doesNotTouchPropagation() {
        CloudAdapters.current = CloudAdapters.NEXT_CLOUD
        PrefsWebDavStateStore(prefs).propagation = Propagation.YES
        impl().setText("cloud_sync_folder_path", "x")
        assertEquals(Propagation.YES, PrefsWebDavStateStore(prefs).propagation)
    }

    @Test fun webDav_certificateRowFields() {
        CloudAdapters.current = CloudAdapters.WEBDAV
        val service = impl()
        assertFalse(service.snapshot.value.certificateVisible)
        PrefsWebDavStateStore(prefs).certPin = CertPin("nas", "ABCDEF0123456789ABCDEF0123456789")
        service.refresh()
        val snap = service.snapshot.value
        assertTrue(snap.certificateVisible)
        assertTrue(snap.certificateSummary, snap.certificateSummary.startsWith("AB:CD"))
        service.forgetCertificate()
        assertNull(PrefsWebDavStateStore(prefs).certPin)
        service.refresh()
        assertFalse(service.snapshot.value.certificateVisible)
    }

    @Test fun webDav_hintAndHttpsOnly() {
        CloudAdapters.current = CloudAdapters.WEBDAV
        val service = impl()
        service.refresh()
        assertNotNull(service.snapshot.value.serverUrlHint)
        assertTrue(service.snapshot.value.httpsOnly)
        CloudAdapters.current = CloudAdapters.NEXT_CLOUD
        service.refresh()
        assertNull(service.snapshot.value.serverUrlHint)
        assertFalse(service.snapshot.value.httpsOnly)
    }
}

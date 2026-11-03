package net.bible.service.cloudsync.ios

import net.bible.service.cloudsync.webdav.CertPin
import net.bible.service.cloudsync.webdav.Propagation
import net.bible.sharedcore.platform.secrets.SecretStore
import platform.Foundation.NSUUID
import platform.Foundation.NSUserDefaults
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IosSyncSettingsPlatformTest {
    private val suite = "i2-test-${NSUUID().UUIDString}"
    private val defaults = NSUserDefaults(suiteName = suite)
    private val prefs = IosDevicePrefs(defaults)
    private val secrets = FakeSecrets()
    private val platform = IosSyncSettingsPlatform(prefs, secrets) { "hint" }

    @AfterTest
    fun cleanUp() {
        defaults.removePersistentDomainForName(suite)
    }

    private class FakeSecrets : SecretStore {
        val map = mutableMapOf<String, String>()
        override fun get(key: String) = map[key]
        override fun put(key: String, value: String) { map[key] = value }
        override fun remove(key: String) { map.remove(key) }
    }

    @Test
    fun plainCredentialsLiveInDefaultsUnderAndroidKeys() {
        platform.setCredential("cloud_sync_username", "anna")
        assertEquals("anna", defaults.stringForKey("cloud_sync_username"))
        assertEquals("anna", platform.credential("cloud_sync_username"))
        assertEquals("", platform.credential("cloud_sync_missing"))
    }

    @Test
    fun passwordsLiveInSecretStore() {
        platform.setCredential("webdav_sync_password", "pw")
        assertEquals("pw", secrets.map["webdav_sync_password"])
        assertNull(defaults.stringForKey("webdav_sync_password"))
        assertEquals("pw", platform.credential("webdav_sync_password"))
    }

    @Test
    fun webDavStateRoundTrips() {
        val store = platform.webDavState
        assertEquals(Propagation.UNKNOWN, store.propagation)
        store.propagation = Propagation.NO
        store.certPin = CertPin("h", "AA")
        val again = IosWebDavStateStore(IosDevicePrefs(defaults))
        assertEquals(Propagation.NO, again.propagation)
        assertEquals(CertPin("h", "AA"), again.certPin)
        again.certPin = null
        assertNull(defaults.stringForKey("webdav_sync_cert_pin"))
        assertNull(IosWebDavStateStore(prefs).certPin)
    }

    @Test
    fun isValidUrl() {
        assertTrue(platform.isValidUrl("https://a.b/x"))
        assertTrue(platform.isValidUrl("http://localhost:8080"))
        assertFalse(platform.isValidUrl(""))
        assertFalse(platform.isValidUrl("nas.local"))
        assertFalse(platform.isValidUrl("ftp://a.b"))
        assertFalse(platform.isValidUrl("https://"))
    }

    @Test
    fun formatting() {
        assertTrue(Regex("""\d{2}-\d{2}-\d{4} \d{2}:\d{2}:\d{2}""").matches(platform.formatSyncTime(0L)))
        val d = platform.formatDecimal2(1.5)
        assertEquals(2, d.takeLastWhile { it.isDigit() }.length)
        val size = platform.formatFileSize(1_500_000)
        assertTrue(size.isNotEmpty() && size.contains("MB"), size)
        assertEquals("hint", platform.webDavServerUrlHint)
    }
}

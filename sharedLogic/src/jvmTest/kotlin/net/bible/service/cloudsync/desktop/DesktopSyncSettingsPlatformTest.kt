/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */
package net.bible.service.cloudsync.desktop

import net.bible.service.cloudsync.webdav.CertPin
import net.bible.service.cloudsync.webdav.Propagation
import net.bible.sharedcore.platform.secrets.SecretStore
import net.bible.sharedcore.settings.DeviceLocalPrefs
import java.util.Locale
import java.util.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopSyncSettingsPlatformTest {
    private class MapPrefs : DeviceLocalPrefs {
        val map = HashMap<String, String>()
        override fun getString(key: String, default: String?) = map[key] ?: default
        override fun getBoolean(key: String, default: Boolean) = map[key]?.toBoolean() ?: default
        override fun setString(key: String, value: String) { map[key] = value }
        override fun setBoolean(key: String, value: Boolean) { map[key] = value.toString() }
        override fun remove(key: String) { map.remove(key) }
    }

    private class MapSecrets : SecretStore {
        val map = HashMap<String, String>()
        override fun get(key: String) = map[key]
        override fun put(key: String, value: String) { map[key] = value }
        override fun remove(key: String) { map.remove(key) }
    }

    private val prefs = MapPrefs()
    private val secrets = MapSecrets()
    private val platform = DesktopSyncSettingsPlatform(prefs, secrets) { "https://cloud.example.org" }

    private fun <T> withLocale(locale: Locale, zone: String? = null, block: () -> T): T {
        val oldLocale = Locale.getDefault(); val oldZone = TimeZone.getDefault()
        try {
            Locale.setDefault(locale); if (zone != null) TimeZone.setDefault(TimeZone.getTimeZone(zone))
            return block()
        } finally { Locale.setDefault(oldLocale); TimeZone.setDefault(oldZone) }
    }

    @Test fun syncSettingsFormatsBytesAndDates() = withLocale(Locale.US, "UTC") {
        assertEquals("0 bytes", platform.formatFileSize(0))
        assertEquals("999 bytes", platform.formatFileSize(999))
        assertEquals("1 KB", platform.formatFileSize(1_000))
        assertEquals("1.5 MB", platform.formatFileSize(1_500_000))
        assertEquals("12 MB", platform.formatFileSize(12_000_000))
        assertEquals("2.3 GB", platform.formatFileSize(2_340_000_000))
        // 2026-10-30 08:09:10 UTC
        assertEquals("30-10-2026 08:09:10", platform.formatSyncTime(1_793_347_750_000))
        assertEquals("1.50", platform.formatDecimal2(1.5))
        assertEquals("3.14", platform.formatDecimal2(3.14159))
    }

    @Test fun decimalsFollowTheLocale() = withLocale(Locale.forLanguageTag("fi-FI")) {
        assertEquals("1,50", platform.formatDecimal2(1.5))
        assertEquals("1,5 MB", platform.formatFileSize(1_500_000))
    }

    @Test fun passwordsGoToTheSecretStoreAndTheRestToThePrefs() {
        platform.setCredential("cloud_sync_password", "pw1")
        platform.setCredential("webdav_sync_password", "pw2")
        platform.setCredential("cloud_sync_username", "me")
        assertEquals(mapOf("cloud_sync_password" to "pw1", "webdav_sync_password" to "pw2"), secrets.map)
        assertEquals(mapOf("cloud_sync_username" to "me"), prefs.map, "no password in the preferences file")
        assertEquals("pw1", platform.credential("cloud_sync_password"))
        assertEquals("me", platform.credential("cloud_sync_username"))
        assertEquals("", platform.credential("webdav_sync_server_url"))
    }

    @Test fun urlValidationAndPlainHttpRule() {
        assertTrue(platform.isValidUrl("https://cloud.example.org/remote.php"))
        assertTrue(platform.isValidUrl("http://localhost:8080"))
        assertFalse(platform.isValidUrl("ftp://example.org"))
        assertFalse(platform.isValidUrl("https://"))
        assertFalse(platform.isValidUrl("not a url"))
        assertTrue(platform.plainHttpAllowed("https://cloud.example.org"))
        assertTrue(platform.plainHttpAllowed("http://127.0.0.1:8080"))
        assertFalse(platform.plainHttpAllowed("http://cloud.example.org"))
        assertEquals("https://cloud.example.org", platform.webDavServerUrlHint)
    }

    @Test fun webDavStateRoundTripsThroughThePrefs() {
        val state = platform.webDavState
        assertEquals(Propagation.UNKNOWN, state.propagation)
        assertNull(state.certPin)
        state.propagation = Propagation.YES
        val pin = CertPin("example.org", "ab".repeat(32))
        state.certPin = pin
        val again = DesktopWebDavStateStore(prefs)
        assertEquals(state.propagation, again.propagation)
        assertEquals(pin, again.certPin)
        again.certPin = null
        assertNull(DesktopWebDavStateStore(prefs).certPin)
    }
}

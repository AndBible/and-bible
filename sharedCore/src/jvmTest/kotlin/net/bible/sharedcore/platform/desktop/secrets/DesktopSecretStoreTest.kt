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
package net.bible.sharedcore.platform.desktop.secrets

import net.bible.sharedcore.settings.DeviceLocalPrefs
import com.github.javakeyring.PasswordAccessException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopSecretStoreTest {
    private class FakePrefs : DeviceLocalPrefs {
        val map = HashMap<String, String>()
        override fun getString(key: String, default: String?) = map[key] ?: default
        override fun getBoolean(key: String, default: Boolean) = map[key]?.toBoolean() ?: default
        override fun setString(key: String, value: String) { map[key] = value }
        override fun setBoolean(key: String, value: Boolean) { map[key] = value.toString() }
        override fun remove(key: String) { map.remove(key) }
    }

    private class MapBackend : KeyringBackend {
        val map = HashMap<Pair<String, String>, String>()
        override fun get(service: String, account: String) = map[service to account]
        override fun set(service: String, account: String, value: String) { map[service to account] = value }
        override fun delete(service: String, account: String) { map.remove(service to account) }
    }

    private class ThrowingBackend : KeyringBackend {
        var calls = 0
        override fun get(service: String, account: String): String? { calls++; error("no keyring") }
        override fun set(service: String, account: String, value: String) { calls++; error("no keyring") }
        override fun delete(service: String, account: String) { calls++; error("no keyring") }
    }

    @Test
    fun roundTrip() {
        val backend = MapBackend()
        val store = DesktopSecretStore(backend, FakePrefs())
        store.put("webdav_password", "s3cret")
        assertEquals("s3cret", store.get("webdav_password"))
        assertEquals("s3cret", backend.map["AndBible" to "webdav_password"])
    }

    @Test
    fun deleteRemoves() {
        val store = DesktopSecretStore(MapBackend(), FakePrefs())
        store.put("k", "v")
        store.remove("k")
        assertNull(store.get("k"))
    }

    @Test
    fun fallsBackToSessionMemory() {
        val backend = ThrowingBackend()
        val prefs = FakePrefs()
        run {
            val store = DesktopSecretStore(backend, prefs)
            store.put("pw", "secret-value")
            assertEquals("secret-value", store.get("pw"))
            store.remove("pw")
            assertNull(store.get("pw"))
            store.put("pw", "again")
            assertEquals("again", store.get("pw"))
            assertEquals(1, backend.calls, "backend is abandoned after the first failure")
            assertTrue(prefs.map.values.none { it.contains("secret") || it == "again" }, "secret must not reach prefs")
        }
    }

    @Test
    fun deviceIdStableAcrossRestartsWithoutKeyring() {
        val prefs = FakePrefs()
        val first = DesktopSecretStore(ThrowingBackend(), prefs)
        assertNull(first.get("device_identifier"))
        first.put("device_identifier", "dev-123")
        val second = DesktopSecretStore(ThrowingBackend(), prefs)
        assertEquals("dev-123", second.get("device_identifier"))
        assertEquals("dev-123", prefs.map["device_identifier"])
    }

    @Test
    fun deviceIdNeverGoesToKeyring() {
        val backend = MapBackend()
        val store = DesktopSecretStore(backend, FakePrefs())
        store.put("device_identifier", "x")
        assertTrue(backend.map.isEmpty())
        store.remove("device_identifier")
        assertNull(store.get("device_identifier"))
    }

    @Test
    fun iosSemanticsKept() {
        val store = DesktopSecretStore(MapBackend(), FakePrefs())
        assertNull(store.get("absent"))
        store.remove("absent") // no-op, no throw
        store.put("k", "one")
        store.put("k", "two")
        assertEquals("two", store.get("k"))
        val unicode = "pässwörd-日本語-😀"
        store.put("u", unicode)
        assertEquals(unicode, store.get("u"))
        val long = "x".repeat(10_000)
        store.put("l", long)
        assertEquals(long, store.get("l"))
        store.put("empty", "")
        assertEquals("", store.get("empty"))
    }

    @Test
    fun factoryFailureFallsBack() {
        val store = DesktopSecretStore.create(FakePrefs()) { error("cannot construct") }
        store.put("k", "v")
        assertEquals("v", store.get("k"))
    }

    @Test
    fun notFoundDetection() {
        assertTrue(JavaKeyringBackend.isNotFound(PasswordAccessException("No stored credentials match AndBible account: k")))
        assertTrue(JavaKeyringBackend.isNotFound(PasswordAccessException("lookup failed", RuntimeException("Item not found"))))
        assertTrue(!JavaKeyringBackend.isNotFound(PasswordAccessException("Cannot connect to the secret service")))
    }
}

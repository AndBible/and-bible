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

import com.github.javakeyring.Keyring
import com.github.javakeyring.PasswordAccessException
import net.bible.sharedcore.log.Log
import net.bible.sharedcore.platform.secrets.SecretStore
import net.bible.sharedcore.platform.secrets.SecretStoreException
import net.bible.sharedcore.settings.DeviceLocalPrefs

/** Minimal credential-store API the desktop [DesktopSecretStore] needs; [get] returns null when the entry is absent. */
interface KeyringBackend {
    fun get(service: String, account: String): String?
    fun set(service: String, account: String, value: String)
    /** Deleting an absent entry is a no-op. */
    fun delete(service: String, account: String)
}

/** The OS credential store (Secret Service, macOS Keychain, Windows Credential Manager) through java-keyring. */
class JavaKeyringBackend : KeyringBackend {
    private val keyring: Keyring = Keyring.create()

    override fun get(service: String, account: String): String? = try {
        keyring.getPassword(service, account)
    } catch (e: PasswordAccessException) {
        if (isNotFound(e)) null else throw e
    }

    override fun set(service: String, account: String, value: String) = keyring.setPassword(service, account, value)

    override fun delete(service: String, account: String) {
        try {
            keyring.deletePassword(service, account)
        } catch (e: PasswordAccessException) {
            if (!isNotFound(e)) throw e
        }
    }

    private fun isNotFound(e: PasswordAccessException): Boolean {
        val text = generateSequence<Throwable>(e) { it.cause }.mapNotNull { it.message }.joinToString(" ").lowercase()
        return "no stored credentials" in text || "not found" in text || "no such" in text || "could not be found" in text || "not exist" in text
    }
}

/** In-memory fallback used when no OS keyring is usable. Nothing is persisted. */
class SessionMemoryBackend : KeyringBackend {
    private val map = HashMap<Pair<String, String>, String>()
    override fun get(service: String, account: String): String? = synchronized(map) { map[service to account] }
    override fun set(service: String, account: String, value: String) { synchronized(map) { map[service to account] = value } }
    override fun delete(service: String, account: String) { synchronized(map) { map.remove(service to account) } }
}

/**
 * Desktop [SecretStore] over a [KeyringBackend] (service "AndBible", account = key).
 *
 * If the backend throws on any use, this logs once and switches permanently to a [SessionMemoryBackend]: secrets then
 * last for the session only and are never written to a plain file. The non-secret [DEVICE_ID_KEY] is always kept in
 * [prefs] (never in the keyring) so the device id stays stable across restarts even without a keyring.
 */
class DesktopSecretStore(backend: KeyringBackend, private val prefs: DeviceLocalPrefs) : SecretStore {
    @Volatile private var backend: KeyringBackend = backend

    override fun get(key: String): String? =
        if (key == DEVICE_ID_KEY) prefs.getString(key, null) else guarded { it.get(SERVICE, key) }

    override fun put(key: String, value: String) {
        if (key == DEVICE_ID_KEY) prefs.setString(key, value) else guarded { it.set(SERVICE, key, value) }
    }

    override fun remove(key: String) {
        if (key == DEVICE_ID_KEY) prefs.remove(key) else guarded { it.delete(SERVICE, key) }
    }

    @Synchronized
    private fun <T> guarded(block: (KeyringBackend) -> T): T = try {
        block(backend)
    } catch (e: Exception) {
        if (backend is SessionMemoryBackend) throw SecretStoreException("Session secret store failed", e)
        Log.w(TAG, "OS keyring unavailable; keeping secrets in memory for this session only", e)
        backend = SessionMemoryBackend()
        block(backend)
    }

    companion object {
        const val SERVICE = "AndBible"
        const val DEVICE_ID_KEY = "device_identifier"
        private const val TAG = "DesktopSecretStore"

        /** Builds a store over the OS keyring, or over session memory when the keyring cannot be opened. */
        fun create(prefs: DeviceLocalPrefs, factory: () -> KeyringBackend = { JavaKeyringBackend() }): DesktopSecretStore {
            val backend = try {
                factory()
            } catch (e: Throwable) {
                Log.w(TAG, "OS keyring unavailable; keeping secrets in memory for this session only", e)
                SessionMemoryBackend()
            }
            return DesktopSecretStore(backend, prefs)
        }
    }
}

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
import net.bible.service.cloudsync.webdav.WebDavPrefKeys
import net.bible.service.cloudsync.webdav.WebDavStateStore
import net.bible.sharedcore.settings.DeviceLocalPrefs

/** The credential keys whose values live in the secret store, never in the device preferences file (same keys as iOS/Android). */
val DESKTOP_SECRET_CREDENTIAL_KEYS = setOf("cloud_sync_password", "webdav_sync_password")

/** The WebDAV adapter's persisted calibration and certificate pin, in the device-local preferences (same keys and encodings as iOS). */
class DesktopWebDavStateStore(private val prefs: DeviceLocalPrefs) : WebDavStateStore {
    override var propagation: Propagation
        get() = prefs.getString(WebDavPrefKeys.PROPAGATION, null)
            ?.let { runCatching { Propagation.valueOf(it) }.getOrNull() } ?: Propagation.UNKNOWN
        set(v) = prefs.setString(WebDavPrefKeys.PROPAGATION, v.name)
    override var certPin: CertPin?
        get() = CertPin.decode(prefs.getString(WebDavPrefKeys.CERT_PIN, null))
        set(v) {
            val encoded = v?.encode()
            if (encoded == null) prefs.remove(WebDavPrefKeys.CERT_PIN) else prefs.setString(WebDavPrefKeys.CERT_PIN, encoded)
        }
}

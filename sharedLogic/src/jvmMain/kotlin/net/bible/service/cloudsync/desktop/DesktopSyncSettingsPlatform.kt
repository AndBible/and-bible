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

import net.bible.service.cloudsync.SyncSettingsPlatform
import net.bible.service.cloudsync.webdav.WebDavStateStore
import net.bible.sharedcore.platform.secrets.SecretStore
import net.bible.sharedcore.settings.DeviceLocalPrefs
import java.net.URI
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The desktop sync settings platform: plain credentials in the device preferences under Android's key names, the two
 * password keys in the [secrets] store, locale-aware formatting through java.text / java.time.
 */
class DesktopSyncSettingsPlatform(
    private val prefs: DeviceLocalPrefs,
    private val secrets: SecretStore,
    private val serverUrlHint: () -> String,
) : SyncSettingsPlatform {
    override fun credential(key: String): String =
        (if (key in DESKTOP_SECRET_CREDENTIAL_KEYS) secrets.get(key) else prefs.getString(key, null)) ?: ""

    override fun setCredential(key: String, value: String) {
        if (key in DESKTOP_SECRET_CREDENTIAL_KEYS) secrets.put(key, value) else prefs.setString(key, value)
    }

    override fun isValidUrl(value: String): Boolean {
        val uri = try { URI(value) } catch (_: Exception) { return false }
        val scheme = uri.scheme?.lowercase()
        return (scheme == "http" || scheme == "https") && !uri.host.isNullOrEmpty()
    }

    override fun plainHttpAllowed(url: String): Boolean = desktopPlainHttpAllowed(url)

    /** File-style sizes with decimal units, like NSByteCountFormatter: "999 bytes", "12 KB", "1.5 MB". */
    override fun formatFileSize(bytes: Long): String {
        if (bytes < 1000) return NumberFormat.getIntegerInstance().format(bytes) + " bytes"
        val units = listOf("KB", "MB", "GB", "TB", "PB")
        var value = bytes / 1000.0
        var unit = 0
        while (value >= 1000 && unit < units.lastIndex) { value /= 1000; unit++ }
        val format = DecimalFormat(if (unit == 0) "0" else "0.#", DecimalFormatSymbols.getInstance())
        return format.format(value) + " " + units[unit]
    }

    override fun formatSyncTime(timestamp: Long): String =
        DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss", Locale.getDefault())
            .withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(timestamp))

    override fun formatDecimal2(value: Double): String =
        NumberFormat.getNumberInstance(Locale.getDefault()).apply { minimumFractionDigits = 2; maximumFractionDigits = 2 }.format(value)

    override val webDavState: WebDavStateStore get() = DesktopWebDavStateStore(prefs)
    override val webDavServerUrlHint: String get() = serverUrlHint()
}

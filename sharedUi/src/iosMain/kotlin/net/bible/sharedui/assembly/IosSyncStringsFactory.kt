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
package net.bible.sharedui.assembly

import net.bible.service.cloudsync.ios.IosSyncStrings
import net.bible.service.cloudsync.webdav.WebDavMessage
import net.bible.sharedui.strings.keyed.StringTable

/**
 * The texts of the iOS sync stack, from existing string keys (the same ones Android's adapter factory and WebDAV
 * sign-in UI read). `ICLOUD_DRIVE` has no key yet: its name stays a pending English string (Stream X's row).
 */
internal fun iosSyncStrings(strings: StringTable): IosSyncStrings = IosSyncStrings(
    adapterDisplayName = { id ->
        when (id) {
            "ICLOUD_DRIVE" -> "iCloud Drive" // pending: no string key yet
            "NEXT_CLOUD" -> strings.text("adapters_next_cloud")
            "WEBDAV" -> strings.text("adapters_webdav")
            else -> error("Unknown cloud adapter: $id")
        }
    },
    webDavServerUrlHint = { strings.text("webdav_server_url_hint") },
    syncError = { strings.text("sync_error") },
    webDavMessage = { message ->
        strings.text(
            when (message) {
                WebDavMessage.WRONG_CREDENTIALS -> "webdav_wrong_credentials"
                WebDavMessage.NOT_A_FOLDER -> "webdav_not_a_folder"
                WebDavMessage.CERTIFICATE_CHANGED -> "webdav_certificate_changed"
                WebDavMessage.STORAGE_FULL -> "webdav_storage_full"
            },
        )
    },
    ok = { strings.text("okay") },
)

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

import net.bible.service.cloudsync.CloudAdapter
import net.bible.service.cloudsync.CloudAdapterFactory
import net.bible.service.cloudsync.host.HostCloudAdapterFactory

/**
 * The desktop cloud back ends: Nextcloud and generic WebDAV only (no iCloud, no Google Drive). Everything else is the hoisted
 * [HostCloudAdapterFactory]; an id outside [available] is refused here so the factory's iCloud branch is never reached.
 */
class DesktopCloudAdapterFactory(private val inner: HostCloudAdapterFactory) : CloudAdapterFactory by inner {
    override val available: List<String> = listOf("NEXT_CLOUD", "WEBDAV")

    override fun create(id: String): CloudAdapter = inner.create(checked(id))
    override fun createForBackground(id: String): CloudAdapter = inner.createForBackground(checked(id))

    private fun checked(id: String): String {
        check(id in available) { "Cloud adapter $id is not available on desktop" }
        return id
    }
}

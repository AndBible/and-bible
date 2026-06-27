/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with AndBible. If not, see <http://www.gnu.org/licenses/>.
 */

package net.bible.service.cloudsync.documents

import net.bible.service.common.CommonUtils

object DocumentSyncSettings {
    private const val ENABLED = "sync_enable_documents"
    private const val WIFI_ONLY = "sync_documents_wifi_only"
    private const val AUTO_DOWNLOAD = "sync_documents_auto_download"
    private const val AUTO_UPLOAD = "sync_documents_auto_upload"
    private const val AUTO_DELETE = "sync_documents_auto_delete"
    private const val SYNC_NOW_DOWNLOAD = "sync_documents_sync_now_download"
    private const val SYNC_NOW_UPLOAD = "sync_documents_sync_now_upload"
    private const val SYNC_NOW_DELETE = "sync_documents_sync_now_delete"
    private const val BLOCKED = "sync_documents_blocked"
    private const val TS_PREFIX = "doc_sync_ts_"

    var enabled: Boolean
        get() = CommonUtils.settings.getBoolean(ENABLED, false)
        set(value) = CommonUtils.settings.setBoolean(ENABLED, value)

    var wifiOnly: Boolean
        get() = CommonUtils.settings.getBoolean(WIFI_ONLY, true)
        set(value) = CommonUtils.settings.setBoolean(WIFI_ONLY, value)

    // Per-device automatic-operation toggles (default on = current behaviour). These gate the
    // automatic sync cycle and install-time auto-upload only; manual actions ignore them.
    var autoDownload: Boolean
        get() = CommonUtils.settings.getBoolean(AUTO_DOWNLOAD, true)
        set(value) = CommonUtils.settings.setBoolean(AUTO_DOWNLOAD, value)

    var autoUpload: Boolean
        get() = CommonUtils.settings.getBoolean(AUTO_UPLOAD, true)
        set(value) = CommonUtils.settings.setBoolean(AUTO_UPLOAD, value)

    var autoDelete: Boolean
        get() = CommonUtils.settings.getBoolean(AUTO_DELETE, true)
        set(value) = CommonUtils.settings.setBoolean(AUTO_DELETE, value)

    // Remembered checkbox state for the manual "Sync now" operation picker (default all on).
    var syncNowDownload: Boolean
        get() = CommonUtils.settings.getBoolean(SYNC_NOW_DOWNLOAD, true)
        set(value) = CommonUtils.settings.setBoolean(SYNC_NOW_DOWNLOAD, value)

    var syncNowUpload: Boolean
        get() = CommonUtils.settings.getBoolean(SYNC_NOW_UPLOAD, true)
        set(value) = CommonUtils.settings.setBoolean(SYNC_NOW_UPLOAD, value)

    var syncNowDelete: Boolean
        get() = CommonUtils.settings.getBoolean(SYNC_NOW_DELETE, true)
        set(value) = CommonUtils.settings.setBoolean(SYNC_NOW_DELETE, value)

    val blockList: DocumentBlockList = DocumentBlockList(object : StringSetStore {
        override fun get(): Set<String> =
            CommonUtils.settings.getString(BLOCKED, "")
                ?.split("\n")?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()
        override fun set(value: Set<String>) =
            CommonUtils.settings.setString(BLOCKED, value.joinToString("\n"))
    })

    fun syncTimestamp(initials: String): Long? =
        CommonUtils.settings.getLong("$TS_PREFIX$initials", -1L).takeIf { it >= 0 }

    fun setSyncTimestamp(initials: String, ts: Long) =
        CommonUtils.settings.setLong("$TS_PREFIX$initials", ts)

    val isAutoTransferAllowed: Boolean
        get() = !wifiOnly || !CommonUtils.isMeteredNetwork
}

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

import net.bible.android.database.CloudDocumentSyncTimestamp
import net.bible.android.database.CloudListingState
import net.bible.android.database.DocumentSyncPreferences
import net.bible.service.common.CommonUtils
import net.bible.service.db.DatabaseContainer

object DocumentSyncSettings {
    private val prefsDao get() = DatabaseContainer.instance.documentSyncDb.documentSyncPreferencesDao()
    private val listingDao get() = DatabaseContainer.instance.documentSyncDb.cloudListingStateDao()
    private val tsDao get() = DatabaseContainer.instance.documentSyncDb.cloudDocumentSyncTimestampDao()

    private fun prefs(): DocumentSyncPreferences = prefsDao.get() ?: DocumentSyncPreferences()
    private fun update(transform: DocumentSyncPreferences.() -> DocumentSyncPreferences) =
        prefsDao.set(prefs().transform())

    var enabled: Boolean
        get() = prefs().enabled
        set(value) = update { copy(enabled = value) }

    var wifiOnly: Boolean
        get() = prefs().wifiOnly
        set(value) = update { copy(wifiOnly = value) }

    var showRemovedDocuments: Boolean
        get() = prefs().showRemovedDocuments
        set(value) = update { copy(showRemovedDocuments = value) }

    var autoDownload: Boolean
        get() = prefs().autoDownload
        set(value) = update { copy(autoDownload = value) }

    var autoUpload: Boolean
        get() = prefs().autoUpload
        set(value) = update { copy(autoUpload = value) }

    var autoDelete: Boolean
        get() = prefs().autoDelete
        set(value) = update { copy(autoDelete = value) }

    var syncNowDownload: Boolean
        get() = prefs().syncNowDownload
        set(value) = update { copy(syncNowDownload = value) }

    var syncNowUpload: Boolean
        get() = prefs().syncNowUpload
        set(value) = update { copy(syncNowUpload = value) }

    var syncNowDelete: Boolean
        get() = prefs().syncNowDelete
        set(value) = update { copy(syncNowDelete = value) }

    val blockList: DocumentBlockList = DocumentBlockList(object : StringSetStore {
        override fun get(): Set<String> = prefs().blockList
        override fun set(value: Set<String>) = update { copy(blockList = value) }
    })

    /** Incremental-listing watermark: max observed cloud meta createdTime. 0 ⇒ cold start. */
    var watermark: Long
        get() = (listingDao.get() ?: CloudListingState()).watermark
        set(value) = listingDao.set(CloudListingState(watermark = value))

    fun syncTimestamp(initials: String): Long? = tsDao.get(initials)

    fun setSyncTimestamp(initials: String, ts: Long) =
        tsDao.set(CloudDocumentSyncTimestamp(initials, ts))

    val isAutoTransferAllowed: Boolean
        get() = !wifiOnly || !CommonUtils.isMeteredNetwork
}

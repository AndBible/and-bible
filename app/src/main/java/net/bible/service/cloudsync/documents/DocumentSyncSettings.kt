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
import net.bible.android.database.DocumentSyncPreferencesDao
import net.bible.android.database.CloudListingStateDao
import net.bible.android.database.CloudDocumentSyncTimestampDao
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.bible.sharedcore.platform.AppEnvironment
import net.bible.sharedcore.platform.OrderedLauncher
import net.bible.sharedcore.log.Log

class DocumentSyncSettings(
    private val prefsDao: () -> DocumentSyncPreferencesDao,
    private val listingDao: () -> CloudListingStateDao,
    private val tsDao: () -> CloudDocumentSyncTimestampDao,
    private val launcher: OrderedLauncher,
    private val env: AppEnvironment,
) {
    private val state = MutableStateFlow<DocumentSyncPreferences?>(null)
    private val dbMutex = Mutex()
    private val stateLock = SynchronizedObject()

    /** Load once; publication cannot overwrite a concurrent release-mode setter's snapshot. */
    suspend fun ensureLoaded() {
        if (state.value != null) return
        dbMutex.withLock {
            if (state.value == null) {
                val loaded = prefsDao().get() ?: DocumentSyncPreferences()
                synchronized(stateLock) { if (state.value == null) state.value = loaded } // L1-common-ok: atomicfu lock
            }
        }
    }

    private fun prefs(): DocumentSyncPreferences = state.value ?: run {
        val message = "DocumentSyncSettings read before ensureLoaded()"
        if (env.isDebugMode) throw IllegalStateException(message)
        Log.e("DocumentSyncSettings", message)
        DocumentSyncPreferences()
    }

    private fun update(transform: DocumentSyncPreferences.() -> DocumentSyncPreferences) {
        synchronized(stateLock) { // L1-common-ok: atomicfu lock
            val snapshot = prefs().transform()
            state.value = snapshot
            launcher.launch("document-sync-settings") {
                dbMutex.withLock { prefsDao().set(snapshot) }
            }
        }
    }

    /** Sign-out queues behind all preference writes, so no old snapshot can resurrect the row. */
    suspend fun clearPreferences() {
        val clear = synchronized(stateLock) { // L1-common-ok: atomicfu lock
            state.value = DocumentSyncPreferences()
            launcher.launch("document-sync-settings") {
                dbMutex.withLock { prefsDao().clear() }
            }
        }
        clear.join()
    }

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
    suspend fun watermark(): Long = (listingDao().get() ?: CloudListingState()).watermark

    suspend fun setWatermark(value: Long) = listingDao().set(CloudListingState(watermark = value))

    suspend fun syncTimestamp(initials: String): Long? = tsDao().get(initials)

    suspend fun setSyncTimestamp(initials: String, ts: Long) =
        tsDao().set(CloudDocumentSyncTimestamp(initials, ts))

    val isAutoTransferAllowed: Boolean
        get() = !wifiOnly || !env.isMeteredNetwork
}

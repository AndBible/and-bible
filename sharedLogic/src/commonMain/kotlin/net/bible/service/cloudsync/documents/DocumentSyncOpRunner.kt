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
package net.bible.service.cloudsync.documents

import kotlinx.coroutines.CancellationException
import net.bible.service.cloudsync.CloudSyncUserFacingException
import net.bible.sharedcore.log.Log
import org.crosswire.jsword.book.Books

private const val TAG = "DocumentSyncService"

/** The transfer actions a [DocumentSyncOp] maps to; [DocumentSync.asActions] is the production binding. */
interface DocumentSyncActions {
    suspend fun push(initials: String)
    suspend fun download(initials: String, onProgress: (Long, Long) -> Unit)
    suspend fun remove(initials: String)
    suspend fun purge(initials: String)
    suspend fun uninstall(initials: String)
}

/** Binds [DocumentSyncActions] to this [DocumentSync]. Push does nothing when the book is not installed. */
fun DocumentSync.asActions(): DocumentSyncActions {
    val sync = this
    return object : DocumentSyncActions {
        override suspend fun push(initials: String) {
            Books.installed().getBook(initials)?.let { sync.pushDocument(it) }
        }
        override suspend fun download(initials: String, onProgress: (Long, Long) -> Unit) =
            sync.downloadAndInstall(initials, onProgress)
        override suspend fun remove(initials: String) = sync.removeFromCloud(initials)
        override suspend fun purge(initials: String) = sync.purgeTombstone(initials)
        override suspend fun uninstall(initials: String) = sync.uninstallLocal(initials)
    }
}

/** Result of one [runDocumentSyncOp]; the platform maps it to notifications and queue handling. */
sealed interface DocumentSyncOpOutcome {
    data object Done : DocumentSyncOpOutcome
    /** Transient network failure; the op is retried on the next sync, no user notification. */
    data object RetryLater : DocumentSyncOpOutcome
    /** Every remaining op would hit the same wall: notify once, reconnect if needed, drop the queue. */
    data class UserFacing(val message: String, val requiresReconnect: Boolean) : DocumentSyncOpOutcome
    /** Unexpected failure: show the generic error notification. */
    data object Failed : DocumentSyncOpOutcome
}

/**
 * Runs one [op] through [actions] and classifies the result, logging failures. [CancellationException]
 * is rethrown.
 */
suspend fun runDocumentSyncOp(
    op: DocumentSyncOp,
    actions: DocumentSyncActions,
    onDownloadProgress: (Long, Long) -> Unit,
): DocumentSyncOpOutcome {
    try {
        when (op) {
            is DocumentSyncOp.Push -> actions.push(op.initials)
            is DocumentSyncOp.Download -> actions.download(op.initials, onDownloadProgress)
            is DocumentSyncOp.Remove -> actions.remove(op.initials)
            is DocumentSyncOp.Purge -> actions.purge(op.initials)
            is DocumentSyncOp.Uninstall -> actions.uninstall(op.initials)
        }
        return DocumentSyncOpOutcome.Done
    } catch (e: CancellationException) {
        throw e
    } catch (e: CloudSyncUserFacingException) {
        Log.w(TAG, "Document sync op failed (user-facing); abandoning queue: ${op.initials}", e)
        return DocumentSyncOpOutcome.UserFacing(e.message ?: "", e.requiresReconnect)
    } catch (e: Exception) {
        if (isTransientNetworkError(e)) {
            Log.w(TAG, "Document sync op failed (network); will retry next sync: ${op.initials}", e)
            return DocumentSyncOpOutcome.RetryLater
        }
        Log.e(TAG, "Document sync op failed: ${op.initials}", e)
        return DocumentSyncOpOutcome.Failed
    }
}

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

import net.bible.service.download.isPseudoBook
import net.bible.service.sword.mydocument.isMyDocument
import org.crosswire.jsword.book.Book
import java.io.IOException

/**
 * Whether a book can participate in document sync at all.
 *
 * Document sync transfers modules as SWORD archives, so it only applies to books backed by real
 * files on disk. Two kinds of registered book are not:
 *
 *  - **Pseudo books** — placeholder entries (e.g. "no document installed") with no content.
 *  - **MyDocuments** — user/AI documents stored in [net.bible.android.database.mydocument.MyDocumentDatabase]
 *    and registered into JSword with byte-array metadata. They are already synced as part of the
 *    database sync, and having no `configFile` they cannot be packaged as a module archive at all
 *    (OSTicket 3392: an auto-push of `AIDocuments` / `MyDoc_*` raised an NPE from
 *    `BackupControl.addBookToZip`, surfacing to the user as "An error has occurred").
 *
 * Every entry point that can enqueue a push must consult this — not just the periodic scan.
 */
val Book.isSyncableDocument: Boolean get() = !isPseudoBook && !isMyDocument

/** A single document-sync operation the [DocumentSyncService] queue can process. */
sealed class DocumentSyncOp {
    abstract val initials: String
    /** Upload a locally installed document to the cloud. */
    data class Push(override val initials: String) : DocumentSyncOp()
    /** Download + install a document from the cloud. */
    data class Download(override val initials: String) : DocumentSyncOp()
    /** Remove a document from the cloud (write a tombstone). */
    data class Remove(override val initials: String) : DocumentSyncOp()
    /** Permanently delete a document's tombstone (removed-document marker) from the cloud. */
    data class Purge(override val initials: String) : DocumentSyncOp()
    /** Uninstall a document's local copy after it was removed from the cloud elsewhere. */
    data class Uninstall(override val initials: String) : DocumentSyncOp()
}

/**
 * Builds the ordered op list for a batch: pushes, then downloads, then removals, then purges,
 * then local uninstalls.
 */
fun buildDocumentSyncOps(
    pushInitials: List<String>,
    downloadInitials: List<String>,
    removeInitials: List<String> = emptyList(),
    purgeInitials: List<String> = emptyList(),
    uninstallInitials: List<String> = emptyList(),
): List<DocumentSyncOp> =
    pushInitials.map { DocumentSyncOp.Push(it) } +
        downloadInitials.map { DocumentSyncOp.Download(it) } +
        removeInitials.map { DocumentSyncOp.Remove(it) } +
        purgeInitials.map { DocumentSyncOp.Purge(it) } +
        uninstallInitials.map { DocumentSyncOp.Uninstall(it) }

/** Whether an installed document should be auto-uploaded (on install or in the sync cycle). */
fun shouldAutoUpload(enabled: Boolean, autoUpload: Boolean, blocked: Boolean, autoTransferAllowed: Boolean): Boolean =
    enabled && autoUpload && !blocked && autoTransferAllowed

/**
 * Whether a sync-op failure is a transient network error (timeout, dropped connection, host
 * unreachable) rather than a genuine app error. Such failures must not raise the user-facing
 * "An error has occurred" notification (with its "Report" button): a connectivity blip is not a
 * bug to report, and the op is naturally retried on the next sync because its sync timestamp is
 * only advanced on success. Mirrors [net.bible.service.cloudsync.CloudSync]'s database-sync
 * handling, which silently swallows [IOException] as "probably network down".
 *
 * Walks the cause chain so a network failure wrapped in a higher-level exception is still
 * recognised (e.g. an [IOException] surfaced from a coroutine/JSword wrapper).
 */
fun isTransientNetworkError(e: Throwable?): Boolean {
    var cur = e
    val seen = HashSet<Throwable>()
    while (cur != null && seen.add(cur)) {
        if (cur is IOException) return true
        cur = cur.cause
    }
    return false
}

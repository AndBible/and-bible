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

data class CloudDocument(
    val initials: String,
    val name: String,
    val documentType: DocumentType,
    val version: String,
    val size: Long,
    val timestamp: Long,
    val deleted: Boolean,
)

data class LocalDocument(
    val initials: String,
    val version: String,
)

enum class DocumentSyncActionType { DOWNLOAD, UPGRADE, UNINSTALL, SKIP_BLOCKED, NONE }

data class DocumentSyncAction(val initials: String, val type: DocumentSyncActionType)

/**
 * Resolves the sync action to perform for each document in [cloudDocs], preserving input order.
 *
 * Decision precedence for each cloud document:
 * 1. **Tombstone (deleted=true)**: UNINSTALL if the document is not [blocked], a [syncTimestamps]
 *    record exists, and the tombstone is strictly newer than the last known sync; otherwise NONE
 *    (locally-installed-only docs and blocked docs are never auto-deleted).
 * 2. **Blocked**: SKIP_BLOCKED if the document's initials appear in [blocked].
 * 3. **Not installed locally**: DOWNLOAD.
 * 4. **Cloud newer than local**: UPGRADE (determined via [isNewer]).
 * 5. **Otherwise**: NONE (local is same or newer).
 *
 * @param cloudDocs   Documents reported by the cloud store, in desired processing order.
 * @param localDocs   Locally installed documents keyed by initials.
 * @param syncTimestamps Per-initials timestamp of the last confirmed sync (used by tombstone check).
 * @param blocked     Set of initials that must never be auto-downloaded or upgraded.
 * @param isNewer     Caller-injected version comparator. Returns **true** if [cloudVersion] is
 *                    strictly newer than [localVersion] (e.g. semantic version comparison).
 */
fun resolveDocumentSyncActions(
    cloudDocs: List<CloudDocument>,
    localDocs: Map<String, LocalDocument>,
    syncTimestamps: Map<String, Long>,
    blocked: Set<String>,
    isNewer: (cloudVersion: String, localVersion: String) -> Boolean,
): List<DocumentSyncAction> = cloudDocs.map { cloud ->
    val local = localDocs[cloud.initials]
    val type = when {
        cloud.deleted -> {
            val syncedAt = syncTimestamps[cloud.initials]
            // A blocked document is managed independently on this device, so a tombstone must not
            // uninstall it — blocking means "this device opts out of sync for this doc", which
            // includes opting out of remote deletion. The local copy is kept.
            if (cloud.initials !in blocked && local != null && syncedAt != null && cloud.timestamp > syncedAt)
                DocumentSyncActionType.UNINSTALL
            else DocumentSyncActionType.NONE
        }
        cloud.initials in blocked -> DocumentSyncActionType.SKIP_BLOCKED
        local == null -> DocumentSyncActionType.DOWNLOAD
        isNewer(cloud.version, local.version) -> DocumentSyncActionType.UPGRADE
        else -> DocumentSyncActionType.NONE
    }
    DocumentSyncAction(cloud.initials, type)
}

/**
 * Outcome of applying a propagated [DocumentSyncActionType.UNINSTALL] tombstone on *this* device.
 *
 * @property delete delete the local copy — only when the book is actually deletable.
 * @property advanceTimestamp record the tombstone as seen so it isn't re-evaluated every cycle.
 */
data class UninstallDecision(val delete: Boolean, val advanceTimestamp: Boolean)

/**
 * Decides what a receiving device should do for a propagated UNINSTALL.
 *
 * The tombstone is unconditionally acknowledged ([advanceTimestamp] always true) so a
 * non-deletable book doesn't trigger a fresh uninstall attempt on every sync cycle, but the
 * local copy is deleted only when [canDelete] is true. This is the guard that prevents a
 * tombstone from silently deleting the *last* Bible on a receiving device: the initiating
 * device's menu guard only protects its own UI, so the pull-flow delete must re-check here.
 */
fun decideUninstall(canDelete: Boolean): UninstallDecision =
    UninstallDecision(delete = canDelete, advanceTimestamp = true)

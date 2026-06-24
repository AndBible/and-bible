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
            if (local != null && syncedAt != null && cloud.timestamp > syncedAt)
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

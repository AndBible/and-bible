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

package net.bible.android.view.activity.cloud

import net.bible.android.activity.R
import net.bible.service.cloudsync.documents.DocumentSync.DocumentStatusItem
import net.bible.service.cloudsync.documents.DocumentType
import org.crosswire.jsword.book.BookCategory
import org.junit.Assert.assertEquals
import org.junit.Test

class CloudDocumentsMenuTest {
    private fun item(
        cloudOnly: Boolean = false, localOnly: Boolean = false,
        update: Boolean = false, localNewer: Boolean = false, blocked: Boolean = false,
        canDeleteLocal: Boolean = true, cloudDeleted: Boolean = false,
    ) = DocumentStatusItem(
        initials = "KJV", name = "KJV", type = DocumentType.SWORD,
        cloudVersion = "1.0", localVersion = "1.0",
        cloudOnly = cloudOnly, localOnly = localOnly, updateAvailable = update,
        localNewer = localNewer, blocked = blocked, sizeBytes = 0, category = BookCategory.BIBLE,
        canDeleteLocal = canDeleteLocal, cloudDeleted = cloudDeleted,
    )

    @Test fun cloudOnlyOffersDownloadRemoveBlock() {
        assertEquals(
            listOf(CloudDocAction.DOWNLOAD, CloudDocAction.REMOVE_CLOUD, CloudDocAction.BLOCK),
            documentMenuActions(item(cloudOnly = true), syncEnabled = true),
        )
    }

    @Test fun localOnlyOffersPushAndBlock() {
        // A device-only document can now be marked "do not sync to cloud" (BLOCK) in addition to Push.
        assertEquals(
            listOf(CloudDocAction.PUSH, CloudDocAction.BLOCK),
            documentMenuActions(item(localOnly = true), syncEnabled = true),
        )
    }

    @Test fun blockedLocalOnlyOffersUnblock() {
        assertEquals(
            listOf(CloudDocAction.PUSH, CloudDocAction.UNBLOCK),
            documentMenuActions(item(localOnly = true, blocked = true), syncEnabled = true),
        )
    }

    @Test fun actionLabelIsContextSensitiveForBlock() {
        // Local-only: "do not sync to cloud"; cloud document: the existing "block" wording.
        assertEquals(R.string.cloud_doc_action_dont_sync, actionLabelRes(CloudDocAction.BLOCK, localOnly = true, syncEnabled = true))
        assertEquals(R.string.cloud_doc_action_allow_sync, actionLabelRes(CloudDocAction.UNBLOCK, localOnly = true, syncEnabled = true))
        assertEquals(R.string.cloud_doc_action_block, actionLabelRes(CloudDocAction.BLOCK, localOnly = false, syncEnabled = true))
        assertEquals(R.string.cloud_doc_action_unblock, actionLabelRes(CloudDocAction.UNBLOCK, localOnly = false, syncEnabled = true))
    }

    @Test fun actionLabelRemoveDependsOnSyncEnabled() {
        assertEquals(R.string.cloud_doc_action_remove_all_devices, actionLabelRes(CloudDocAction.REMOVE_CLOUD, localOnly = false, syncEnabled = true))
        assertEquals(R.string.cloud_doc_action_remove_cloud, actionLabelRes(CloudDocAction.REMOVE_CLOUD, localOnly = false, syncEnabled = false))
    }

    @Test fun fullySyncedHasNoPushOrDownload() {
        // Same version on both sides: neither push nor download is meaningful.
        assertEquals(
            listOf(CloudDocAction.REMOVE_CLOUD, CloudDocAction.BLOCK),
            documentMenuActions(item(), syncEnabled = true),
        )
    }

    @Test fun cloudNewerOffersDownloadNotPush() {
        val actions = documentMenuActions(item(update = true), syncEnabled = true)
        assertEquals(listOf(CloudDocAction.DOWNLOAD, CloudDocAction.REMOVE_CLOUD, CloudDocAction.BLOCK), actions)
    }

    @Test fun localNewerOffersPushNotDownload() {
        val actions = documentMenuActions(item(localNewer = true), syncEnabled = true)
        assertEquals(listOf(CloudDocAction.PUSH, CloudDocAction.REMOVE_CLOUD, CloudDocAction.BLOCK), actions)
    }

    @Test fun blockedOffersUnblockNotBlock() {
        val actions = documentMenuActions(item(cloudOnly = true, blocked = true), syncEnabled = true)
        assertEquals(listOf(CloudDocAction.DOWNLOAD, CloudDocAction.REMOVE_CLOUD, CloudDocAction.UNBLOCK), actions)
    }

    @Test fun syncEnabledHidesRemoveForUndeletableLocal() {
        // Last Bible: with sync on, remove would delete it locally, so it isn't offered.
        val actions = documentMenuActions(item(canDeleteLocal = false), syncEnabled = true)
        assertEquals(listOf(CloudDocAction.BLOCK), actions)
    }

    @Test fun syncDisabledStillOffersRemoveForUndeletableLocal() {
        // With sync off, remove only touches the cloud (local copy kept), so it's still offered.
        val actions = documentMenuActions(item(canDeleteLocal = false), syncEnabled = false)
        assertEquals(listOf(CloudDocAction.REMOVE_CLOUD, CloudDocAction.BLOCK), actions)
    }

    @Test fun optimisticRemovalDropsRowWhenSyncEnabled() {
        val items = listOf(item())
        assertEquals(emptyList<DocumentStatusItem>(), applyOptimisticRemoval(items, "KJV", syncEnabled = true))
    }

    @Test fun optimisticRemovalDropsCloudOnlyRowWhenSyncDisabled() {
        val items = listOf(item(cloudOnly = true))
        assertEquals(emptyList<DocumentStatusItem>(), applyOptimisticRemoval(items, "KJV", syncEnabled = false))
    }

    @Test fun optimisticRemovalKeepsLocalCopyAsLocalOnlyWhenSyncDisabled() {
        // Synced (local + cloud), sync off: cloud copy goes, local copy stays → local-only.
        val result = applyOptimisticRemoval(listOf(item()), "KJV", syncEnabled = false)
        val row = result.single()
        assertEquals(true, row.localOnly)
        assertEquals(false, row.cloudOnly)
        assertEquals(null, row.cloudVersion)
    }

    @Test fun tombstoneInstalledLocallyOffersRestoreAndPurge() {
        val actions = documentMenuActions(item(localOnly = true, cloudDeleted = true), syncEnabled = true)
        assertEquals(listOf(CloudDocAction.RESTORE, CloudDocAction.PURGE), actions)
    }

    @Test fun tombstoneNotInstalledOffersOnlyPurge() {
        val actions = documentMenuActions(item(cloudDeleted = true), syncEnabled = true)
        assertEquals(listOf(CloudDocAction.PURGE), actions)
    }

    @Test fun tombstoneNeverOffersDownloadOrBlock() {
        val installed = documentMenuActions(item(localOnly = true, cloudDeleted = true), syncEnabled = false)
        assertEquals(false, installed.contains(CloudDocAction.DOWNLOAD))
        assertEquals(false, installed.contains(CloudDocAction.BLOCK))
        assertEquals(false, installed.contains(CloudDocAction.REMOVE_CLOUD))
    }

    @Test fun optimisticPurgeDropsNotInstalledTombstone() {
        // No local copy: purging the tombstone leaves nothing, so the row drops out.
        assertEquals(emptyList<DocumentStatusItem>(), applyOptimisticPurge(listOf(item(cloudDeleted = true)), "KJV"))
    }

    @Test fun optimisticPurgeKeepsInstalledTombstoneAsLocalOnly() {
        // Local copy remains: purging only removes the cloud marker, so the row becomes a plain
        // local-only document (no longer a tombstone, no cloud version).
        val row = applyOptimisticPurge(listOf(item(localOnly = true, cloudDeleted = true)), "KJV").single()
        assertEquals(false, row.cloudDeleted)
        assertEquals(true, row.localOnly)
        assertEquals(null, row.cloudVersion)
    }
}

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

import net.bible.service.cloudsync.documents.DocumentSync.DocumentStatusItem
import net.bible.service.cloudsync.documents.DocumentType
import org.crosswire.jsword.book.BookCategory
import org.junit.Assert.assertEquals
import org.junit.Test

class CloudDocumentsBulkTest {
    private fun item(
        initials: String,
        cloudOnly: Boolean = false, localOnly: Boolean = false,
        update: Boolean = false, localNewer: Boolean = false, blocked: Boolean = false,
        canDeleteLocal: Boolean = true, cloudDeleted: Boolean = false,
    ) = DocumentStatusItem(
        initials = initials, name = initials, type = DocumentType.SWORD,
        cloudVersion = "1.0", localVersion = "1.0",
        cloudOnly = cloudOnly, localOnly = localOnly, updateAvailable = update,
        localNewer = localNewer, blocked = blocked, sizeBytes = 0, category = BookCategory.BIBLE,
        canDeleteLocal = canDeleteLocal, cloudDeleted = cloudDeleted,
    )

    @Test fun emptySelectionHasNoActions() {
        assertEquals(emptyList<CloudDocAction>(), bulkMenuActions(emptyList(), syncEnabled = true))
    }

    @Test fun unionOverHeterogeneousSelectionInCanonicalOrder() {
        // cloud-only → DOWNLOAD, REMOVE_CLOUD, BLOCK; device-only → PUSH, BLOCK; synced → REMOVE_CLOUD, BLOCK.
        // Union, in CloudDocAction declaration order (DOWNLOAD, PUSH, REMOVE_CLOUD, BLOCK, ...).
        val selected = listOf(item("A", cloudOnly = true), item("B", localOnly = true), item("C"))
        assertEquals(
            listOf(CloudDocAction.DOWNLOAD, CloudDocAction.PUSH, CloudDocAction.REMOVE_CLOUD, CloudDocAction.BLOCK),
            bulkMenuActions(selected, syncEnabled = true),
        )
    }

    @Test fun blockAndUnblockBothAppearForMixedBlockedState() {
        // One blocked, one not → both opt-out actions surface (each applies to its own subset).
        val selected = listOf(item("A", cloudOnly = true, blocked = true), item("B", cloudOnly = true))
        val actions = bulkMenuActions(selected, syncEnabled = true)
        assertEquals(true, actions.contains(CloudDocAction.BLOCK))
        assertEquals(true, actions.contains(CloudDocAction.UNBLOCK))
    }

    @Test fun applicableInitialsForDownloadSkipsNonDownloadable() {
        val selected = listOf(item("A", cloudOnly = true), item("B", localOnly = true), item("C", update = true))
        // Only the cloud-only and updatable rows can be downloaded; the device-only row is skipped.
        assertEquals(listOf("A", "C"), applicableInitials(CloudDocAction.DOWNLOAD, selected, syncEnabled = true))
    }

    @Test fun applicableInitialsForBlockSkipsAlreadyBlocked() {
        val selected = listOf(item("A", cloudOnly = true, blocked = true), item("B", localOnly = true))
        // A is already blocked (offers UNBLOCK, not BLOCK); only B can be newly blocked.
        assertEquals(listOf("B"), applicableInitials(CloudDocAction.BLOCK, selected, syncEnabled = true))
    }

    @Test fun applicableInitialsForRemoveRespectsLastBibleGuardWhenSyncOn() {
        // Undeletable local copy (last Bible) with sync on: REMOVE_CLOUD is not offered → skipped.
        val selected = listOf(item("A", canDeleteLocal = false), item("B"))
        assertEquals(listOf("B"), applicableInitials(CloudDocAction.REMOVE_CLOUD, selected, syncEnabled = true))
    }
}

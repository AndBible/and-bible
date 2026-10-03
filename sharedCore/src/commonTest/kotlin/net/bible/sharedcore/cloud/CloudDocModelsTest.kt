package net.bible.sharedcore.cloud

import net.bible.sharedcore.navigation.DocCategory
import kotlin.test.Test
import kotlin.test.assertEquals

class CloudDocModelsTest {
    private fun item(
        initials: String = "KJV", cloudOnly: Boolean = false, localOnly: Boolean = false,
        updateAvailable: Boolean = false, blocked: Boolean = false, cloudDeleted: Boolean = false,
    ) = CloudDocItem(
        initials = initials, name = "name-$initials", category = DocCategory.BIBLE,
        cloudVersion = "1.0", localVersion = "1.0", cloudOnly = cloudOnly, localOnly = localOnly,
        updateAvailable = updateAvailable, localNewer = false, blocked = blocked,
        canDeleteLocal = true, cloudDeleted = cloudDeleted, sizeLabel = "4.2 MB",
    )

    @Test fun status_synced_when_both_present_and_current() =
        assertEquals(CloudDocStatus.SYNCED, cloudDocStatus(item()))

    @Test fun status_removed_still_installed_takes_precedence() =
        assertEquals(CloudDocStatus.REMOVED_STILL_INSTALLED, cloudDocStatus(item(cloudDeleted = true, localOnly = true)))

    @Test fun status_removed_when_tombstone_no_local() =
        assertEquals(CloudDocStatus.REMOVED, cloudDocStatus(item(cloudDeleted = true)))

    @Test fun status_wont_sync_for_blocked_local_only() =
        assertEquals(CloudDocStatus.WONT_SYNC, cloudDocStatus(item(blocked = true, localOnly = true)))

    @Test fun status_blocked_for_blocked_cloud_backed() =
        assertEquals(CloudDocStatus.BLOCKED, cloudDocStatus(item(blocked = true)))

    @Test fun status_update_then_cloud_only_then_local_only_ordering() {
        assertEquals(CloudDocStatus.UPDATE, cloudDocStatus(item(updateAvailable = true)))
        assertEquals(CloudDocStatus.CLOUD_ONLY, cloudDocStatus(item(cloudOnly = true)))
        assertEquals(CloudDocStatus.LOCAL_ONLY, cloudDocStatus(item(localOnly = true)))
    }

    @Test fun action_label_remove_adapts_to_sync_enabled() {
        assertEquals(CloudDocActionLabel.REMOVE_ALL_DEVICES, actionLabelKind(CloudDocAction.REMOVE_CLOUD, localOnly = false, syncEnabled = true))
        assertEquals(CloudDocActionLabel.REMOVE_CLOUD, actionLabelKind(CloudDocAction.REMOVE_CLOUD, localOnly = false, syncEnabled = false))
    }

    @Test fun action_label_block_unblock_adapt_to_local_only() {
        assertEquals(CloudDocActionLabel.DONT_SYNC, actionLabelKind(CloudDocAction.BLOCK, localOnly = true, syncEnabled = false))
        assertEquals(CloudDocActionLabel.BLOCK, actionLabelKind(CloudDocAction.BLOCK, localOnly = false, syncEnabled = false))
        assertEquals(CloudDocActionLabel.ALLOW_SYNC, actionLabelKind(CloudDocAction.UNBLOCK, localOnly = true, syncEnabled = false))
        assertEquals(CloudDocActionLabel.UNBLOCK, actionLabelKind(CloudDocAction.UNBLOCK, localOnly = false, syncEnabled = false))
    }
}

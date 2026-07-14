package net.bible.sharedcore.cloud

import net.bible.sharedcore.navigation.DocCategory
import kotlin.test.Test
import kotlin.test.assertEquals

class CloudDocFunctionsTest {
    private fun item(
        initials: String, name: String = "name-$initials", category: DocCategory? = DocCategory.BIBLE,
        cloudOnly: Boolean = false, localOnly: Boolean = false, updateAvailable: Boolean = false,
        localNewer: Boolean = false, blocked: Boolean = false, canDeleteLocal: Boolean = true,
        cloudDeleted: Boolean = false,
    ) = CloudDocItem(initials, name, category, "1.0", "1.0", cloudOnly, localOnly, updateAvailable,
        localNewer, blocked, canDeleteLocal, cloudDeleted, "4.2 MB")

    @Test fun filter_all_keeps_everything_but_tombstones() {
        val items = listOf(item("A"), item("B", cloudDeleted = true))
        assertEquals(listOf("A"), filterCloudDocuments(items, CloudDocFilter.ALL, "", null).map { it.initials })
    }

    @Test fun filter_removed_only_tombstones() {
        val items = listOf(item("A"), item("B", cloudDeleted = true))
        assertEquals(listOf("B"), filterCloudDocuments(items, CloudDocFilter.REMOVED, "", null).map { it.initials })
    }

    @Test fun filter_name_substring_case_insensitive_and_category() {
        val items = listOf(item("KJV", name = "King James"), item("MHC", name = "Matthew", category = DocCategory.COMMENTARY))
        assertEquals(listOf("KJV"), filterCloudDocuments(items, CloudDocFilter.ALL, "king", null).map { it.initials })
        assertEquals(listOf("MHC"), filterCloudDocuments(items, CloudDocFilter.ALL, "", DocCategory.COMMENTARY).map { it.initials })
    }

    @Test fun menu_actions_for_synced_offers_push_only_when_local_newer_and_remove_and_block() {
        // fully synced (both present, no update, not local-newer): Remove-from-cloud + Block, no Download/Push.
        assertEquals(listOf(CloudDocAction.REMOVE_CLOUD, CloudDocAction.BLOCK), documentMenuActions(item("A"), syncEnabled = false))
    }

    @Test fun menu_actions_cloud_only_offers_download_remove_block() {
        // Classic parity (CloudDocumentsMenuTest.cloudOnlyOffersDownloadRemoveBlock): a cloud copy exists,
        // and canDeleteLocal defaults true, so REMOVE_CLOUD IS offered for a cloud-only row.
        assertEquals(listOf(CloudDocAction.DOWNLOAD, CloudDocAction.REMOVE_CLOUD, CloudDocAction.BLOCK), documentMenuActions(item("A", cloudOnly = true, localOnly = false), syncEnabled = false))
    }

    @Test fun menu_actions_local_only_offers_push_block_no_remove() {
        assertEquals(listOf(CloudDocAction.PUSH, CloudDocAction.BLOCK), documentMenuActions(item("A", localOnly = true), syncEnabled = false))
    }

    @Test fun menu_actions_tombstone_localOnly_offers_restore_then_purge() {
        assertEquals(listOf(CloudDocAction.RESTORE, CloudDocAction.PURGE), documentMenuActions(item("A", cloudDeleted = true, localOnly = true), syncEnabled = false))
    }

    @Test fun menu_actions_tombstone_no_local_offers_purge_only() {
        assertEquals(listOf(CloudDocAction.PURGE), documentMenuActions(item("A", cloudDeleted = true), syncEnabled = false))
    }

    @Test fun remove_suppressed_when_sync_enabled_and_cannot_delete_local() {
        val a = item("A", canDeleteLocal = false)
        assertEquals(false, CloudDocAction.REMOVE_CLOUD in documentMenuActions(a, syncEnabled = true))
    }

    @Test fun bulk_actions_are_union_in_declaration_order() {
        val selected = listOf(item("cloud", cloudOnly = true, localOnly = false), item("local", localOnly = true))
        // union: DOWNLOAD + REMOVE_CLOUD (cloud), PUSH (local), BLOCK (both) -> declaration order.
        assertEquals(listOf(CloudDocAction.DOWNLOAD, CloudDocAction.PUSH, CloudDocAction.REMOVE_CLOUD, CloudDocAction.BLOCK), bulkMenuActions(selected, syncEnabled = false))
    }

    @Test fun applicable_initials_only_supporting_rows() {
        val selected = listOf(item("cloud", cloudOnly = true, localOnly = false), item("local", localOnly = true))
        assertEquals(listOf("cloud"), applicableInitials(CloudDocAction.DOWNLOAD, selected, syncEnabled = false))
    }

    @Test fun optimistic_removal_drops_when_sync_on_else_becomes_local_only() {
        val items = listOf(item("A"))
        assertEquals(emptyList(), applyOptimisticRemoval(items, "A", syncEnabled = true).map { it.initials })
        val kept = applyOptimisticRemoval(items, "A", syncEnabled = false).single()
        assertEquals(true, kept.localOnly); assertEquals(false, kept.cloudOnly); assertEquals(null, kept.cloudVersion)
    }

    @Test fun optimistic_purge_localOnly_clears_tombstone_else_drops() {
        val local = item("A", cloudDeleted = true, localOnly = true)
        assertEquals(false, applyOptimisticPurge(listOf(local), "A").single().cloudDeleted)
        val cloudTomb = item("B", cloudDeleted = true, localOnly = false)
        assertEquals(emptyList(), applyOptimisticPurge(listOf(cloudTomb), "B").map { it.initials })
    }
}

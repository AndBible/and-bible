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

    @Test fun filter_all_keeps_everything_including_tombstones() {
        // Classic parity: ALL returns true for every row, tombstones included. Tombstone presence is
        // gated at the SCAN (includeDeleted), not by this filter — so when a tombstone reaches the
        // filter (show-removed on), ALL shows it too, exactly like classic CloudDocumentsActivity.
        val items = listOf(item("A"), item("B", cloudDeleted = true))
        assertEquals(listOf("A", "B"), filterCloudDocuments(items, CloudDocFilter.ALL, "", null).map { it.initials })
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

    // ---------------------------------------------------------------------------------------------
    // Cases ported from :app's classic cloud tests (CloudDocumentsFilterTest, CloudDocumentsMenuTest,
    // CloudDocumentsBulkTest). Their host was the set of top-level functions declared OUTSIDE classic
    // CloudDocumentsActivity.kt, deleted in S8 as a duplicate of this already-migrated logic — so
    // these assertions live here now, restated against the KMP-clean CloudDocItem/DocCategory model.
    // Each case names the classic original it came from.
    // ---------------------------------------------------------------------------------------------

    /** The classic CloudDocumentsFilterTest fixture, translated to CloudDocItem/DocCategory. */
    private val filterFixture = listOf(
        item("KJV", name = "King James", localOnly = true, category = DocCategory.BIBLE),
        item("ESV", name = "English Standard", cloudOnly = true, category = DocCategory.BIBLE),
        item("MHC", name = "Matthew Henry", updateAvailable = true, category = DocCategory.COMMENTARY),
        item("STRONGS", name = "Strongs", blocked = true, category = DocCategory.DICTIONARY),
        item("NOCAT", name = "Unknown", cloudOnly = true, category = null),
        item("GONE", name = "Removed Book", cloudDeleted = true, category = DocCategory.BIBLE),
    )

    @Test fun filter_installed_excludes_cloud_only() {
        // Ported from CloudDocumentsFilterTest.installedExcludesCloudOnly.
        assertEquals(
            listOf("KJV", "MHC", "STRONGS"),
            filterCloudDocuments(filterFixture, CloudDocFilter.INSTALLED, "", null).map { it.initials },
        )
    }

    @Test fun filter_cloud_excludes_local_only() {
        // Ported from CloudDocumentsFilterTest.cloudExcludesLocalOnly.
        assertEquals(
            listOf("ESV", "MHC", "STRONGS", "NOCAT"),
            filterCloudDocuments(filterFixture, CloudDocFilter.CLOUD, "", null).map { it.initials },
        )
    }

    @Test fun filter_updates_and_blocked_branches() {
        // Ported from CloudDocumentsFilterTest.updatesAndBlocked, which asserts two filter branches
        // in one case: UPDATES keeps only the update-available row, BLOCKED only the blocked one.
        assertEquals(
            listOf("MHC"),
            filterCloudDocuments(filterFixture, CloudDocFilter.UPDATES, "", null).map { it.initials },
        )
        assertEquals(
            listOf("STRONGS"),
            filterCloudDocuments(filterFixture, CloudDocFilter.BLOCKED, "", null).map { it.initials },
        )
    }

    @Test fun filter_name_query_matches_a_later_word_case_insensitively() {
        // Ported from CloudDocumentsFilterTest.nameQueryIsCaseInsensitiveSubstring: a lowercase query
        // finds a capitalised word that is NOT the start of the name ("james" in "King James"), and
        // excludes every other row. Classic matched a plain substring; matchesDocumentQuery matches a
        // token prefix over initials AND name, so this second-word case holds under both.
        assertEquals(
            listOf("KJV"),
            filterCloudDocuments(filterFixture, CloudDocFilter.ALL, "james", null).map { it.initials },
        )
    }

    @Test fun filter_null_category_item_never_matches_a_concrete_category() {
        // Ported from CloudDocumentsFilterTest.nullCategoryItemMatchesOnlyAllCategory.
        // Filtering by a concrete category never includes the null-category item.
        assertEquals(
            emptyList(),
            filterCloudDocuments(filterFixture, CloudDocFilter.ALL, "", DocCategory.MAPS).map { it.initials },
        )
    }

    @Test fun filter_combines_status_name_and_category() {
        // Ported from CloudDocumentsFilterTest.combinesStatusNameAndCategory: the three predicates are
        // a conjunction, so only the row satisfying status AND name AND category survives.
        assertEquals(
            listOf("ESV"),
            filterCloudDocuments(filterFixture, CloudDocFilter.CLOUD, "english", DocCategory.BIBLE).map { it.initials },
        )
    }

    @Test fun filter_device_only_keeps_only_local_only_documents() {
        // Ported from CloudDocumentsFilterTest.deviceOnlyKeepsOnlyLocalOnlyDocuments.
        // Only KJV is installed on this device and absent from the cloud.
        assertEquals(
            listOf("KJV"),
            filterCloudDocuments(filterFixture, CloudDocFilter.DEVICE_ONLY, "", null).map { it.initials },
        )
    }

    @Test fun filter_device_only_excludes_tombstones() {
        // Ported from CloudDocumentsFilterTest.deviceOnlyExcludesTombstones.
        // A still-installed tombstone is local-only in the data model but must not appear here —
        // it belongs under REMOVED, not "Only on this device".
        val ghost = listOf(item("GHOST", localOnly = true, cloudDeleted = true))
        assertEquals(
            emptyList(),
            filterCloudDocuments(ghost, CloudDocFilter.DEVICE_ONLY, "", null).map { it.initials },
        )
    }

    @Test fun filter_device_only_is_a_strict_subset_of_installed() {
        // Ported from CloudDocumentsFilterTest.deviceOnlyDiffersFromInstalled.
        // INSTALLED includes the synced/cloud-backed local copies; DEVICE_ONLY is the strict subset.
        val both = listOf(
            item("LOCALONLY", localOnly = true),
            item("SYNCED"), // installed AND in cloud: neither localOnly nor cloudOnly
        )
        assertEquals(
            listOf("LOCALONLY", "SYNCED"),
            filterCloudDocuments(both, CloudDocFilter.INSTALLED, "", null).map { it.initials },
        )
        assertEquals(
            listOf("LOCALONLY"),
            filterCloudDocuments(both, CloudDocFilter.DEVICE_ONLY, "", null).map { it.initials },
        )
    }

    @Test fun filter_cloud_only_keeps_only_cloud_only_documents() {
        // Ported from CloudDocumentsFilterTest.cloudOnlyKeepsOnlyCloudOnlyDocuments.
        // ESV and NOCAT exist only in the cloud and are not installed on this device.
        assertEquals(
            listOf("ESV", "NOCAT"),
            filterCloudDocuments(filterFixture, CloudDocFilter.CLOUD_ONLY, "", null).map { it.initials },
        )
    }

    @Test fun filter_cloud_only_excludes_tombstones() {
        // Ported from CloudDocumentsFilterTest.cloudOnlyExcludesTombstones.
        // A cloud-only tombstone belongs under REMOVED, not "In cloud only".
        val ghost = listOf(item("GHOST", cloudOnly = true, cloudDeleted = true))
        assertEquals(
            emptyList(),
            filterCloudDocuments(ghost, CloudDocFilter.CLOUD_ONLY, "", null).map { it.initials },
        )
    }

    @Test fun filter_cloud_only_is_a_strict_subset_of_cloud() {
        // Ported from CloudDocumentsFilterTest.cloudOnlyDiffersFromCloud.
        // CLOUD includes the synced/local-backed cloud copies; CLOUD_ONLY is the strict subset.
        val both = listOf(
            item("CLOUDONLY", cloudOnly = true),
            item("SYNCED"), // installed AND in cloud: neither localOnly nor cloudOnly
        )
        assertEquals(
            listOf("CLOUDONLY", "SYNCED"),
            filterCloudDocuments(both, CloudDocFilter.CLOUD, "", null).map { it.initials },
        )
        assertEquals(
            listOf("CLOUDONLY"),
            filterCloudDocuments(both, CloudDocFilter.CLOUD_ONLY, "", null).map { it.initials },
        )
    }

    @Test fun filter_device_only_and_cloud_only_are_mutually_exclusive() {
        // Ported from CloudDocumentsFilterTest.deviceOnlyAndCloudOnlyAreMutuallyExclusive.
        // The two opposite filters never both match the same document, and together exclude synced items.
        val deviceOnly = filterCloudDocuments(filterFixture, CloudDocFilter.DEVICE_ONLY, "", null).map { it.initials }.toSet()
        val cloudOnly = filterCloudDocuments(filterFixture, CloudDocFilter.CLOUD_ONLY, "", null).map { it.initials }.toSet()
        // Both halves are non-empty for this fixture (KJV vs ESV/NOCAT), so the intersection is a
        // real check rather than a vacuous one.
        assertEquals(setOf("KJV"), deviceOnly)
        assertEquals(setOf("ESV", "NOCAT"), cloudOnly)
        assertEquals(emptySet(), deviceOnly intersect cloudOnly)
    }

    @Test fun filter_tombstone_excluded_from_all_non_removed_status_filters() {
        // Ported from CloudDocumentsFilterTest.tombstoneExcludedFromAllNonRemovedStatusFilters.
        // A removed (tombstone) document may still carry last-known blocked / update-available flags,
        // but it must surface only under ALL and REMOVED — never INSTALLED/CLOUD/UPDATES/BLOCKED.
        val ghost = listOf(item("GHOST", blocked = true, updateAvailable = true, cloudDeleted = true))
        assertEquals(listOf("GHOST"), filterCloudDocuments(ghost, CloudDocFilter.ALL, "", null).map { it.initials })
        assertEquals(listOf("GHOST"), filterCloudDocuments(ghost, CloudDocFilter.REMOVED, "", null).map { it.initials })
        for (status in listOf(CloudDocFilter.INSTALLED, CloudDocFilter.CLOUD, CloudDocFilter.UPDATES, CloudDocFilter.BLOCKED)) {
            assertEquals(
                emptyList(),
                filterCloudDocuments(ghost, status, "", null).map { it.initials },
                "$status must exclude tombstones",
            )
        }
    }

    @Test fun menu_actions_blocked_local_only_offers_push_and_unblock() {
        // Ported from CloudDocumentsMenuTest.blockedLocalOnlyOffersUnblock: a blocked device-only
        // document offers Push plus the un-block half of the opt-out, and still no Remove.
        assertEquals(
            listOf(CloudDocAction.PUSH, CloudDocAction.UNBLOCK),
            documentMenuActions(item("A", localOnly = true, blocked = true), syncEnabled = true),
        )
    }

    @Test fun menu_actions_update_available_offers_download_not_push() {
        // Ported from CloudDocumentsMenuTest.cloudNewerOffersDownloadNotPush: DOWNLOAD is offered for
        // the updateAvailable branch too, not only for a cloud-only row.
        assertEquals(
            listOf(CloudDocAction.DOWNLOAD, CloudDocAction.REMOVE_CLOUD, CloudDocAction.BLOCK),
            documentMenuActions(item("A", updateAvailable = true), syncEnabled = true),
        )
    }

    @Test fun menu_actions_local_newer_offers_push_not_download() {
        // Ported from CloudDocumentsMenuTest.localNewerOffersPushNotDownload: PUSH is offered for the
        // localNewer branch too, not only for a device-only row.
        assertEquals(
            listOf(CloudDocAction.PUSH, CloudDocAction.REMOVE_CLOUD, CloudDocAction.BLOCK),
            documentMenuActions(item("A", localNewer = true), syncEnabled = true),
        )
    }

    @Test fun menu_actions_blocked_cloud_only_offers_unblock_not_block() {
        // Ported from CloudDocumentsMenuTest.blockedOffersUnblockNotBlock.
        assertEquals(
            listOf(CloudDocAction.DOWNLOAD, CloudDocAction.REMOVE_CLOUD, CloudDocAction.UNBLOCK),
            documentMenuActions(item("A", cloudOnly = true, blocked = true), syncEnabled = true),
        )
    }

    @Test fun menu_actions_remove_still_offered_for_undeletable_local_when_sync_disabled() {
        // Ported from CloudDocumentsMenuTest.syncDisabledStillOffersRemoveForUndeletableLocal — the
        // other half of the last-Bible guard: with sync off, remove only touches the cloud (the local
        // copy is kept), so it IS still offered even though the local copy cannot be deleted.
        assertEquals(
            listOf(CloudDocAction.REMOVE_CLOUD, CloudDocAction.BLOCK),
            documentMenuActions(item("A", canDeleteLocal = false), syncEnabled = false),
        )
    }

    @Test fun optimistic_removal_drops_cloud_only_row_when_sync_disabled() {
        // Ported from CloudDocumentsMenuTest.optimisticRemovalDropsCloudOnlyRowWhenSyncDisabled: with
        // sync off the row is normally KEPT as local-only, but a cloud-only row has no local copy to
        // keep, so it drops out.
        assertEquals(
            emptyList(),
            applyOptimisticRemoval(listOf(item("A", cloudOnly = true)), "A", syncEnabled = false).map { it.initials },
        )
    }

    @Test fun optimistic_purge_keeps_installed_tombstone_as_plain_local_only() {
        // Ported from CloudDocumentsMenuTest.optimisticPurgeKeepsInstalledTombstoneAsLocalOnly.
        // Local copy remains: purging only removes the cloud marker, so the row becomes a plain
        // local-only document (no longer a tombstone, no cloud version).
        val row = applyOptimisticPurge(listOf(item("A", localOnly = true, cloudDeleted = true)), "A").single()
        assertEquals(false, row.cloudDeleted)
        assertEquals(true, row.localOnly)
        assertEquals(null, row.cloudVersion)
    }

    @Test fun bulk_actions_empty_selection_has_no_actions() {
        // Ported from CloudDocumentsBulkTest.emptySelectionHasNoActions.
        assertEquals(emptyList(), bulkMenuActions(emptyList(), syncEnabled = true))
    }

    @Test fun bulk_actions_block_and_unblock_both_appear_for_mixed_blocked_state() {
        // Ported from CloudDocumentsBulkTest.blockAndUnblockBothAppearForMixedBlockedState.
        // One blocked, one not → both opt-out actions surface (each applies to its own subset).
        val selected = listOf(item("A", cloudOnly = true, blocked = true), item("B", cloudOnly = true))
        val actions = bulkMenuActions(selected, syncEnabled = true)
        assertEquals(true, CloudDocAction.BLOCK in actions)
        assertEquals(true, CloudDocAction.UNBLOCK in actions)
    }

    @Test fun applicable_initials_for_download_includes_update_available_and_skips_device_only() {
        // Ported from CloudDocumentsBulkTest.applicableInitialsForDownloadSkipsNonDownloadable.
        // Only the cloud-only and updatable rows can be downloaded; the device-only row is skipped.
        val selected = listOf(item("A", cloudOnly = true), item("B", localOnly = true), item("C", updateAvailable = true))
        assertEquals(listOf("A", "C"), applicableInitials(CloudDocAction.DOWNLOAD, selected, syncEnabled = true))
    }

    @Test fun applicable_initials_for_block_skips_already_blocked() {
        // Ported from CloudDocumentsBulkTest.applicableInitialsForBlockSkipsAlreadyBlocked.
        // A is already blocked (offers UNBLOCK, not BLOCK); only B can be newly blocked.
        val selected = listOf(item("A", cloudOnly = true, blocked = true), item("B", localOnly = true))
        assertEquals(listOf("B"), applicableInitials(CloudDocAction.BLOCK, selected, syncEnabled = true))
    }

    @Test fun applicable_initials_for_remove_respects_last_bible_guard_when_sync_on() {
        // Ported from CloudDocumentsBulkTest.applicableInitialsForRemoveRespectsLastBibleGuardWhenSyncOn:
        // a domain rule worth keeping — an undeletable local copy (the user's last Bible) with sync on
        // is not offered REMOVE_CLOUD, so a bulk remove must skip it instead of deleting it.
        val selected = listOf(item("A", canDeleteLocal = false), item("B"))
        assertEquals(listOf("B"), applicableInitials(CloudDocAction.REMOVE_CLOUD, selected, syncEnabled = true))
    }
}

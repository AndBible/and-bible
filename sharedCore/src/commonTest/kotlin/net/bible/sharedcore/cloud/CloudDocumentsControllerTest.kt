package net.bible.sharedcore.cloud

import net.bible.sharedcore.navigation.DocCategory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CloudDocumentsControllerTest {
    private fun item(
        i: String,
        cloudOnly: Boolean = false,
        localOnly: Boolean = false,
        cloudDeleted: Boolean = false,
        category: DocCategory = DocCategory.BIBLE,
        name: String = "name-$i",
    ) =
        CloudDocItem(i, name, category, "1.0", "1.0", cloudOnly, localOnly, false, false, false, true, cloudDeleted, "4.2 MB")

    private fun controller(
        syncEnabled: Boolean = false,
        onAction: (CloudDocAction, String) -> Unit = { _, _ -> },
        onBulkAction: (CloudDocAction, List<String>) -> Unit = { _, _ -> },
        onSyncNow: (Boolean, Boolean, Boolean) -> Unit = { _, _, _ -> },
        onShowRemovedChange: (Boolean) -> Unit = {},
    ) = CloudDocumentsController({ syncEnabled }, onAction, onBulkAction, onSyncNow, {}, onShowRemovedChange)

    @Test fun setItems_populates_displayed_via_filter() {
        val c = controller()
        c.setItems(listOf(item("A"), item("B", cloudDeleted = true)))
        // Classic parity: ALL keeps everything, tombstones included. In the real app the host's scan
        // (includeDeleted) decides whether a tombstone reaches the controller at all; here it is
        // injected directly, so ALL surfaces it.
        assertEquals(listOf("A", "B"), c.displayed.value.map { it.initials })
    }

    @Test fun status_and_category_and_query_filters_compose() {
        val c = controller()
        c.setItems(listOf(
            item("KJV", category = DocCategory.BIBLE, name = "King James"),
            item("CLD", cloudOnly = true, category = DocCategory.BIBLE, name = "Cloud Bible"),
            item("DICT", category = DocCategory.DICTIONARY, name = "Strong Dictionary"),
        ))

        // Status alone.
        c.setStatusFilter(CloudDocFilter.CLOUD_ONLY)
        assertEquals(listOf("CLD"), c.displayed.value.map { it.initials })

        // Category alone (reset status to ALL first so category is the only discriminator).
        c.setStatusFilter(CloudDocFilter.ALL)
        c.setCategoryFilter(DocCategory.DICTIONARY)
        assertEquals(listOf("DICT"), c.displayed.value.map { it.initials })

        // Query alone composes with the standing category filter (DICTIONARY): "bible" matches no
        // dictionary, so nothing shows even though a bible name matches.
        c.setQuery("bible")
        assertTrue(c.displayed.value.isEmpty())

        // Query alone once category is cleared: name substring across all categories.
        c.setCategoryFilter(null)
        assertEquals(listOf("CLD"), c.displayed.value.map { it.initials }) // only "Cloud Bible" contains "bible"

        // Status + category + query all composing at once.
        c.setStatusFilter(CloudDocFilter.ALL)
        c.setCategoryFilter(DocCategory.BIBLE)
        c.setQuery("king")
        assertEquals(listOf("KJV"), c.displayed.value.map { it.initials })
    }

    @Test fun category_and_query_filters_exit_selection() {
        val c = controller()
        c.setItems(listOf(item("A")))
        c.enterSelection(); c.toggle("A")
        c.setCategoryFilter(DocCategory.DICTIONARY)
        assertFalse(c.selectionMode.value); assertTrue(c.selectedIds.value.isEmpty())

        c.enterSelection(); c.toggle("A")
        c.setQuery("x")
        assertFalse(c.selectionMode.value); assertTrue(c.selectedIds.value.isEmpty())
    }

    @Test fun changing_filter_exits_selection() {
        val c = controller()
        c.setItems(listOf(item("A")))
        c.enterSelection(); c.toggle("A")
        assertTrue(c.selectionMode.value)
        c.setStatusFilter(CloudDocFilter.CLOUD)
        assertFalse(c.selectionMode.value); assertTrue(c.selectedIds.value.isEmpty())
    }

    @Test fun setItems_prunes_selection_without_dropping_mode() {
        val c = controller()
        c.setItems(listOf(item("A"), item("B")))
        c.enterSelection(); c.toggle("A"); c.toggle("B")
        c.setItems(listOf(item("A"))) // B removed by a background refresh
        assertTrue(c.selectionMode.value)
        assertEquals(setOf("A"), c.selectedIds.value)
    }

    @Test fun busy_counter_and_transfer_drive_loading() {
        val c = controller()
        assertFalse(c.busy.value)
        c.pushBusy(true); c.pushBusy(true); assertTrue(c.busy.value)
        c.pushBusy(false); assertTrue(c.busy.value)
        c.pushBusy(false); assertFalse(c.busy.value)
        c.setTransferRunning(true); assertTrue(c.transferRunning.value)
    }

    @Test fun performAction_forwards_nondestructive_to_seam() {
        var got: Pair<CloudDocAction, String>? = null
        val c = controller(onAction = { a, i -> got = a to i })
        c.setItems(listOf(item("A", localOnly = true)))
        c.performAction(item("A", localOnly = true), CloudDocAction.PUSH)
        assertEquals(CloudDocAction.PUSH to "A", got)
    }

    @Test fun performBulk_forwards_applicable_subset() {
        var got: Pair<CloudDocAction, List<String>>? = null
        val c = controller(onBulkAction = { a, i -> got = a to i })
        c.setItems(listOf(item("cloud", cloudOnly = true, localOnly = false), item("local", localOnly = true)))
        c.enterSelection(); c.toggle("cloud"); c.toggle("local")
        c.performBulk(CloudDocAction.DOWNLOAD)
        assertEquals(CloudDocAction.DOWNLOAD to listOf("cloud"), got)
    }

    @Test fun setBlocked_updates_row_in_place() {
        val c = controller()
        c.setItems(listOf(item("A")))
        c.setBlocked("A", true)
        assertTrue(c.items.value.single { it.initials == "A" }.blocked)
    }

    @Test fun applyRemoval_and_applyPurge_use_optimistic_functions() {
        val c = controller(syncEnabled = true)
        c.setItems(listOf(item("A")))
        c.applyRemoval("A")
        assertTrue(c.items.value.none { it.initials == "A" }) // sync-on removal drops
        val c2 = controller()
        c2.setItems(listOf(item("B", cloudDeleted = true, localOnly = true)))
        c2.applyPurge("B")
        assertFalse(c2.items.value.single { it.initials == "B" }.cloudDeleted)
    }

    @Test fun sync_now_dialog_lifecycle() {
        var dirs: Triple<Boolean, Boolean, Boolean>? = null
        val c = controller(onSyncNow = { d, u, x -> dirs = Triple(d, u, x) })
        c.showSyncNow(listOf("dl", "ul", "del"), listOf(true, false, true))
        assertEquals(3, c.syncNowDialog.value!!.labels.size)
        c.confirmSyncNow(listOf(true, true, false))
        assertEquals(Triple(true, true, false), dirs)
        assertNull(c.syncNowDialog.value) // confirm dismisses
    }

    @Test fun show_removed_toggles_state_and_reports() {
        var reported: Boolean? = null
        val c = controller(onShowRemovedChange = { reported = it })
        c.setShowRemoved(true)
        assertTrue(c.showRemoved.value); assertEquals(true, reported)
    }

    @Test fun hiding_removed_from_REMOVED_filter_recomputes_displayed_and_exits_selection() {
        val c = controller()
        c.setItems(listOf(item("A"), item("TOMB", cloudDeleted = true)))
        c.setShowRemoved(true)
        c.setStatusFilter(CloudDocFilter.REMOVED)
        assertEquals(listOf("TOMB"), c.displayed.value.map { it.initials }) // tombstone surfaced
        c.enterSelection(); c.toggle("TOMB")
        assertTrue(c.selectionMode.value)

        c.setShowRemoved(false)

        // Filter flipped REMOVED → ALL and `displayed` was recomputed to the ALL result rather than
        // left stale on the REMOVED list. Under classic semantics the controller does NOT strip
        // tombstones — presence is the host's job (its re-scan with includeDeleted=false would drop
        // TOMB from `_items`). Here TOMB was injected directly and is still in `_items`, so the
        // recomputed ALL result includes it.
        assertEquals(CloudDocFilter.ALL, c.statusFilter.value)
        assertEquals(listOf("A", "TOMB"), c.displayed.value.map { it.initials })
        assertEquals(
            filterCloudDocuments(c.items.value, CloudDocFilter.ALL, "", null).map { it.initials },
            c.displayed.value.map { it.initials },
        )
        // Selection mode exited on the flip, like the other filter-changing setters.
        assertFalse(c.selectionMode.value); assertTrue(c.selectedIds.value.isEmpty())
    }
}

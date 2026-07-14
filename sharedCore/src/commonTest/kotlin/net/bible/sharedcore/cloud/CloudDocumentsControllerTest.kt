package net.bible.sharedcore.cloud

import net.bible.sharedcore.navigation.DocCategory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CloudDocumentsControllerTest {
    private fun item(i: String, cloudOnly: Boolean = false, localOnly: Boolean = false, cloudDeleted: Boolean = false) =
        CloudDocItem(i, "name-$i", DocCategory.BIBLE, "1.0", "1.0", cloudOnly, localOnly, false, false, false, true, cloudDeleted, "4.2 MB")

    private fun controller(
        syncEnabled: Boolean = false,
        onAction: (CloudDocAction, String) -> Unit = { _, _ -> },
        onBulkAction: (CloudDocAction, List<String>) -> Unit = { _, _ -> },
        onSyncNow: (Boolean, Boolean, Boolean) -> Unit = { _, _, _ -> },
        onShowRemovedChange: (Boolean) -> Unit = {},
    ) = CloudDocumentsController({ syncEnabled }, onAction, onBulkAction, onSyncNow, {}, {}, {}, onShowRemovedChange)

    @Test fun setItems_populates_displayed_via_filter() {
        val c = controller()
        c.setItems(listOf(item("A"), item("B", cloudDeleted = true)))
        assertEquals(listOf("A"), c.displayed.value.map { it.initials }) // ALL hides tombstones
    }

    @Test fun status_and_category_and_query_filters_compose() {
        val c = controller()
        c.setItems(listOf(item("KJV"), item("CLD", cloudOnly = true, localOnly = false)))
        c.setStatusFilter(CloudDocFilter.CLOUD_ONLY)
        assertEquals(listOf("CLD"), c.displayed.value.map { it.initials })
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
}

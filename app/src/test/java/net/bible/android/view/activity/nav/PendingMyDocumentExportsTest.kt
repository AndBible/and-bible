package net.bible.android.view.activity.nav

import android.os.Bundle
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PendingMyDocumentExportsTest {
    @Test fun pending_targets_survive_a_saved_state_round_trip() {
        val before = PendingMyDocumentExports()
        before.documentIds = listOf("doc-a", "doc-b")
        before.pageIds = listOf("page-1"); before.documentsBatch = true
        val state = Bundle().also { before.saveTo(it) }

        // The recreated Activity gets a brand-new holder and restores it from the bundle.
        val after = PendingMyDocumentExports().also { it.restoreFrom(state) }
        assertEquals(listOf("doc-a", "doc-b"), after.takeDocumentIds())
        assertEquals(listOf("page-1"), after.takePageIds()); assertEquals(true, after.documentsBatch)
    }

    @Test fun take_clears_so_a_target_is_delivered_once() {
        val p = PendingMyDocumentExports()
        p.documentIds = listOf("x")
        assertEquals(listOf("x"), p.takeDocumentIds())
        assertEquals(emptyList<String>(), p.takeDocumentIds())
    }

    @Test fun restore_from_null_or_empty_state_is_empty() {
        val p = PendingMyDocumentExports().also { it.documentIds = listOf("stale") }
        p.restoreFrom(null)
        assertEquals(emptyList<String>(), p.documentIds)
        p.restoreFrom(Bundle())
        assertEquals(emptyList<String>(), p.pageIds)
    }
}

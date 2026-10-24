package net.bible.sharedui.app.mydocuments

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PendingMyDocumentExportsTest {
    @Test fun pendingTargetsSurviveASnapshotRoundTrip() {
        val before = PendingMyDocumentExports()
        before.documentIds = listOf("doc-a", "doc-b")
        before.pageIds = listOf("page-1")
        before.documentsBatch = true

        // The recreated host gets a brand-new holder and restores it from the snapshot.
        val after = PendingMyDocumentExports().also { it.restoreFrom(before.snapshot()) }
        assertEquals(listOf("doc-a", "doc-b"), after.takeDocumentIds())
        assertEquals(listOf("page-1"), after.takePageIds())
        assertTrue(after.documentsBatch)
    }

    @Test fun takeClearsSoATargetIsDeliveredOnce() {
        val p = PendingMyDocumentExports()
        p.documentIds = listOf("x")
        assertTrue(p.isPending)
        assertEquals(listOf("x"), p.takeDocumentIds())
        assertEquals(emptyList<String>(), p.takeDocumentIds())
        assertFalse(p.isPending)
    }

    @Test fun restoreFromNullIsEmpty() {
        val p = PendingMyDocumentExports().also { it.documentIds = listOf("stale"); it.documentsBatch = true }
        p.restoreFrom(null)
        assertEquals(MyDocumentsRecreationState(), p.snapshot())
    }
}

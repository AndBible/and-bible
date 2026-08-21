package net.bible.sharedcore.mydocuments

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MyDocumentsControllerTest {
    private fun item(id: Long, name: String = "doc$id", description: String = "", canDelete: Boolean = true) =
        MyDocItem(id, "Ini$id", name, description, isAiGenerated = !canDelete, canDelete = canDelete)

    private var savedOrder: List<Long>? = null
    private var savedChanged: Set<Long>? = null
    private var savedDeleted: Set<Long>? = null

    private fun controller() = MyDocumentsController(
        onOpen = {}, onImport = {}, onExport = {}, onCreate = {},
        onSave = { order, changed, deleted -> savedOrder = order; savedChanged = changed; savedDeleted = deleted },
    )

    @Test fun starts_clean_and_empty() {
        val c = controller()
        assertTrue(c.documents.value.isEmpty())
        assertFalse(c.dirty.value)
    }

    @Test fun setDocuments_resets_dirty_and_bookkeeping() {
        val c = controller()
        c.setDocuments(listOf(item(0), item(1)))
        c.rename(0, "x")
        assertTrue(c.dirty.value)
        c.setDocuments(listOf(item(0), item(1)))     // reload
        assertFalse(c.dirty.value)
        c.save()
        assertEquals(emptySet(), savedChanged)         // reload cleared the changed set
    }

    @Test fun moveItem_reorders_marks_range_changed_and_dirty() {
        val c = controller()
        c.setDocuments(listOf(item(0), item(1), item(2)))
        c.moveItem(0, 2)
        assertEquals(listOf(1L, 2L, 0L), c.documents.value.map { it.id })
        assertTrue(c.dirty.value)
        c.save()
        assertEquals(listOf(1L, 2L, 0L), savedOrder)
        assertEquals(setOf(0L, 1L, 2L), savedChanged)  // every position in [0,2] shifted
    }

    @Test fun moveItem_noop_when_from_equals_to() {
        val c = controller()
        c.setDocuments(listOf(item(0), item(1)))
        c.moveItem(1, 1)
        assertFalse(c.dirty.value)
    }

    @Test fun rename_updates_row_and_changed_set() {
        val c = controller()
        c.setDocuments(listOf(item(0, "old"), item(1)))
        c.rename(0, "new")
        assertEquals("new", c.documents.value.first { it.id == 0L }.name)
        c.save()
        assertEquals(setOf(0L), savedChanged)
    }

    @Test fun editDescription_updates_row_and_changed_set() {
        val c = controller()
        c.setDocuments(listOf(item(0)))
        c.editDescription(0, "desc")
        assertEquals("desc", c.documents.value.first().description)
        c.save()
        assertEquals(setOf(0L), savedChanged)
    }

    @Test fun addDocument_appends_and_dirties() {
        val c = controller()
        c.setDocuments(listOf(item(0)))
        c.addDocument(item(1, "imported"))
        assertEquals(listOf(0L, 1L), c.documents.value.map { it.id })
        assertTrue(c.dirty.value)
    }

    @Test fun delete_removes_row_records_toDelete_and_dirties() {
        val c = controller()
        c.setDocuments(listOf(item(0), item(1)))
        c.delete(0)
        assertEquals(listOf(1L), c.documents.value.map { it.id })
        assertTrue(c.dirty.value)
        c.save()
        assertEquals(setOf(0L), savedDeleted)
        assertEquals(listOf(1L), savedOrder)
    }

    @Test fun save_payload_shape_order_changed_deleted() {
        val c = controller()
        c.setDocuments(listOf(item(0), item(1), item(2)))
        c.rename(1, "r")
        c.delete(2)
        c.moveItem(0, 1)                               // order now [1,0]
        c.save()
        assertEquals(listOf(1L, 0L), savedOrder)
        assertTrue(savedChanged!!.containsAll(setOf(0L, 1L)))
        assertEquals(setOf(2L), savedDeleted)
    }

    @Test fun query_filters_on_name_and_description_case_insensitively() {
        val c = controller()
        c.setDocuments(listOf(
            item(0, "Sermon notes"), item(1, "Romans"), item(2, "Misc"),
        ))
        c.setQuery("rom")
        assertEquals(listOf(1L), c.documents.value.map { it.id })
        assertTrue(c.filtering.value)
    }

    @Test fun blank_query_republishes_everything_and_clears_filtering() {
        val c = controller()
        c.setDocuments(listOf(item(0), item(1)))
        c.setQuery("zzz")
        assertTrue(c.documents.value.isEmpty())
        c.setQuery("   ")
        assertEquals(listOf(0L, 1L), c.documents.value.map { it.id })
        assertFalse(c.filtering.value)
    }

    @Test fun closeSearch_clears_the_query_and_the_filter() {
        val c = controller()
        c.setDocuments(listOf(item(0), item(1)))
        c.openSearch()
        assertTrue(c.searchModeActive.value)
        c.setQuery("zzz")
        c.closeSearch()
        assertFalse(c.searchModeActive.value)
        assertEquals("", c.query.value)
        assertFalse(c.filtering.value)
        assertEquals(listOf(0L, 1L), c.documents.value.map { it.id })
    }

    @Test fun moveItem_is_a_noop_while_filtering() {
        val c = controller()
        c.setDocuments(listOf(item(0), item(1), item(2)))
        c.setQuery("x")                        // matches nothing, but filtering is on
        c.moveItem(0, 2)
        assertFalse(c.dirty.value)
        c.setQuery("")
        assertEquals(listOf(0L, 1L, 2L), c.documents.value.map { it.id })
    }

    @Test fun save_reports_the_full_order_even_while_filtered() {
        val c = controller()
        c.setDocuments(listOf(item(0, "aaa"), item(1, "bbb"), item(2, "ccc")))
        c.setQuery("bbb")
        assertEquals(listOf(1L), c.documents.value.map { it.id })
        c.save()
        assertEquals(listOf(0L, 1L, 2L), savedOrder)   // NOT the filtered list
    }

    @Test fun rename_while_filtered_survives_clearing_the_filter() {
        val c = controller()
        c.setDocuments(listOf(item(0, "aaa"), item(1, "bbb")))
        c.setQuery("bbb")
        c.rename(1, "renamed")
        c.setQuery("")
        assertEquals("renamed", c.documents.value.first { it.id == 1L }.name)
    }

    @Test fun totalCount_stays_unfiltered() {
        val c = controller()
        c.setDocuments(listOf(item(0, "aaa"), item(1, "bbb"), item(2, "ccc")))
        c.setQuery("bbb")
        assertEquals(1, c.documents.value.size)
        assertEquals(3, c.totalCount.value)
    }

    @Test fun setDocuments_resets_query_and_search_mode() {
        val c = controller()
        c.setDocuments(listOf(item(0)))
        c.openSearch(); c.setQuery("x")
        c.setDocuments(listOf(item(0), item(1)))
        assertEquals("", c.query.value)
        assertFalse(c.filtering.value)
        assertFalse(c.searchModeActive.value)
    }

    @Test fun query_matches_the_description_alone() {
        val c = controller()
        c.setDocuments(listOf(item(0, "aaa", "weekly outlines"), item(1, "bbb", "")))
        c.setQuery("outlines")
        assertEquals(listOf(0L), c.documents.value.map { it.id })
    }
}

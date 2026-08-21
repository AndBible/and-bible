package net.bible.sharedcore.mydocuments

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MyDocumentPagesControllerTest {
    private fun page(id: Long, name: String = "p$id", type: ContentType = ContentType.MARKDOWN) =
        MyDocPageItem(id, name, type, isAiGenerated = false)

    private var savedOrder: List<Long>? = null
    private var savedChanged: Set<Long>? = null
    private var savedDeleted: Set<Long>? = null
    private var createdType: ContentType? = null
    private var exportedIds: List<Long>? = null

    private fun controller() = MyDocumentPagesController(
        onOpenPage = {}, onImport = {}, onExport = {},
        onCreatePage = { _, type -> createdType = type },
        onExportSelected = { ids -> exportedIds = ids },
        onSave = { o, c, d -> savedOrder = o; savedChanged = c; savedDeleted = d },
    )

    @Test fun starts_clean_and_empty() {
        val c = controller(); assertTrue(c.pages.value.isEmpty()); assertFalse(c.dirty.value)
    }

    @Test fun moveItem_reorders_marks_range_changed_and_dirty() {
        val c = controller()
        c.setPages(listOf(page(0), page(1), page(2)))
        c.moveItem(2, 0)
        assertEquals(listOf(2L, 0L, 1L), c.pages.value.map { it.id })
        c.save()
        assertEquals(listOf(2L, 0L, 1L), savedOrder)
        assertEquals(setOf(0L, 1L, 2L), savedChanged)
    }

    @Test fun rename_updates_row_and_changed() {
        val c = controller(); c.setPages(listOf(page(0, "old")))
        c.rename(0, "new"); assertEquals("new", c.pages.value.first().name)
        c.save(); assertEquals(setOf(0L), savedChanged)
    }

    @Test fun addPage_appends_dirties_and_carries_content_type() {
        val c = controller(); c.setPages(emptyList())
        c.addPage(page(0, "html", ContentType.HTML))
        assertEquals(ContentType.HTML, c.pages.value.first().contentType)
        assertTrue(c.dirty.value)
    }

    @Test fun createPage_forwards_type_to_seam() {
        val c = controller(); c.createPage("x", ContentType.HTML)
        assertEquals(ContentType.HTML, createdType)
    }

    @Test fun delete_removes_records_toDelete_and_dirties() {
        val c = controller(); c.setPages(listOf(page(0), page(1)))
        c.delete(1); assertEquals(listOf(0L), c.pages.value.map { it.id })
        c.save(); assertEquals(setOf(1L), savedDeleted)
    }

    @Test fun setPages_resets_dirty_and_bookkeeping() {
        val c = controller(); c.setPages(listOf(page(0)))
        c.rename(0, "x"); assertTrue(c.dirty.value)
        c.setPages(listOf(page(0))); assertFalse(c.dirty.value)
        c.save(); assertEquals(emptySet(), savedChanged)
    }

    @Test fun query_filters_on_name_case_insensitively() {
        val c = controller()
        c.setPages(listOf(page(0, "Chapter one"), page(1, "Appendix")))
        c.setQuery("CHAP")
        assertEquals(listOf(0L), c.pages.value.map { it.id })
        assertTrue(c.filtering.value)
    }

    @Test fun blank_query_republishes_everything() {
        val c = controller()
        c.setPages(listOf(page(0), page(1)))
        c.setQuery("zzz")
        assertTrue(c.pages.value.isEmpty())
        c.setQuery("")
        assertEquals(listOf(0L, 1L), c.pages.value.map { it.id })
        assertFalse(c.filtering.value)
    }

    @Test fun closeSearch_clears_query_filter_and_selection() {
        val c = controller()
        c.setPages(listOf(page(0), page(1)))
        c.openSearch()
        c.toggleSelect(0)
        c.setQuery("zzz")
        c.closeSearch()
        assertFalse(c.searchModeActive.value)
        assertEquals("", c.query.value)
        assertFalse(c.filtering.value)
        assertTrue(c.selection.value.isEmpty())
    }

    @Test fun moveItem_is_a_noop_while_filtering() {
        val c = controller()
        c.setPages(listOf(page(0), page(1), page(2)))
        c.setQuery("x")
        c.moveItem(0, 2)
        assertFalse(c.dirty.value)
    }

    @Test fun save_reports_the_full_order_even_while_filtered() {
        val c = controller()
        c.setPages(listOf(page(0, "aaa"), page(1, "bbb"), page(2, "ccc")))
        c.setQuery("bbb")
        c.save()
        assertEquals(listOf(0L, 1L, 2L), savedOrder)
    }

    @Test fun deleteSelected_deletes_every_selected_page() {
        val c = controller()
        c.setPages(listOf(page(0), page(1), page(2)))
        c.toggleSelect(0); c.toggleSelect(2)
        c.deleteSelected()
        assertEquals(listOf(1L), c.pages.value.map { it.id })
        assertTrue(c.selection.value.isEmpty())
        c.save()
        assertEquals(setOf(0L, 2L), savedDeleted)
    }

    @Test fun exportSelected_reports_the_ids_then_leaves_selection_mode() {
        val c = controller()
        c.setPages(listOf(page(0), page(1)))
        c.toggleSelect(1)
        c.exportSelected()
        assertEquals(listOf(1L), exportedIds)
        assertTrue(c.selection.value.isEmpty())
    }

    @Test fun setQuery_clears_the_selection() {
        val c = controller()
        c.setPages(listOf(page(0, "aaa"), page(1, "bbb")))
        c.toggleSelect(0)
        c.setQuery("bbb")
        assertTrue(c.selection.value.isEmpty())
    }
}

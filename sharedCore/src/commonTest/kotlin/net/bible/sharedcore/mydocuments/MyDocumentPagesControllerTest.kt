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

    private fun controller() = MyDocumentPagesController(
        onOpenPage = {}, onImport = {}, onExport = {},
        onCreatePage = { _, type -> createdType = type },
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
}

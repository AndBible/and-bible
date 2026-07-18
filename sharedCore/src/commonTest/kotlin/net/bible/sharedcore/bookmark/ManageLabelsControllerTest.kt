package net.bible.sharedcore.bookmark

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ManageLabelsControllerTest {

    private fun label(id: String, name: String, favourite: Boolean = false, isUnlabeled: Boolean = false) = LabelItem(
        id = id, name = name, color = 1, favourite = favourite, isUnlabeled = isUnlabeled,
        isSpecial = false, customIcon = null, hasOverride = false,
    )

    private class FakeService(
        private val labels: List<LabelItem>,
        private val recent: List<String> = emptyList(),
        private val overridden: Set<String> = emptySet(),
        private val unlabeled: LabelItem = LabelItem("UNL", "Unlabeled", 0, false, true, true, null, false),
    ) : ManageLabelsService {
        override fun assignableLabels() = labels
        override fun unlabeledLabel() = unlabeled
        override fun recentLabelIds() = recent
        override fun overriddenLabelIds() = overridden
        override fun randomColorArgb() = 0x11223344
    }

    private fun controller(
        mode: ManageLabelsMode,
        labels: List<LabelItem>,
        recent: List<String> = emptyList(),
        overridden: Set<String> = emptySet(),
        initialSelected: Set<String> = emptySet(),
        initialAutoAssign: Set<String> = emptySet(),
        initialAutoAssignPrimary: String? = null,
        initialBookmarkPrimary: String? = null,
        highlightLabelId: String? = null,
        unlabeled: LabelItem = LabelItem("UNL", "Unlabeled", 0, false, true, true, null, false),
    ): ManageLabelsController = ManageLabelsController(
        mode = mode,
        service = FakeService(labels, recent, overridden, unlabeled),
        scope = CoroutineScope(Dispatchers.Unconfined),
        initialSelected = initialSelected,
        initialAutoAssign = initialAutoAssign,
        initialAutoAssignPrimary = initialAutoAssignPrimary,
        initialBookmarkPrimary = initialBookmarkPrimary,
        highlightLabelId = highlightLabelId,
        onEditLabel = {},
        onSelectStudyPad = {},
        onSave = {},
        onReset = {},
    )

    /** Flattens rows to ids ("H_<CATEGORY>" for headers, label id for items) for order assertions. */
    private fun describe(rows: List<ManageLabelsRow>): List<String> = rows.map {
        when (it) {
            is ManageLabelsRow.Header -> "H_${it.category}"
            is ManageLabelsRow.Item -> it.label.id
        }
    }

    private val A = label("A", "Apple")
    private val B = label("B", "Banana")
    private val C = label("C", "Cherry")
    private val D = label("D", "Date")
    private val E = label("E", "Elderberry")
    private val F = label("F", "Fig")

    @Test fun categorization_and_sort_order_ASSIGN() {
        val c = controller(
            mode = ManageLabelsMode.ASSIGN,
            labels = listOf(F, E, D, C, B, A), // scrambled input order
            recent = listOf("B", "D"),
            initialSelected = setOf("A", "C"),
        )
        assertEquals(
            listOf("H_ACTIVE", "A", "C", "H_RECENT", "B", "D", "H_OTHER", "E", "F"),
            describe(c.rows.value),
        )
    }

    @Test fun hideCategories_STUDYPAD_no_headers_alpha_sorted() {
        val c = controller(mode = ManageLabelsMode.STUDYPAD, labels = listOf(C, A, B))
        assertEquals(listOf("A", "B", "C"), describe(c.rows.value))
    }

    @Test fun showUnassigned_WORKSPACE_inserts_unlabeled() {
        val c = controller(mode = ManageLabelsMode.WORKSPACE, labels = listOf(A))
        assertTrue(describe(c.rows.value).contains("UNL"))
    }

    @Test fun showUnassigned_ASSIGN_does_not_insert_unlabeled() {
        val c = controller(mode = ManageLabelsMode.ASSIGN, labels = listOf(A))
        assertFalse(describe(c.rows.value).contains("UNL"))
    }

    @Test fun showUnassigned_suppressed_once_already_in_changed() {
        val c = controller(mode = ManageLabelsMode.WORKSPACE, labels = listOf(A))
        assertEquals(1, describe(c.rows.value).count { it == "UNL" })
        // host applies a round-trip edit of the special Unlabeled label -> it lands in `labels`
        // AND `changed`, so the separate special-insertion path must not duplicate it.
        c.applyLabelChanged(label("UNL", "Unlabeled", isUnlabeled = true), null, null, null)
        assertEquals(1, describe(c.rows.value).count { it == "UNL" })
    }

    @Test fun toggleChecked_sets_and_reassigns_primary() {
        val c = controller(mode = ManageLabelsMode.ASSIGN, labels = listOf(A, B))

        c.toggleChecked("A")
        var aRow = c.rows.value.filterIsInstance<ManageLabelsRow.Item>().first { it.label.id == "A" }
        assertTrue(aRow.checked); assertTrue(aRow.isPrimary)

        c.toggleChecked("B")
        val bRow = c.rows.value.filterIsInstance<ManageLabelsRow.Item>().first { it.label.id == "B" }
        assertTrue(bRow.checked); assertFalse(bRow.isPrimary) // primary stays A

        c.toggleChecked("A") // uncheck A -> primary reassigned to remaining B
        aRow = c.rows.value.filterIsInstance<ManageLabelsRow.Item>().first { it.label.id == "A" }
        val bRow2 = c.rows.value.filterIsInstance<ManageLabelsRow.Item>().first { it.label.id == "B" }
        assertFalse(aRow.checked)
        assertTrue(bRow2.isPrimary)

        c.toggleChecked("B") // uncheck last selection -> primary cleared
        assertEquals(emptySet<String>(), c.resultSelected())
        assertNull(c.resultBookmarkPrimary())
    }

    @Test fun toggleAutoAssign_WORKSPACE_mirrors_primary_logic() {
        val c = controller(mode = ManageLabelsMode.WORKSPACE, labels = listOf(A, B))

        c.toggleAutoAssign("A")
        assertEquals(setOf("A"), c.resultAutoAssign())
        assertEquals("A", c.resultAutoAssignPrimary())

        c.toggleAutoAssign("B")
        assertEquals(setOf("A", "B"), c.resultAutoAssign())
        assertEquals("A", c.resultAutoAssignPrimary()) // unchanged

        c.toggleAutoAssign("A") // remove primary -> reassigned to remaining B
        assertEquals(setOf("B"), c.resultAutoAssign())
        assertEquals("B", c.resultAutoAssignPrimary())
    }

    @Test fun toggleFavourite_flips_and_marks_changed() {
        val c = controller(mode = ManageLabelsMode.STUDYPAD, labels = listOf(A))
        assertFalse(c.resultChanged().contains("A"))

        c.toggleFavourite("A")
        assertTrue(c.resultChanged().contains("A"))
        val row = c.rows.value.filterIsInstance<ManageLabelsRow.Item>().first { it.label.id == "A" }
        assertTrue(row.label.favourite)

        c.toggleFavourite("A")
        val row2 = c.rows.value.filterIsInstance<ManageLabelsRow.Item>().first { it.label.id == "A" }
        assertFalse(row2.label.favourite)
    }

    @Test fun applyLabelDeleted_reassigns_primary_and_cleans_changed() {
        val c = controller(
            mode = ManageLabelsMode.ASSIGN,
            labels = listOf(A, B),
            initialSelected = setOf("A", "B"),
            initialBookmarkPrimary = "A",
        )
        c.toggleFavourite("A") // marks "A" as changed
        assertTrue(c.resultChanged().contains("A"))

        c.applyLabelDeleted("A", orphaned = false)
        assertEquals(setOf("B"), c.resultSelected())
        assertEquals("B", c.resultBookmarkPrimary()) // reassigned to remaining member
        assertFalse(c.resultChanged().contains("A")) // dropped from changed
        assertTrue(c.resultDeleted().contains("A"))

        c.applyLabelDeleted("B", orphaned = true)
        assertEquals(emptySet<String>(), c.resultSelected())
        assertNull(c.resultBookmarkPrimary()) // set now empty -> reassigned to null
        assertTrue(c.resultDeletedWithOrphaned().contains("B"))
    }

    @Test fun applyLabelChanged_primaryFlag_false_reassigns_primary_away() {
        val c = controller(
            mode = ManageLabelsMode.ASSIGN,
            labels = listOf(A, B),
            initialSelected = setOf("B", "A"), // "B" precedes "A" -> firstOrNull() picks it on reassignment
            initialBookmarkPrimary = "A",
        )
        // "A" comes back from the edit round-trip still selected but with isThisBookmarkPrimary == false:
        // ensureNotBookmarkPrimaryLabel must still fire (primary == this label's id) and reassign away,
        // even though the label itself remains in the selected set.
        c.applyLabelChanged(A.copy(name = "Apple2"), selectedFlag = true, autoAssignFlag = null, primaryFlag = false)
        assertEquals(setOf("B", "A"), c.resultSelected())
        assertEquals("B", c.resultBookmarkPrimary())
    }

    @Test fun toggleChecked_HIDELABELS_no_primary_but_selection_toggles() {
        val c = controller(mode = ManageLabelsMode.HIDELABELS, labels = listOf(A, B))
        assertTrue(ManageLabelsMode.HIDELABELS.showCheckboxes)
        assertFalse(ManageLabelsMode.HIDELABELS.primaryShown)

        c.toggleChecked("A")
        assertEquals(setOf("A"), c.resultSelected())
        assertNull(c.resultBookmarkPrimary())
        assertNull(c.resultAutoAssignPrimary())

        c.toggleChecked("B")
        assertEquals(setOf("A", "B"), c.resultSelected())
        assertNull(c.resultBookmarkPrimary())

        c.toggleChecked("A")
        assertEquals(setOf("B"), c.resultSelected())
        assertNull(c.resultBookmarkPrimary())
    }

    @Test fun name_filter_NAME_START_vs_NAME_CONTAINS() {
        val antelope = label("ANT", "Antelope")
        val banana = label("BAN", "Banana")
        val cantaloupe = label("CAN", "Cantaloupe")
        val c = controller(mode = ManageLabelsMode.STUDYPAD, labels = listOf(antelope, banana, cantaloupe))

        c.setSearch("an")
        assertEquals(listOf("ANT"), describe(c.rows.value)) // NAME_START (default): only "Antelope" starts with "an"

        c.setNameSearchInside(true)
        assertEquals(listOf("ANT", "BAN", "CAN"), describe(c.rows.value)) // NAME_CONTAINS: all three contain "an"
    }
}

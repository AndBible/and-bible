package net.bible.sharedcore.bookmark

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ManageLabelsModelTest {

    @Test fun getters_truth_table() {
        // showUnassigned: HIDELABELS, WORKSPACE
        assertFalse(ManageLabelsMode.STUDYPAD.showUnassigned)
        assertTrue(ManageLabelsMode.WORKSPACE.showUnassigned)
        assertFalse(ManageLabelsMode.ASSIGN.showUnassigned)
        assertTrue(ManageLabelsMode.HIDELABELS.showUnassigned)

        // showCheckboxes: HIDELABELS, ASSIGN
        assertFalse(ManageLabelsMode.STUDYPAD.showCheckboxes)
        assertFalse(ManageLabelsMode.WORKSPACE.showCheckboxes)
        assertTrue(ManageLabelsMode.ASSIGN.showCheckboxes)
        assertTrue(ManageLabelsMode.HIDELABELS.showCheckboxes)

        // hasResetButton: WORKSPACE, HIDELABELS
        assertFalse(ManageLabelsMode.STUDYPAD.hasResetButton)
        assertTrue(ManageLabelsMode.WORKSPACE.hasResetButton)
        assertFalse(ManageLabelsMode.ASSIGN.hasResetButton)
        assertTrue(ManageLabelsMode.HIDELABELS.hasResetButton)

        // hasReOrderButton: HIDELABELS, ASSIGN
        assertFalse(ManageLabelsMode.STUDYPAD.hasReOrderButton)
        assertFalse(ManageLabelsMode.WORKSPACE.hasReOrderButton)
        assertTrue(ManageLabelsMode.ASSIGN.hasReOrderButton)
        assertTrue(ManageLabelsMode.HIDELABELS.hasReOrderButton)

        // workspaceEdits: WORKSPACE, ASSIGN
        assertFalse(ManageLabelsMode.STUDYPAD.workspaceEdits)
        assertTrue(ManageLabelsMode.WORKSPACE.workspaceEdits)
        assertTrue(ManageLabelsMode.ASSIGN.workspaceEdits)
        assertFalse(ManageLabelsMode.HIDELABELS.workspaceEdits)

        // primaryShown: WORKSPACE, ASSIGN
        assertFalse(ManageLabelsMode.STUDYPAD.primaryShown)
        assertTrue(ManageLabelsMode.WORKSPACE.primaryShown)
        assertTrue(ManageLabelsMode.ASSIGN.primaryShown)
        assertFalse(ManageLabelsMode.HIDELABELS.primaryShown)

        // showActiveCategory: ASSIGN, HIDELABELS
        assertFalse(ManageLabelsMode.STUDYPAD.showActiveCategory)
        assertFalse(ManageLabelsMode.WORKSPACE.showActiveCategory)
        assertTrue(ManageLabelsMode.ASSIGN.showActiveCategory)
        assertTrue(ManageLabelsMode.HIDELABELS.showActiveCategory)

        // hideCategories: STUDYPAD
        assertTrue(ManageLabelsMode.STUDYPAD.hideCategories)
        assertFalse(ManageLabelsMode.WORKSPACE.hideCategories)
        assertFalse(ManageLabelsMode.ASSIGN.hideCategories)
        assertFalse(ManageLabelsMode.HIDELABELS.hideCategories)
    }

    private fun label(id: String = "L1") = LabelItem(
        id = id,
        name = "Study",
        color = -0xff0100,
        favourite = false,
        isUnlabeled = false,
        isSpecial = false,
        customIcon = null,
    )

    @Test fun labelItem_is_a_plain_value_holder() {
        val a = label()
        val b = label()
        assertEquals(a, b)
        assertEquals("L1", a.id)
        assertEquals(-0xff0100, a.color)
    }

    @Test fun manageLabelsRow_header_carries_category() {
        val header = ManageLabelsRow.Header(LabelCategory.RECENT)
        assertEquals(LabelCategory.RECENT, header.category)
    }

    @Test fun manageLabelsRow_item_carries_resolved_control_states() {
        val row = ManageLabelsRow.Item(
            label = label(),
            checked = true,
            isAutoAssign = false,
            isPrimary = true,
            highlighted = false,
        )
        assertTrue(row.checked)
        assertFalse(row.isAutoAssign)
        assertTrue(row.isPrimary)
        assertFalse(row.highlighted)
        assertEquals("L1", row.label.id)
    }

    @Test fun manageLabelsRow_searchResult_carries_all_fields() {
        val row = ManageLabelsRow.SearchResult(
            labelId = "L1",
            name = "Study",
            color = -0xff0100,
            matchCount = 3,
            snippet = "…quick brown fox…",
            matchStart = 8,
            matchEnd = 13,
            firstMatchEntryId = "E1",
        )
        assertEquals("L1", row.labelId)
        assertEquals("Study", row.name)
        assertEquals(-0xff0100, row.color)
        assertEquals(3, row.matchCount)
        assertEquals("…quick brown fox…", row.snippet)
        assertEquals(8, row.matchStart)
        assertEquals(13, row.matchEnd)
        assertEquals("E1", row.firstMatchEntryId)
    }

    @Test fun manageLabelsRow_searchResult_firstMatchEntryId_may_be_null() {
        val row = ManageLabelsRow.SearchResult(
            labelId = "L1", name = "Study", color = 0, matchCount = 0,
            snippet = "", matchStart = 0, matchEnd = 0, firstMatchEntryId = null,
        )
        assertEquals(null, row.firstMatchEntryId)
    }
}

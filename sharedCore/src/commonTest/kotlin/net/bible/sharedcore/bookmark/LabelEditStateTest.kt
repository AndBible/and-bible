package net.bible.sharedcore.bookmark

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LabelEditStateTest {
    private fun base() = LabelEditState(
        labelId = "L1", name = "Study", color = -0xff0100, customIcon = null,
        selectionStyle = BookmarkDisplayStyle.MARKER, wholeVerseStyle = BookmarkDisplayStyle.HIGHLIGHT,
        favourite = false, isAssigning = false,
        thisBookmarkSelected = false, thisBookmarkPrimary = false, hasWorkspaceContext = false,
        autoAssign = false, autoAssignPrimary = false, overrideMode = OverrideMode.NONE,
        isSpecialLabel = false, isSpeakLabel = false,
    )

    @Test fun primary_enabled_follows_selection_flags() {
        assertFalse(base().copy(thisBookmarkSelected = false).thisBookmarkPrimaryEnabled)
        assertTrue(base().copy(thisBookmarkSelected = true).thisBookmarkPrimaryEnabled)
        assertFalse(base().copy(autoAssign = false).autoAssignPrimaryEnabled)
        assertTrue(base().copy(autoAssign = true).autoAssignPrimaryEnabled)
    }

    @Test fun special_label_hides_editable_groups() {
        val s = base().copy(isSpecialLabel = true, hasWorkspaceContext = true)
        assertFalse(s.nameEditable)
        assertFalse(s.favouriteVisible)
        assertFalse(s.workspaceGroupVisible)
    }

    @Test fun assigning_gates_this_bookmark_group_and_speak_hides_icon() {
        assertTrue(base().copy(isAssigning = true).thisBookmarkGroupVisible)
        assertFalse(base().copy(isAssigning = false).thisBookmarkGroupVisible)
        assertFalse(base().copy(isSpeakLabel = true).customIconVisible)
    }
}

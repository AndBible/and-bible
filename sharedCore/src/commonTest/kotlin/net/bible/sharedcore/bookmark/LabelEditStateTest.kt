package net.bible.sharedcore.bookmark

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LabelEditStateTest {
    private fun base() = LabelEditState(
        labelId = "L1", name = "Study", color = -0xff0100, customIcon = null,
        underline = false, underlineWholeVerse = false, marker = true, markerWholeVerse = false,
        hide = false, hideWholeVerse = false, favourite = false, isAssigning = false,
        thisBookmarkSelected = false, thisBookmarkPrimary = false, hasWorkspaceContext = false,
        autoAssign = false, autoAssignPrimary = false, overrideMode = OverrideMode.NONE,
        isSpecialLabel = false, isSpeakLabel = false,
    )

    @Test fun underline_disabled_when_marker_or_hide_set() {
        assertFalse(base().copy(marker = true).underlineEnabled)
        assertFalse(base().copy(marker = false, hide = true).underlineEnabled)
        assertTrue(base().copy(marker = false, hide = false).underlineEnabled)
    }

    @Test fun markerWholeVerse_disabled_only_by_hideWholeVerse() {
        assertFalse(base().copy(hideWholeVerse = true).markerWholeVerseEnabled)
        assertTrue(base().copy(hideWholeVerse = false).markerWholeVerseEnabled)
    }

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

package net.bible.sharedcore.bookmark

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
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

    /** The editor's workspace section shows the override as a style tag and a preview, so it needs
     *  the override expressed as a display style rather than as its own enum. NONE is `null`, i.e.
     *  "no override" — the caller falls back to the label's own style. */
    @Test
    fun overrideMode_maps_to_the_display_style_it_imposes() {
        assertNull(OverrideMode.NONE.displayStyle)
        assertEquals(BookmarkDisplayStyle.HIGHLIGHT, OverrideMode.HIGHLIGHT.displayStyle)
        assertEquals(BookmarkDisplayStyle.UNDERLINE, OverrideMode.UNDERLINE.displayStyle)
        assertEquals(BookmarkDisplayStyle.MARKER, OverrideMode.MARKER.displayStyle)
        assertEquals(BookmarkDisplayStyle.HIDDEN, OverrideMode.HIDDEN.displayStyle)
    }

    @Test
    fun every_override_mode_except_NONE_has_a_style() {
        val withoutStyle = OverrideMode.entries.filter { it.displayStyle == null }
        assertEquals(listOf(OverrideMode.NONE), withoutStyle)
    }
}

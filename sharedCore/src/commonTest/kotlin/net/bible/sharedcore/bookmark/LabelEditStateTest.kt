package net.bible.sharedcore.bookmark

import kotlin.test.Test
import kotlin.test.assertEquals
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

    @Test
    fun `hide dominates marker and underline on the selection axis`() {
        assertEquals(BookmarkDisplayStyle.HIDDEN, styleOf(hide = true, marker = true, underline = true))
        assertEquals(BookmarkDisplayStyle.HIDDEN, styleOf(hide = true, marker = false, underline = false))
    }

    @Test
    fun `marker dominates underline`() {
        assertEquals(BookmarkDisplayStyle.MARKER, styleOf(hide = false, marker = true, underline = true))
    }

    @Test
    fun `underline applies when nothing dominates it`() {
        assertEquals(BookmarkDisplayStyle.UNDERLINE, styleOf(hide = false, marker = false, underline = true))
    }

    @Test
    fun `highlight is the fall-through`() {
        assertEquals(BookmarkDisplayStyle.HIGHLIGHT, styleOf(hide = false, marker = false, underline = false))
    }

    @Test
    fun `all sixty-four boolean combinations map onto the sixteen enum pairs`() {
        val pairs = mutableSetOf<Pair<BookmarkDisplayStyle, BookmarkDisplayStyle>>()
        for (bits in 0 until 64) {
            val s = bookmarkDisplayStyleOf(
                hide = bits and 1 != 0, marker = bits and 2 != 0, underline = bits and 4 != 0,
            )
            val w = bookmarkDisplayStyleOf(
                hide = bits and 8 != 0, marker = bits and 16 != 0, underline = bits and 32 != 0,
            )
            pairs.add(s to w)
        }
        assertEquals(16, pairs.size)
    }

    @Test
    fun `expanding a style and reading it back is idempotent`() {
        for (style in BookmarkDisplayStyle.entries) {
            val flags = bookmarkStyleFlagsOf(style)
            assertEquals(
                style,
                bookmarkDisplayStyleOf(hide = flags.hide, marker = flags.marker, underline = flags.underline),
            )
        }
    }

    private fun styleOf(hide: Boolean, marker: Boolean, underline: Boolean) =
        bookmarkDisplayStyleOf(hide = hide, marker = marker, underline = underline)
}

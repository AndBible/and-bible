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
    fun `all sixty-four boolean combinations map onto the expected style`() {
        // Each iteration pins the ACTUAL expected value (computed independently of
        // bookmarkDisplayStyleOf below), not just that 16 distinct pairs turn up -- a mapping that
        // swapped, say, (hide=true, underline=true) to UNDERLINE instead of HIDDEN would still
        // produce 16 distinct pairs overall and pass a cardinality-only check.
        for (bits in 0 until 64) {
            val hideS = bits and 1 != 0
            val markerS = bits and 2 != 0
            val underlineS = bits and 4 != 0
            val hideW = bits and 8 != 0
            val markerW = bits and 16 != 0
            val underlineW = bits and 32 != 0
            val expectedS = expectedStyle(hide = hideS, marker = markerS, underline = underlineS)
            val expectedW = expectedStyle(hide = hideW, marker = markerW, underline = underlineW)
            assertEquals(expectedS, styleOf(hideS, markerS, underlineS), "bits=$bits selection")
            assertEquals(expectedW, styleOf(hideW, markerW, underlineW), "bits=$bits wholeVerse")
        }
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

    /** The precedence rule restated independently of [bookmarkDisplayStyleOf], so the
     *  sixty-four-combination test pins the actual mapping rather than just its cardinality. */
    private fun expectedStyle(hide: Boolean, marker: Boolean, underline: Boolean): BookmarkDisplayStyle = when {
        hide -> BookmarkDisplayStyle.HIDDEN
        marker -> BookmarkDisplayStyle.MARKER
        underline -> BookmarkDisplayStyle.UNDERLINE
        else -> BookmarkDisplayStyle.HIGHLIGHT
    }
}

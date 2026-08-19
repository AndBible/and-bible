package net.bible.sharedcore.bookmark

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertIs

class LabelEditControllerTest {
    private fun state() = LabelEditState(
        labelId = "L1", name = "Study", color = 1, customIcon = null,
        selectionStyle = BookmarkDisplayStyle.HIGHLIGHT, wholeVerseStyle = BookmarkDisplayStyle.UNDERLINE,
        favourite = false, isAssigning = true,
        thisBookmarkSelected = false, thisBookmarkPrimary = true, hasWorkspaceContext = false,
        autoAssign = false, autoAssignPrimary = true, overrideMode = OverrideMode.NONE,
        isSpecialLabel = false, isSpeakLabel = false,
    )
    private fun controller(s: LabelEditState = state(), orphans: Int = 0, onFinish: (LabelEditResult) -> Unit = {}) =
        LabelEditController(s, object : LabelEditService { override fun orphanedBookmarkCount(labelId: String) = orphans },
            CoroutineScope(Dispatchers.Unconfined), onFinish)

    @Test fun clearing_selection_clears_primary() {
        val c = controller(state().copy(thisBookmarkSelected = true, thisBookmarkPrimary = true))
        c.toggleThisBookmarkSelected() // -> false
        assertFalse(c.state.value.thisBookmarkSelected)
        assertFalse(c.state.value.thisBookmarkPrimary)
    }

    @Test fun isDirty_tracks_changes() {
        val c = controller()
        assertFalse(c.isDirty())
        c.setColor(999)
        assertTrue(c.isDirty())
    }

    @Test fun requestDelete_picks_prompt_by_orphan_count() {
        val withOrphans = controller(orphans = 3); withOrphans.requestDelete()
        assertEquals(DeletePrompt.Orphaned(3), withOrphans.deletePrompt.value)
        val none = controller(orphans = 0); none.requestDelete()
        assertEquals(DeletePrompt.Confirm, none.deletePrompt.value)
    }

    @Test fun save_and_delete_emit_results() {
        var result: LabelEditResult? = null
        val c = controller(onFinish = { result = it })
        c.save(); assertIs<LabelEditResult.Save>(result)
        c.confirmDelete(deleteOrphaned = true)
        val d = result; assertIs<LabelEditResult.Delete>(d); assertTrue(d.deleteOrphaned)
    }

    @Test
    fun `setting one axis leaves the other alone`() {
        val start = state()
        val c = controller(start)
        c.setSelectionStyle(BookmarkDisplayStyle.HIDDEN)
        assertEquals(BookmarkDisplayStyle.HIDDEN, c.state.value.selectionStyle)
        assertEquals(start.wholeVerseStyle, c.state.value.wholeVerseStyle)
    }

    @Test
    fun `changing a style makes the editor dirty`() {
        val start = state()
        val c = controller(start)
        assertFalse(c.isDirty())
        // Must differ from `start.selectionStyle`, or this asserts nothing. The file's `state()`
        // builder at `:12` decides that value — set the builder's selectionStyle to HIGHLIGHT and
        // pass MARKER here.
        c.setSelectionStyle(BookmarkDisplayStyle.MARKER)
        assertTrue(c.isDirty())
    }
}

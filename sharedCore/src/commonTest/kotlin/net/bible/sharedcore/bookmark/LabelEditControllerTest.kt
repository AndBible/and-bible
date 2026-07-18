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
        underline = false, underlineWholeVerse = false, marker = false, markerWholeVerse = false,
        hide = false, hideWholeVerse = false, favourite = false, isAssigning = true,
        thisBookmarkSelected = false, thisBookmarkPrimary = true, hasWorkspaceContext = false,
        autoAssign = false, autoAssignPrimary = true, overrideMode = OverrideMode.NONE,
        isSpecialLabel = false, isSpeakLabel = false,
    )
    private fun controller(s: LabelEditState = state(), orphans: Int = 0, onFinish: (LabelEditResult) -> Unit = {}) =
        LabelEditController(s, object : LabelEditService { override fun orphanedBookmarkCount(labelId: String) = orphans },
            CoroutineScope(Dispatchers.Unconfined), onFinish)

    @Test fun disabled_marker_toggle_is_noop() {
        val c = controller(state().copy(hide = true)) // marker disabled
        c.toggleMarker()
        assertFalse(c.state.value.marker)
    }

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
}

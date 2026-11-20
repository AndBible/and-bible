package net.bible.sharedcore.bookmark

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertIs

class LabelEditControllerTest {
    private val copied = mutableListOf<String>()

    private fun state() = LabelEditState(
        labelId = "L1", name = "Study", color = 1, customIcon = null,
        selectionStyle = BookmarkDisplayStyle.HIGHLIGHT, wholeVerseStyle = BookmarkDisplayStyle.UNDERLINE,
        favourite = false, isAssigning = true,
        thisBookmarkSelected = false, thisBookmarkPrimary = true, hasWorkspaceContext = false,
        autoAssign = false, autoAssignPrimary = true, overrideMode = OverrideMode.NONE,
        isSpecialLabel = false, isSpeakLabel = false,
    )
    private fun controller(s: LabelEditState = state(), orphans: Int = 0, onFinish: (LabelEditResult) -> Unit = {}) =
        LabelEditController(s, object : LabelEditService {
            override suspend fun orphanedBookmarkCount(labelId: String) = orphans
            override suspend fun copyAddonLabel(labelId: String): String? { copied += labelId; return "Study (copy)" }
        },
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
        // HIGHLIGHT is state()'s value for selectionStyle, so MARKER here is a real change --
        // this test asserts nothing if the two ever end up equal.
        c.setSelectionStyle(BookmarkDisplayStyle.MARKER)
        assertTrue(c.isDirty())
    }

    @Test
    fun `inherit is selectable and is not the same as an explicit style`() {
        // state() pins wholeVerseStyle = UNDERLINE, so both moves below are real changes.
        val c = controller(state())
        c.setWholeVerseStyle(null)
        assertNull(c.state.value.wholeVerseStyle)
        assertTrue(c.isDirty())
        c.setWholeVerseStyle(BookmarkDisplayStyle.UNDERLINE)
        assertEquals(BookmarkDisplayStyle.UNDERLINE, c.state.value.wholeVerseStyle)
        assertFalse(c.isDirty())
    }

    @Test fun requestUp_when_clean_cancels_immediately_without_asking() {
        var result: LabelEditResult? = null
        val c = controller(onFinish = { result = it })

        c.requestUp()

        assertFalse(c.discardPrompt.value)
        assertIs<LabelEditResult.Cancel>(result)
    }

    @Test fun requestUp_when_dirty_asks_before_discarding() {
        var result: LabelEditResult? = null
        val c = controller(onFinish = { result = it })
        c.setColor(999) // dirty

        c.requestUp()

        assertTrue(c.discardPrompt.value)
        assertNull(result)
    }

    @Test fun confirmDiscard_clears_the_prompt_and_cancels_exactly_once() {
        var cancelCalls = 0
        val c = controller(onFinish = { if (it is LabelEditResult.Cancel) cancelCalls++ })
        c.setColor(999)
        c.requestUp()

        c.confirmDiscard()

        assertFalse(c.discardPrompt.value)
        assertEquals(1, cancelCalls)
    }

    @Test fun confirmDiscard_called_twice_cancels_only_once() {
        var cancelCalls = 0
        val c = controller(onFinish = { if (it is LabelEditResult.Cancel) cancelCalls++ })
        c.setColor(999)
        c.requestUp()

        c.confirmDiscard()
        assertEquals(1, cancelCalls)

        // A stray second confirm (e.g. a double-tap after the dialog closed, or back-then-confirm
        // racing) must not cancel a second time -- there is no longer a discard prompt showing to
        // confirm, so this must be a no-op, matching BookmarksController.confirmDialog() and
        // ManageLabelsController.confirmDialog()'s guard in the same commit.
        c.confirmDiscard()
        assertEquals(1, cancelCalls)
    }

    @Test fun dismissDiscardPrompt_discards_nothing() {
        var cancelCalls = 0
        val c = controller(onFinish = { if (it is LabelEditResult.Cancel) cancelCalls++ })
        c.setColor(999)
        c.requestUp()

        c.dismissDiscardPrompt()

        assertFalse(c.discardPrompt.value)
        assertEquals(0, cancelCalls)
    }

    @Test fun save_normalises_whole_verse_equal_to_selection() {
        var result: LabelEditResult? = null
        val c = controller(
            state().copy(selectionStyle = BookmarkDisplayStyle.HIGHLIGHT, wholeVerseStyle = BookmarkDisplayStyle.MARKER),
            onFinish = { result = it },
        )

        c.setWholeVerseStyle(BookmarkDisplayStyle.HIGHLIGHT)
        // Live state keeps the explicit value: normalising here would flip the editor's checkbox
        // off and collapse the group under the user's finger the moment they picked the matching
        // style.
        assertEquals(BookmarkDisplayStyle.HIGHLIGHT, c.state.value.wholeVerseStyle)

        c.save()
        assertNull((result as LabelEditResult.Save).state.wholeVerseStyle)
    }

    @Test fun save_keeps_a_whole_verse_style_that_differs() {
        var result: LabelEditResult? = null
        val c = controller(
            state().copy(selectionStyle = BookmarkDisplayStyle.HIGHLIGHT, wholeVerseStyle = BookmarkDisplayStyle.UNDERLINE),
            onFinish = { result = it },
        )

        c.save()
        assertEquals(BookmarkDisplayStyle.UNDERLINE, (result as LabelEditResult.Save).state.wholeVerseStyle)
    }

    private fun addonState() = state().copy(
        addonModule = "Memory verses", isAssigning = true, hasWorkspaceContext = true, autoAssign = false,
        autoAssignPrimary = false, thisBookmarkSelected = false, thisBookmarkPrimary = false,
    )

    @Test fun addon_label_is_read_only_but_keeps_favourite_and_workspace_override() {
        val c = controller(addonState())
        val s = c.state.value
        assertFalse(s.nameEditable); assertFalse(s.styleEditable); assertFalse(s.deleteVisible)
        assertFalse(s.thisBookmarkGroupVisible); assertFalse(s.autoAssignVisible)
        assertTrue(s.favouriteVisible); assertTrue(s.workspaceGroupVisible)

        c.setName("Hacked"); c.setColor(5); c.setCustomIcon("star"); c.setSelectionStyle(BookmarkDisplayStyle.HIDDEN)
        c.setWholeVerseStyle(null); c.toggleAutoAssign(); c.toggleThisBookmarkSelected()
        assertEquals(s, c.state.value)
        assertFalse(c.isDirty())

        c.toggleFavourite(); c.setOverrideMode(OverrideMode.HIDDEN)
        assertTrue(c.state.value.favourite)
        assertEquals(OverrideMode.HIDDEN, c.state.value.overrideMode)
    }

    @Test fun addon_label_cannot_be_deleted() {
        val c = controller(addonState())
        c.requestDelete()
        assertNull(c.deletePrompt.value)
        c.confirmDelete(deleteOrphaned = false) // must not report a delete either
    }

    @Test fun copy_to_my_labels_copies_and_finishes() {
        var result: LabelEditResult? = null
        val c = controller(addonState(), onFinish = { result = it })
        c.copyToMyLabels()
        assertEquals(listOf("L1"), copied)
        assertEquals(LabelEditResult.Copied("Study (copy)"), result)
    }

    @Test fun a_second_copy_tap_while_the_first_runs_makes_no_second_copy() {
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        var finishes = 0
        val c = LabelEditController(
            addonState(),
            object : LabelEditService {
                override suspend fun orphanedBookmarkCount(labelId: String) = 0
                override suspend fun copyAddonLabel(labelId: String): String? { copied += labelId; gate.await(); return "Study (copy)" }
            },
            CoroutineScope(Dispatchers.Unconfined),
        ) { finishes++ }
        c.copyToMyLabels(); c.copyToMyLabels()
        gate.complete(Unit)
        assertEquals(listOf("L1"), copied)
        assertEquals(1, finishes)
    }

    @Test fun copy_to_my_labels_is_ignored_for_an_own_label() {
        var result: LabelEditResult? = null
        val c = controller(onFinish = { result = it })
        c.copyToMyLabels()
        assertTrue(copied.isEmpty()); assertNull(result)
    }
}

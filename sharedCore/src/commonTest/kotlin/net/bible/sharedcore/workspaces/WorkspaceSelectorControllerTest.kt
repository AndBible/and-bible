package net.bible.sharedcore.workspaces

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class WorkspaceSelectorControllerTest {
    private fun vd(id: String, name: String = "ws$id", current: Boolean = false) =
        WorkspaceRowVd(id, name, summary = null, colorArgb = 0x444444, isCurrent = current)

    private inner class FakeService(initial: List<WorkspaceRowVd>) : WorkspaceService {
        var working = initial.toMutableList()
        var appliedOrder: List<String>? = null
        var appliedDeleted: List<String>? = null
        var appliedRenamed: Map<String, String>? = null
        var appliedChanged: Set<String>? = null
        var deletedCreated: List<String>? = null
        var copyCall: Triple<String, List<Int>, List<String>>? = null
        var toGlobalCall: Pair<String, List<Int>>? = null
        var savedIntoDb = false
        var nextNewId = 100

        override fun currentWorkspaceId() = working.firstOrNull { it.isCurrent }?.id ?: working.first().id
        override fun saveCurrentIntoDb() { savedIntoDb = true }
        override fun loadAll() = working.toList()
        override fun createWorkspace(name: String): WorkspaceRowVd {
            val v = vd("${nextNewId++}", name); working.add(v); return v
        }
        override fun cloneWorkspace(sourceId: String, name: String): WorkspaceRowVd {
            val v = vd("${nextNewId++}", name)
            working.add(working.indexOfFirst { it.id == sourceId } + 1, v); return v
        }
        override fun applyChanges(orderedIds: List<String>, deletedIds: List<String>, renamed: Map<String, String>, changedIds: Set<String>) {
            appliedOrder = orderedIds; appliedDeleted = deletedIds; appliedRenamed = renamed; appliedChanged = changedIds
        }
        override fun deleteCreated(ids: List<String>) { deletedCreated = ids }
        override fun settingTypeLabels(sourceId: String) = listOf("Font", "Colors", "Margins")
        override fun copySettings(sourceId: String, typeIndices: List<Int>, targetIds: List<String>): List<WorkspaceRowVd> {
            copyCall = Triple(sourceId, typeIndices, targetIds); return targetIds.mapNotNull { t -> working.firstOrNull { it.id == t } }
        }
        override fun copySettingsToGlobal(sourceId: String, typeIndices: List<Int>) { toGlobalCall = sourceId to typeIndices }
        override fun settingsBundleJson(id: String) = "{bundle:$id}"
        override fun applyWorkspaceSettings(id: String, settingsBundleJson: String, reset: Boolean): WorkspaceRowVd {
            val v = working.first { it.id == id }.copy(colorArgb = if (reset) 0x444444 else 0x00FF00)
            working[working.indexOfFirst { it.id == id }] = v; return v
        }
    }

    private var resultId: String? = null
    private var resultChanged: Boolean? = null
    private var canceled = false
    private var editSettingsId: String? = null

    private fun controller(svc: FakeService) = WorkspaceSelectorController(
        service = svc, scope = kotlinx.coroutines.CoroutineScope(UnconfinedTestDispatcher()),
        onResult = { id, ch -> resultId = id; resultChanged = ch },
        onCancel = { canceled = true },
        onEditSettings = { editSettingsId = it },
    ).also { it.load() }

    @Test fun load_populates_clean() = runTest(UnconfinedTestDispatcher()) {
        val c = controller(FakeService(listOf(vd("1", current = true), vd("2"))))
        assertEquals(listOf("1", "2"), c.workspaces.value.map { it.id })
        assertFalse(c.dirty.value)
        assertTrue(c.canDelete.value)
    }

    @Test fun single_workspace_cannot_delete() = runTest(UnconfinedTestDispatcher()) {
        val c = controller(FakeService(listOf(vd("1"))))
        assertFalse(c.canDelete.value)
        c.requestDelete("1")
        assertEquals(listOf("1"), c.workspaces.value.map { it.id })  // guard: not removed
        assertFalse(c.dirty.value)
    }

    @Test fun requestDelete_stages_and_dirties() = runTest(UnconfinedTestDispatcher()) {
        val c = controller(FakeService(listOf(vd("1"), vd("2"))))
        c.requestDelete("1")
        assertEquals(listOf("2"), c.workspaces.value.map { it.id })
        assertTrue(c.dirty.value)
        c.save()
        assertEquals(listOf("1"), svcOf(c).appliedDeleted)
        assertEquals(listOf("2"), svcOf(c).appliedOrder)
    }

    @Test fun moveIndex_reorders_marks_span_changed_dirties() = runTest(UnconfinedTestDispatcher()) {
        val c = controller(FakeService(listOf(vd("1"), vd("2"), vd("3"))))
        c.moveIndex(0, 2)
        assertEquals(listOf("2", "3", "1"), c.workspaces.value.map { it.id })
        c.save()
        assertEquals(listOf("2", "3", "1"), svcOf(c).appliedOrder)
        assertTrue(svcOf(c).appliedChanged!!.containsAll(setOf("1", "2", "3")))
    }

    @Test fun moveIndex_noop_when_from_equals_to() = runTest(UnconfinedTestDispatcher()) {
        val c = controller(FakeService(listOf(vd("1"), vd("2"))))
        c.moveIndex(1, 1); assertFalse(c.dirty.value)
    }

    @Test fun rename_updates_row_marks_changed_and_carries_map() = runTest(UnconfinedTestDispatcher()) {
        val c = controller(FakeService(listOf(vd("1", "old"), vd("2"))))
        c.rename("1", "new")
        assertEquals("new", c.workspaces.value.first { it.id == "1" }.name)
        c.save()
        assertEquals(mapOf("1" to "new"), svcOf(c).appliedRenamed)
        assertTrue(svcOf(c).appliedChanged!!.contains("1"))
    }

    @Test fun clone_inserts_after_source_tracks_created_dirties() = runTest(UnconfinedTestDispatcher()) {
        val svc = FakeService(listOf(vd("1"), vd("2")))
        val c = controller(svc); c.clone("1", "copy")
        assertEquals(3, c.workspaces.value.size)
        assertEquals(1, c.workspaces.value.indexOfFirst { it.name == "copy" })  // inserted right after source "1" (index 0)
        assertTrue(c.dirty.value)
    }

    @Test fun createNew_inserts_and_selects_terminal() = runTest(UnconfinedTestDispatcher()) {
        val svc = FakeService(listOf(vd("1", current = true)))
        val c = controller(svc); c.createNew("brand new")
        // not dirty before create → selectWorkspace applies immediately and finishes with the new id
        assertEquals("100", resultId)
        assertEquals(false, resultChanged)
    }

    @Test fun filter_hides_by_query_and_flags_filtering() = runTest(UnconfinedTestDispatcher()) {
        val c = controller(FakeService(listOf(vd("1", "Alpha"), vd("2", "Beta"))))
        c.setQuery("bet")
        assertEquals(listOf("2"), c.workspaces.value.map { it.id })
        assertTrue(c.filtering.value)
        c.setQuery(""); assertFalse(c.filtering.value)
    }

    @Test fun copySettings_two_stage_flow_applies() = runTest(UnconfinedTestDispatcher()) {
        val svc = FakeService(listOf(vd("1"), vd("2"), vd("3")))
        val c = controller(svc)
        c.beginCopySettings("1")
        assertEquals(CopySettingsState.ChooseTypes("1", listOf("Font", "Colors", "Margins")), c.copySettingsState.value)
        c.chooseCopyTypes(listOf(0, 1))
        val s2 = c.copySettingsState.value
        assertTrue(s2 is CopySettingsState.ChooseTargets && s2.typeIndices == listOf(0, 1))
        c.chooseCopyTargets(listOf("2", "3"))
        assertEquals(Triple("1", listOf(0, 1), listOf("2", "3")), svc.copyCall)
        assertNull(c.copySettingsState.value)          // closed
        assertTrue(c.dirty.value)
        c.save()
        assertTrue(svcOf(c).appliedChanged!!.containsAll(setOf("2", "3")))  // fix-forward: targets persisted
    }

    @Test fun copySettings_empty_type_selection_cancels() = runTest(UnconfinedTestDispatcher()) {
        val c = controller(FakeService(listOf(vd("1"), vd("2"))))
        c.beginCopySettings("1"); c.chooseCopyTypes(emptyList())
        assertNull(c.copySettingsState.value)
    }

    @Test fun copySettingsToGlobal_applies_without_dirty() = runTest(UnconfinedTestDispatcher()) {
        val svc = FakeService(listOf(vd("1")))
        val c = controller(svc)
        c.beginCopySettingsToGlobal("1")
        c.chooseCopyTypes(listOf(2))
        assertEquals("1" to listOf(2), svc.toGlobalCall)
        assertFalse(c.dirty.value)                     // classic writes global immediately, no dirty
        assertNull(c.copySettingsState.value)
    }

    @Test fun applyWorkspaceSettings_refreshes_row_and_marks_changed() = runTest(UnconfinedTestDispatcher()) {
        val c = controller(FakeService(listOf(vd("1"), vd("2"))))
        c.applyWorkspaceSettings("1", "{}", reset = false)
        assertEquals(0x00FF00, c.workspaces.value.first { it.id == "1" }.colorArgb)
        assertTrue(c.dirty.value)
        c.save(); assertTrue(svcOf(c).appliedChanged!!.contains("1"))
    }

    @Test fun dirty_select_prompts_then_saves() = runTest(UnconfinedTestDispatcher()) {
        val c = controller(FakeService(listOf(vd("1"), vd("2"))))
        c.rename("1", "x")
        c.selectWorkspace("2")
        assertEquals("2", c.pendingSelectId.value)     // prompt shown, no result yet
        assertNull(resultId)
        c.confirmPendingSelect(save = true)
        assertEquals("2", resultId); assertEquals(true, resultChanged)
        assertNotNull(svcOf(c).appliedOrder)           // applyChanges ran
    }

    @Test fun dirty_select_no_discards_created_and_returns_id() = runTest(UnconfinedTestDispatcher()) {
        val svc = FakeService(listOf(vd("1"), vd("2")))
        val c = controller(svc); c.clone("1", "tmp")     // created + dirty
        c.selectWorkspace("2"); c.confirmPendingSelect(save = false)
        assertEquals("2", resultId); assertEquals(false, resultChanged)
        assertNotNull(svc.deletedCreated)               // cancel path hard-deletes the created clone
    }

    @Test fun non_dirty_select_finishes_immediately() = runTest(UnconfinedTestDispatcher()) {
        val c = controller(FakeService(listOf(vd("1"), vd("2"))))
        c.selectWorkspace("2")
        assertNull(c.pendingSelectId.value)
        assertEquals("2", resultId); assertEquals(false, resultChanged)
    }

    @Test fun save_button_applies_and_returns_null_id_changed_true() = runTest(UnconfinedTestDispatcher()) {
        val c = controller(FakeService(listOf(vd("1"), vd("2"))))
        c.rename("1", "x"); c.save()
        assertNull(resultId); assertEquals(true, resultChanged)
    }

    @Test fun cancel_deletes_created_and_cancels() = runTest(UnconfinedTestDispatcher()) {
        val svc = FakeService(listOf(vd("1"), vd("2")))
        val c = controller(svc); c.clone("1", "tmp"); c.cancel()
        assertTrue(canceled); assertNotNull(svc.deletedCreated)
    }

    @Test fun editSettings_forwards_to_host() = runTest(UnconfinedTestDispatcher()) {
        val c = controller(FakeService(listOf(vd("1")))); c.editSettings("1")
        assertEquals("1", editSettingsId)
    }

    // Helper: reach the fake behind the controller for assertions.
    private fun svcOf(c: WorkspaceSelectorController): FakeService =
        (WorkspaceSelectorController::class.java.getDeclaredField("service").apply { isAccessible = true }.get(c) as FakeService)
}

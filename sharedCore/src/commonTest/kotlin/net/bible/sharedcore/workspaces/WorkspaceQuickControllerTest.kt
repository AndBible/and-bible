package net.bible.sharedcore.workspaces

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class SpyWorkspaceService(private val all: List<WorkspaceRowVd>) : WorkspaceService {
    val calls = mutableListOf<String>()
    override fun currentWorkspaceId(): String { calls += "currentWorkspaceId"; return all.first { it.isCurrent }.id }
    override fun saveCurrentIntoDb() { calls += "saveCurrentIntoDb" }
    override fun loadAll(): List<WorkspaceRowVd> { calls += "loadAll"; return all }
    override fun createWorkspace(name: String): WorkspaceRowVd { calls += "createWorkspace"; error("must not be called") }
    override fun cloneWorkspace(sourceId: String, name: String): WorkspaceRowVd { calls += "cloneWorkspace"; error("must not be called") }
    override fun applyChanges(orderedIds: List<String>, deletedIds: List<String>, renamed: Map<String, String>, changedIds: Set<String>) { calls += "applyChanges" }
    override fun deleteCreated(ids: List<String>) { calls += "deleteCreated" }
    override fun settingTypeLabels(sourceId: String): List<String> { calls += "settingTypeLabels"; return emptyList() }
    override fun copySettings(sourceId: String, typeIndices: List<Int>, targetIds: List<String>): List<WorkspaceRowVd> { calls += "copySettings"; return emptyList() }
    override fun copySettingsToGlobal(sourceId: String, typeIndices: List<Int>) { calls += "copySettingsToGlobal" }
    override fun settingsBundleJson(id: String): String { calls += "settingsBundleJson"; return "" }
    override fun applyWorkspaceSettings(id: String, settingsBundleJson: String, reset: Boolean): WorkspaceRowVd { calls += "applyWorkspaceSettings"; error("must not be called") }
}

class WorkspaceQuickControllerTest {
    private val rows = listOf(
        WorkspaceRowVd("a", "Study", null, 0xFF1B5E20.toInt(), isCurrent = false),
        WorkspaceRowVd("b", "Devotional", null, 0xFF444444.toInt(), isCurrent = true),
    )

    @Test fun itPublishesTheServiceOrderUnchanged() {
        val svc = SpyWorkspaceService(rows)
        val c = WorkspaceQuickController(svc) {}
        assertEquals(listOf("a", "b"), c.rows.value.map { it.id })
        assertEquals(listOf(true), c.rows.value.map { it.isCurrent }.filter { it })
    }

    @Test fun selectingHandsTheIdToTheHost() {
        var switched: String? = null
        val c = WorkspaceQuickController(SpyWorkspaceService(rows)) { switched = it }
        c.select("a")
        assertEquals("a", switched)
    }

    @Test fun selectingTheCurrentWorkspaceIsANoOp() {
        var switched: String? = null
        val c = WorkspaceQuickController(SpyWorkspaceService(rows)) { switched = it }
        c.select("b")
        assertEquals(null, switched, "the current workspace row is inert, as QuickDocPicker's is")
    }

    /**
     * NOTE (whole-branch review fix C1): this deliberately does NOT mean the outgoing workspace's
     * unsaved state is dropped on a quick switch. `MainBibleActivity.quickSwitchToWorkspace` -- the
     * host's `onSwitch` handler passed into this controller -- calls `windowRepository.saveIntoDb()`
     * BEFORE `switchToWorkspace`, exactly mirroring `cycleWorkspace`'s save call. That save is the
     * HOST's job on purpose: this controller stays read-only over [WorkspaceService], and the save
     * happens one layer up, outside anything this test can see. Do not read this test as forbidding
     * a pre-switch save anywhere in the app -- only as pinning that THIS class never triggers one.
     */
    @Test fun itNeverMutatesTheWorkspaceService() {
        val svc = SpyWorkspaceService(rows)
        val c = WorkspaceQuickController(svc) {}
        c.reload()
        c.select("a")
        val mutating = setOf(
            "saveCurrentIntoDb", "createWorkspace", "cloneWorkspace", "applyChanges", "deleteCreated",
            "copySettings", "copySettingsToGlobal", "applyWorkspaceSettings",
        )
        assertTrue(svc.calls.none { it in mutating }, "quick switch is read-only; saw ${svc.calls}")
    }
}

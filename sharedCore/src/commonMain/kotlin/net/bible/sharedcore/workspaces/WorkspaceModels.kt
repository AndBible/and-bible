package net.bible.sharedcore.workspaces

/** One workspace row. [id] = IdType.toString() (stable addressing key, never a list index). */
data class WorkspaceRowVd(
    val id: String,
    val name: String,
    val summary: String?,      // contentsText
    val colorArgb: Int,        // workspaceColor or defaultWorkspaceColor
    val isCurrent: Boolean,
)

/** Copy-settings two-stage flow state (null = closed). */
sealed interface CopySettingsState {
    /** Stage 1: pick which display-setting TYPES to copy from [sourceId]. */
    data class ChooseTypes(val sourceId: String, val typeLabels: List<String>) : CopySettingsState
    /** Stage 2: pick which target workspaces receive the chosen [typeIndices]. */
    data class ChooseTargets(
        val sourceId: String,
        val typeIndices: List<Int>,
        val targets: List<WorkspaceRowVd>,
    ) : CopySettingsState
    /** Copy the chosen types from [sourceId] into the GLOBAL display settings. */
    data class ToGlobal(val sourceId: String, val typeLabels: List<String>) : CopySettingsState
}

/**
 * Host seam for WorkspaceSelector. The impl (in :app) holds the authoritative WORKING copy of the
 * loaded workspace entities; order/deletions/renames/settings mutations are applied onto that working
 * set and flushed together by [applyChanges]. All ids are IdType.toString(). commonMain-safe (no
 * Android/Room/JSword types cross this boundary).
 */
interface WorkspaceService {
    fun currentWorkspaceId(): String
    fun saveCurrentIntoDb()                                   // classic onCreate windowRepository.saveIntoDb()
    fun loadAll(): List<WorkspaceRowVd>                       // (re)load working entities, ordered
    fun createWorkspace(name: String): WorkspaceRowVd         // insert now (seeded from current window repo)
    fun cloneWorkspace(sourceId: String, name: String): WorkspaceRowVd
    fun applyChanges(orderedIds: List<String>, deletedIds: List<String>, renamed: Map<String, String>, changedIds: Set<String>)
    fun deleteCreated(ids: List<String>)                      // cancel path: hard-delete session-created workspaces
    fun settingTypeLabels(sourceId: String): List<String>     // TextDisplaySettings.Types titles for the source
    fun copySettings(sourceId: String, typeIndices: List<Int>, targetIds: List<String>): List<WorkspaceRowVd>  // mutates working; returns refreshed target VDs
    fun copySettingsToGlobal(sourceId: String, typeIndices: List<Int>)
    fun settingsBundleJson(id: String): String                // the detached settings-editor round-trip (spec 11.4)
    fun applyWorkspaceSettings(id: String, settingsBundleJson: String, reset: Boolean): WorkspaceRowVd  // mutates working; returns refreshed VD
}

package net.bible.android.control.backup

import net.bible.android.control.page.window.WindowRepository
import net.bible.android.database.IdType

/**
 * The narrow slice of the reading state that [reloadAfterRestore] drives. [WindowRepository] is adapted by
 * [restoreReloadTarget]; tests substitute a recording fake.
 */
interface RestoreReloadTarget {
    /** Ids of the windows of the CURRENT (pre-reload) in-memory workspace. */
    fun windowIds(): List<String>

    /** Marks every window as needing a full resync. */
    fun setResyncRequired()

    /**
     * Reloads the repository from the (restored) database, letting it pick the restored current workspace
     * (the empty workspace id). This lifts the restore-epoch save freeze.
     */
    suspend fun reloadFromDatabase()

    /** String id of the workspace the repository now holds, or empty when there is none. */
    fun loadedWorkspaceId(): String

    /** Forces every visible window to reload its content. */
    fun reloadAllWindows(force: Boolean)

    /** Re-applies the text display settings to all windows. */
    fun updateAllWindowsTextDisplaySettings()
}

/** Adapts a real [WindowRepository] (and its `windowSync`) to [RestoreReloadTarget]. */
fun restoreReloadTarget(repository: WindowRepository): RestoreReloadTarget = object : RestoreReloadTarget {
    override fun windowIds() = repository.windowList.map { it.id.toString() }
    override fun setResyncRequired() = repository.windowSync.setResyncRequired()
    override suspend fun reloadFromDatabase() = repository.loadFromDb(IdType.empty())
    override fun loadedWorkspaceId() = repository.id.toString()
    override fun reloadAllWindows(force: Boolean) = repository.windowSync.reloadAllWindows(force)
    override fun updateAllWindowsTextDisplaySettings() = repository.updateAllWindowsTextDisplaySettings()
}

/**
 * Platform-neutral twin of Android's `ReadingCommands.applyRestoredDatabase`: brings the live reading state
 * back in line with a database restore that replaced the databases underneath it.
 *
 * Order (each step after the previous one):
 *  1. capture the old window ids (the load destroys the old Window objects);
 *  2. `setResyncRequired()`;
 *  3. reload from the database with the empty workspace id (lifts the epoch save freeze);
 *  4. when a workspace is held, pass its id to [setCurrentWorkspaceId] (skipped for an empty id);
 *  5. [releaseWindow] for every OLD id, so hosts re-assemble on the new Window objects;
 *  6. `reloadAllWindows(true)`;
 *  7. update the text display settings.
 *
 * Thread contract: the caller runs this on the Main thread (the load applies its result on Main).
 */
suspend fun reloadAfterRestore(
    target: RestoreReloadTarget,
    releaseWindow: (String) -> Unit,
    setCurrentWorkspaceId: (String) -> Unit,
) {
    val oldIds = target.windowIds()
    target.setResyncRequired()
    target.reloadFromDatabase()
    val workspaceId = target.loadedWorkspaceId()
    if (workspaceId.isNotEmpty()) setCurrentWorkspaceId(workspaceId)
    oldIds.forEach(releaseWindow)
    target.reloadAllWindows(true)
    target.updateAllWindowsTextDisplaySettings()
}

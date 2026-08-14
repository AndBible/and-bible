/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */
package net.bible.android.view.activity.workspaces

import net.bible.android.control.event.ABEventBus
import net.bible.android.control.event.window.WorkspaceColorChanged
import net.bible.android.control.page.window.WindowControl
import net.bible.android.database.IdType
import net.bible.android.database.SettingsBundle
import net.bible.android.database.SettingsLevel
import net.bible.android.database.WorkspaceEntities
import net.bible.android.database.defaultWorkspaceColor
import net.bible.android.view.activity.settings.getPrefItem
import net.bible.service.common.CommonUtils
import net.bible.service.db.DatabaseContainer
import net.bible.service.llm.agent.AgentSessionManager
import net.bible.sharedcore.workspaces.WorkspaceRowVd
import net.bible.sharedcore.workspaces.WorkspaceService

/**
 * Room/JSword-backed [WorkspaceService]; holds the working entities and mirrors classic
 * [WorkspaceSelectorActivity] DB behavior exactly (see that class for the reference implementation
 * this was ported from). Registered as a Koin single (lives for the process); [loadAll] (re)seeds
 * the working set each time the WorkspaceSelector screen is opened.
 */
class WorkspaceServiceImpl(private val windowControl: WindowControl) : WorkspaceService {
    private val dao get() = DatabaseContainer.instance.workspaceDb.workspaceDao()
    private val working = mutableListOf<WorkspaceEntities.Workspace>()

    private fun WorkspaceEntities.Workspace.toVd() = WorkspaceRowVd(
        id = id.toString(),
        name = name,
        summary = contentsText,
        colorArgb = workspaceSettings?.workspaceColor ?: defaultWorkspaceColor,
        isCurrent = id == windowControl.windowRepository.id,
    )
    private fun find(id: String) = working.first { it.id.toString() == id }

    override fun currentWorkspaceId() = windowControl.windowRepository.id.toString()
    override fun saveCurrentIntoDb() { windowControl.windowRepository.saveIntoDb() }

    override fun loadAll(): List<WorkspaceRowVd> {
        working.clear(); working.addAll(dao.allWorkspaces()); return working.map { it.toVd() }
    }

    override fun createWorkspace(name: String): WorkspaceRowVd {
        val repo = windowControl.windowRepository
        val ws = WorkspaceEntities.Workspace(
            name = name, contentsText = null, orderNumber = repo.orderNumber,
            textDisplaySettings = repo.textDisplaySettings, workspaceSettings = repo.workspaceSettings,
        ).apply { dao.insertWorkspace(this) }
        working.add(ws); return ws.toVd()
    }

    override fun cloneWorkspace(sourceId: String, name: String): WorkspaceRowVd {
        val ws = dao.cloneWorkspace(IdType(sourceId), name)
        working.add(working.indexOfFirst { it.id.toString() == sourceId } + 1, ws)
        return ws.toVd()
    }

    override fun applyChanges(orderedIds: List<String>, deletedIds: List<String>, renamed: Map<String, String>, changedIds: Set<String>) {
        renamed.forEach { (id, n) -> working.firstOrNull { it.id.toString() == id }?.name = n }
        orderedIds.forEachIndexed { idx, id -> working.firstOrNull { it.id.toString() == id }?.orderNumber = idx }
        deletedIds.forEach { id ->
            val wid = IdType(id); dao.deleteWorkspace(wid); AgentSessionManager.clearSession(wid)
        }
        dao.updateWorkspaces(working.filter { it.id.toString() in changedIds && it.id.toString() !in deletedIds })
    }

    override fun deleteCreated(ids: List<String>) { ids.forEach { dao.deleteWorkspace(IdType(it)) } }

    override fun settingTypeLabels(sourceId: String): List<String> {
        val ws = find(sourceId)
        return WorkspaceEntities.TextDisplaySettings.Types.values().map {
            getPrefItem(bundleFor(ws), it).title.toString()
        }
    }

    private fun bundleFor(ws: WorkspaceEntities.Workspace) = SettingsBundle(
        level = SettingsLevel.WORKSPACE, workspaceId = ws.id, workspaceName = ws.name,
        workspaceSettings = (ws.textDisplaySettings ?: WorkspaceEntities.TextDisplaySettings()).apply {
            colors?.workspaceColor = ws.workspaceSettings?.workspaceColor
        },
        globalSettings = CommonUtils.globalTextDisplaySettings,
    )

    override fun copySettings(sourceId: String, typeIndices: List<Int>, targetIds: List<String>): List<WorkspaceRowVd> {
        val types = WorkspaceEntities.TextDisplaySettings.Types.values()
        val source = find(sourceId)
        targetIds.forEach { tid ->
            val ws = find(tid)
            val s = ws.textDisplaySettings ?: WorkspaceEntities.TextDisplaySettings()
            typeIndices.forEach { ti -> s.setValue(types[ti], source.textDisplaySettings?.getValue(types[ti])) }
            ws.textDisplaySettings = s
        }
        return targetIds.map { find(it).toVd() }
    }

    override fun copySettingsToGlobal(sourceId: String, typeIndices: List<Int>) {
        val types = WorkspaceEntities.TextDisplaySettings.Types.values()
        val dirtyTypes = typeIndices.map { types[it] }.toSet()
        val source = find(sourceId)
        val global = CommonUtils.globalTextDisplaySettings

        // Resolve through the source's own parent chain first: a workspace that INHERITS a type has
        // null there, and writing that null used to reset the global to the factory default.
        val resolved = WorkspaceEntities.TextDisplaySettings.actual(
            pageManagerSettings = null,
            workspaceSettings = source.textDisplaySettings ?: WorkspaceEntities.TextDisplaySettings(),
            globalSettings = global,
        )
        val newGlobal = WorkspaceEntities.TextDisplaySettings.globalWithCopiedValues(global, resolved, dirtyTypes)
        CommonUtils.globalTextDisplaySettings = newGlobal

        // Walk the tree, as every other writer of the global level does. Without this the new
        // default stays invisible behind every existing workspace/window override and the command
        // looks like it did nothing.
        windowControl.windowRepository.propagateGlobalTextDisplaySettingsChange(dirtyTypes, newGlobal)

        // ...and again over the STAGED entities. propagateGlobalTextDisplaySettingsChange walks the
        // database, but this screen holds its own working copies and flushes them on Save — so
        // without this, Save would write the old overrides straight back over the propagation.
        // Windows are not staged here, hence the empty window lists; the database walk above covers
        // them. A staged workspace with null textDisplaySettings holds no override to clear.
        WorkspaceEntities.TextDisplaySettings.propagateGlobalChange(
            dirtyTypes, newGlobal,
            working.mapNotNull { ws -> ws.textDisplaySettings?.let { it to emptyList<WorkspaceEntities.TextDisplaySettings>() } },
        )
    }

    override fun settingsBundleJson(id: String): String = bundleFor(find(id)).toJson()

    override fun applyWorkspaceSettings(id: String, settingsBundleJson: String, reset: Boolean): WorkspaceRowVd {
        val ws = find(id)
        val settings = SettingsBundle.fromJson(settingsBundleJson)
        ws.textDisplaySettings = if (reset) WorkspaceEntities.TextDisplaySettings() else settings.workspaceSettings
        ws.workspaceSettings?.workspaceColor =
            if (reset) defaultWorkspaceColor else settings.workspaceSettings.colors?.workspaceColor ?: defaultWorkspaceColor
        ABEventBus.post(WorkspaceColorChanged())
        return ws.toVd()
    }
}

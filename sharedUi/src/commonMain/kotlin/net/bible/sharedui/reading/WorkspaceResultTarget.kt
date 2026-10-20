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
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.reading

/**
 * Android's `ReadingCommands.applyWorkspaceChangedResult` decision for the full workspace selector's answer: the workspace
 * to load, or null when the answer names no change. A workspace other than the current one is loaded; otherwise a
 * `changed` flag (the current workspace was edited or renamed) reloads the current one.
 */
@kotlin.native.HiddenFromObjC
fun workspaceToLoad(workspaceId: String?, changed: Boolean, currentWorkspaceId: String): String? = when {
    workspaceId != null && workspaceId != currentWorkspaceId -> workspaceId
    changed -> currentWorkspaceId
    else -> null
}

/*
 * Copyright (c) 2020-2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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
package net.bible.android.control.event.window

/**
 * Posted whenever `WindowRepository.workspaceSettings.workspaceColor` (or, for a not-currently-active
 * workspace, its persisted `WorkspaceEntities.Workspace.workspaceSettings.workspaceColor`) is written.
 *
 * Exists because the colour has **twelve** live write sites across **five** files, spanning both the
 * classic and Compose settings surfaces — `MainBibleActivity` (the `COLORS_CHANGED` activity-result
 * branch and `workspaceSettingsChanged`'s `SettingsLevel.WORKSPACE` branch), `TextDisplaySettings`
 * (`commitDirtyToInMemoryState`'s workspace branch, the classic per-edit commit),
 * `TextDisplaySettingsServiceImpl` (`reset`, `applyAndPersist`, `applyColors`, `resetColors` — the
 * last two are reached live from the Compose colour picker's per-edit commit and its Reset action,
 * `ColorSettingsController.onWorkspaceColorChange`/`onReset`), `WorkspaceServiceImpl`
 * (`applyWorkspaceSettings`), and `WorkspaceSelectorActivity` (its `WORKSPACE_SETTINGS_CHANGED`
 * activity-result handler) — and only the first of them returns through an `Activity` result the
 * reading view can hook. Before this event, a colour changed anywhere else sat unused in
 * `workspaceSettings` until an unrelated passage/verse/window event happened to rebuild
 * `ToolbarStateServiceImpl`'s snapshot (A/B feedback batch 4a, F1; the missed Compose-picker sites
 * were the actual bug the maintainer hit, found in fix round 1 after the initial fix only covered
 * four of the twelve).
 *
 * Parameterless on purpose: subscribers re-read the current value from the repository rather than
 * trusting a carried one, so a writer cannot post a colour that disagrees with what is stored.
 *
 * `ToolbarStateServiceImplTest.everyWorkspaceColorWriterPostsTheEvent` is a source-level guard that
 * walks the whole of `src/main/java` and pairs every `workspaceSettings.workspaceColor =` write with
 * a nearby post — add a thirteenth writer without a post next to it and that test fails, naming the
 * offending `file:line`. It walks the tree rather than a fixed file list precisely so a writer added
 * in a *new* file cannot slip through, which a hardcoded list would have allowed.
 */
class WorkspaceColorChanged : WindowEvent

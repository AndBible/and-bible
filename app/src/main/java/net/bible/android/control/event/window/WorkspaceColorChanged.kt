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
 * Posted whenever `WindowRepository.workspaceSettings.workspaceColor` is written.
 *
 * Exists because the colour has FOUR writers — the classic `COLORS_CHANGED` activity result, the
 * Compose colour screen's per-edit commit and its reset (`TextDisplaySettingsServiceImpl`), and the
 * workspace editor (`WorkspaceServiceImpl`) — and only the first of them returns through an
 * `Activity` result the reading view can hook. Before this event, a colour changed on the Compose
 * screen sat unused in `workspaceSettings` until an unrelated passage/verse/window event happened
 * to rebuild `ToolbarStateServiceImpl`'s snapshot (A/B feedback batch 4a, F1).
 *
 * Parameterless on purpose: subscribers re-read the current value from the repository rather than
 * trusting a carried one, so a writer cannot post a colour that disagrees with what is stored.
 */
class WorkspaceColorChanged : WindowEvent

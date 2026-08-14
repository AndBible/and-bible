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
 * A different workspace's settings are now in effect: posted at the end of
 * `WindowRepository.loadFromDb`, once the window list and the workspace's settings are consistent.
 *
 * Distinct from [WorkspaceColorChanged], which means "the user edited the colours of the workspace
 * already in effect". A switch replaces every workspace-scoped setting at once, and naming that
 * event after one of them is how the next stale-state defect gets written — the colour seed being
 * exactly the one that was stale before this event existed (`AbAppTheme` refreshed only on
 * [WorkspaceColorChanged], so a switch left the UI in the previous workspace's colour).
 */
class WorkspaceChanged : WindowEvent

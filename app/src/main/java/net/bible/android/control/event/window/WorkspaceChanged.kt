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
 *
 * **Measured on-device (A/B feedback batch 5, task 5, fix round 1): a listener registered on a
 * `BibleView` will NEVER receive this event.** `MainBibleActivity.currentWorkspaceId`'s setter
 * calls `bibleViewFactory.clear()` first, which `doDestroy()`s every cached `BibleView` — setting
 * `listenEvents = false` and unregistering it from [net.bible.android.control.event.ABEventBus] —
 * *before* `loadFromDb()` runs and posts this event. `ABEventBus.post` dispatches against a
 * snapshot of registrations taken at call time, and the replacement `BibleView`s aren't
 * constructed until a later recomposition, well after `post` has already returned. `loadFromDb`'s
 * only other caller is cold-start `initialize()`, where no `BibleView` exists yet either. A
 * `BibleView`-registered handler for this event was added, measured with an on-device log (fired
 * 0 times across 10 confirmed workspace switches, versus 10/10 for the `AbAppTheme` listener
 * below), and removed as dead code the same day. The reading page re-themes anyway because a
 * freshly built `BibleView` reads the already-updated workspace state at construction time — it
 * picks the new colours up by being built late, not by being pushed to.
 */
class WorkspaceChanged : WindowEvent

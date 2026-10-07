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
package net.bible.android.control.page.window

import androidx.annotation.VisibleForTesting
import net.bible.sharedcore.event.EventSource
import net.bible.sharedcore.event.Events

/** What happened to the workspace in effect. */
sealed interface WorkspaceChange {
    /**
     * A different workspace's settings are now in effect: emitted at the end of
     * `WindowRepository.loadFromDb`, once the window list and the workspace's settings are consistent.
     *
     * Distinct from [ColorEdited], which means "the user edited the colours of the workspace
     * already in effect". A switch replaces every workspace-scoped setting at once, and naming that
     * event after one of them is how the next stale-state defect gets written — the colour seed being
     * exactly the one that was stale before this event existed (`AbAppTheme` refreshed only on
     * a colour edit, so a switch left the UI in the previous workspace's colour).
     *
     * **Measured on-device (A/B feedback batch 5, task 5, fix round 1): a listener registered on a
     * `BibleView` will NEVER receive this event.** `MainBibleActivity.currentWorkspaceId`'s setter
     * calls `bibleViewFactory.clear()` first, which `doDestroy()`s every cached `BibleView` — setting
     * `listenEvents = false` and unregistering it from [net.bible.android.control.event.ABEventBus] —
     * *before* `loadFromDb()` runs and emits this event. Emission dispatches against a
     * snapshot of subscribers taken at call time, and the replacement `BibleView`s aren't
     * constructed until a later recomposition, well after the emit has already returned. `loadFromDb`'s
     * only other caller is cold-start `initialize()`, where no `BibleView` exists yet either. A
     * `BibleView`-registered handler for this event was added, measured with an on-device log (fired
     * 0 times across 10 confirmed workspace switches, versus 10/10 for the `AbAppTheme` listener),
     * and removed as dead code the same day. The reading page re-themes anyway because a
     * freshly built `BibleView` reads the already-updated workspace state at construction time — it
     * picks the new colours up by being built late, not by being pushed to.
     */
    object Switched : WorkspaceChange

    /**
     * Emitted whenever `WindowRepository.workspaceSettings.workspaceColor` (or, for a not-currently-active
     * workspace, its persisted `WorkspaceEntities.Workspace.workspaceSettings.workspaceColor`) is written.
     * Subscribers re-read it; nothing is carried.
     *
     * Exists because the colour has several live write sites. At the time this event was introduced
     * they spanned both the classic and Compose settings surfaces; the classic ones are gone now
     * (Z-late slice S12): `MainBibleActivity`'s `COLORS_CHANGED` activity-result branch (deleted along
     * with the TEXT_DISPLAY_SETTINGS_CHANGED round-trip that was its only caller), its
     * `workspaceSettingsChanged`'s `SettingsLevel.WORKSPACE` branch, and classic `TextDisplaySettings`'s
     * `commitDirtyToInMemoryState` workspace branch all used to write here too, and are all deleted.
     * The sites that remain are `TextDisplaySettingsServiceImpl` (`reset`, `applyAndPersist`, `applyColors`, `resetColors` —
     * the last two are reached live from the Compose colour picker's per-edit commit and its Reset
     * action, `ColorSettingsController.onWorkspaceColorChange`/`onReset`) and `WorkspaceServiceImpl`
     * (`applyWorkspaceSettings`). Before this event, a colour changed anywhere else sat
     * unused in `workspaceSettings` until an unrelated passage/verse/window event happened to rebuild
     * `ToolbarStateServiceImpl`'s snapshot (A/B feedback batch 4a, F1; the missed Compose-picker sites
     * were the actual bug the maintainer hit, found in fix round 1 after the initial fix only covered
     * four of the twelve write sites live at that time).
     *
     * Parameterless on purpose: subscribers re-read the current value from the repository rather than
     * trusting a carried one, so a writer cannot emit a colour that disagrees with what is stored.
     *
     * `ToolbarStateServiceImplTest.everyWorkspaceColorWriterNotifies` is a source-level guard that
     * walks the whole of `src/main/java` and pairs every `workspaceSettings.workspaceColor =` write with
     * a nearby [WorkspaceChanges.notifyColorEdited] — add a new writer without one next to it and that test fails, naming the
     * offending `file:line`. It walks the tree rather than a fixed file list precisely so a writer added
     * in a *new* file cannot slip through, which a hardcoded list would have allowed.
     */
    object ColorEdited : WorkspaceChange
}

object WorkspaceChanges {
    private var source = EventSource<WorkspaceChange>()

    /** Workspace switches and colour edits, synchronously on the caller's thread (replaces two bus events). */
    val changes: Events<WorkspaceChange> get() = source

    fun notifySwitched() = source.emit(WorkspaceChange.Switched)
    fun notifyColorEdited() = source.emit(WorkspaceChange.ColorEdited)

    /** Test teardown: process-global, so leaked subscribers would outlive their test. */
    @VisibleForTesting
    fun resetSubscribersForTest() { source = EventSource() }
}

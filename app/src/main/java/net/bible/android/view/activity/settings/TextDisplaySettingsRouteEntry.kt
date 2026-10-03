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
package net.bible.android.view.activity.settings

import net.bible.android.database.SettingsBundle
import net.bible.sharedcore.nav.TextDisplaySettingsArgs
import net.bible.sharedcore.nav.TextSettingsResult
import net.bible.sharedcore.settings.SettingsScope

/**
 * What ONE entry into the `settings/textDisplay` destination resolves to, host-side: classic
 * `TextDisplaySettingsComposeActivity`'s three coupled `by lazy` fields ([detachedEdit], [service])
 * and the `finish()` override that reads them ([resultOnLeave]), plus the [scope] `scopeFromRoute`
 * already answered.
 *
 * They are one object rather than three because the first decides the other two: whether this entry
 * is a DETACHED (selector-originated) edit determines which service every controller of this entry
 * is built against AND whether leaving publishes anything at all. Classic held them as three `by
 * lazy`s on the Activity; the nav-graph host has no per-entry object to hang them on, so this is it.
 *
 * **Extracted from `NavHostComposeActivity` in fix round 1 so it can be TESTED.** Two classic tests
 * that nav-graph slice 7 Task 13 deletes along with the Activity cover exactly this logic --
 * `TextDisplaySettingsComposeActivityDetachedTest`'s `aDetachedLaunchDoesNotUseTheSharedService` and
 * its unedited-returns-nothing / edited-returns-the-bundle pair -- and while the same code sat in a
 * private method of the host Activity, no successor could exist: reaching it meant launching a host
 * that wants Koin, JSword and a window. As a top-level class it is a plain constructor call, and
 * `TextDisplaySettingsRouteEntryTest` is the successor. Same motive, and the same file-of-its-own
 * shape, as `scopeFromRoute` next door: Task 13's deletion takes the classic copy and leaves this.
 *
 * Lives in `:app` for `scopeFromRoute`'s reason -- [SettingsBundle] embeds Room-backed
 * `WorkspaceEntities` types that cannot cross into `commonMain`.
 */
class TextDisplaySettingsRouteEntry(
    args: TextDisplaySettingsArgs,
    sharedService: TextDisplaySettingsServiceImpl,
) {
    /** Non-null only for a selector-originated entry; classic's `detachedEdit`. */
    val detachedEdit: DetachedWorkspaceEdit? =
        args.settingsBundleJson?.let { DetachedWorkspaceEdit(SettingsBundle.fromJson(it)) }

    /**
     * Classic's `service`: the detached entry builds its OWN instance rather than reusing the Koin
     * singleton, for [DetachedWorkspaceEdit]'s stated reason -- a selector-originated edit must touch
     * neither the active workspace nor the shared service (which the reading view's in-place settings
     * editor also holds).
     */
    val service: TextDisplaySettingsServiceImpl =
        detachedEdit?.let { TextDisplaySettingsServiceImpl(it) } ?: sharedService

    /** Which scope the destination opens at; see [scopeFromRoute]. */
    val scope: SettingsScope = scopeFromRoute(args)

    /**
     * Classic's whole `finish()` override, condition included: a result ONLY when the detached edit
     * actually CHANGED (`DetachedWorkspaceEdit.changed`, i.e. dirty or reset -- plan D3), and `null`
     * on every other exit, including every non-detached one. `null` means "pop with nothing
     * published", which is what classic did by simply not calling `setResult`.
     */
    fun resultOnLeave(): TextSettingsResult? = detachedEdit
        ?.takeIf { it.changed }
        ?.let { TextSettingsResult(settingsBundleJson = it.bundle.toJson(), reset = it.reset) }
}

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
import net.bible.sharedcore.settings.SettingsScope

/**
 * Which [SettingsScope] the `settings/textDisplay` destination opens at, from the route's own
 * arguments — the route-argument port of classic `scopeFromIntent`
 * (`TextDisplaySettingsComposeActivity.kt`, still in the tree until nav-graph slice 7 Task 13
 * deletes it, so the two exist side by side for now, exactly as the five chooser seams Task 4
 * ported do).
 *
 * **A detached bundle still wins over an explicit scope level**, and that is the classic behaviour
 * preserved rather than a bug carried over: a selector-originated edit scopes to the workspace the
 * SELECTOR named, never to whatever is active, and there is no coherent reading in which a
 * scope-level argument should override the bundle whose contents are about to be edited. What made
 * that precedence dangerous under Intents was that the "detached bundle" extra key and the plain
 * "global settings" launch were told apart by nothing but which extra happened to be set, and the
 * host's global-settings row set both meanings at once (design §3.2 item 2). Route arguments are
 * named and the builder is typed, so the global row now asks for `scopeLevel = "global"` and carries
 * no bundle at all; see `TextDisplaySettingsScopeTest`, which pins both halves.
 *
 * Lives in `:app` rather than in the graph because resolving the detached branch means parsing
 * [SettingsBundle] JSON, and [SettingsBundle] embeds Room-backed `WorkspaceEntities` types that
 * cannot cross into `commonMain`. Lives in a file of its own rather than on the Activity so that
 * Task 13's deletion takes the classic copy and leaves this one.
 *
 * The `IdType` behind a bundle's `workspaceId` stringifies to `""` when empty
 * (`IdType.toString()`), so a GLOBAL-level bundle resolves to `SettingsScope.Workspace("")` — the
 * empty workspace, which is precisely what the defect produced for a row that meant GLOBAL. It is
 * left as-is because nothing builds such a route any more, and inventing a fallback here would hide
 * a caller that had got its arguments wrong.
 */
fun scopeFromRoute(args: TextDisplaySettingsArgs): SettingsScope {
    args.settingsBundleJson?.let {
        return SettingsScope.Workspace(SettingsBundle.fromJson(it).workspaceId.toString())
    }
    return when (args.scopeLevel) {
        "window" -> SettingsScope.Window(
            windowId = args.windowId!!,
            workspaceId = args.workspaceId!!,
        )
        "workspace" -> SettingsScope.Workspace(workspaceId = args.workspaceId!!)
        else -> SettingsScope.Global
    }
}

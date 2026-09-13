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
 * **There is no precedence any more: saying both is an ERROR** (fix round 1). What stood here was
 * classic's rule -- a detached bundle wins over an explicit scope level -- defended as "safe now
 * because the global row no longer sets a bundle". That defence is about one CALLER, and the defect
 * design §3.2 item 2 records is about what happens when some caller sets both by accident: under the
 * old rule such a caller still landed, silently, in a detached edit of whatever workspace its bundle
 * named. The commit message claimed "the precedence cannot be ambiguous again" on the strength of
 * the arguments being NAMED, but naming only makes the mistake visible in the source; it does not
 * make it fail.
 *
 * The [require] below does. A route that says both is rejected at the point it is read, with a
 * message naming both arguments, instead of resolving to one of them. `scopeLevel` then means what
 * it says at every call site, and a detached bundle is the ONLY thing a detached route carries --
 * which is what every caller in the tree does today: the selector arm passes `settingsBundle` alone
 * (`WorkspaceNavGraph.kt`), and the host's global-settings row passes `scopeLevel = "global"` alone.
 *
 * The check deliberately lives HERE, on the reading side, and not on `NavRoutes.textDisplaySettings`:
 * it then covers every route this function is ever handed, including one assembled by hand or
 * restored from a saved state, not only the ones that went through the builder.
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
    require(args.settingsBundleJson == null || args.scopeLevel == null) {
        "a text-display-settings route may name a scopeLevel or carry a detached settingsBundle, " +
            "never both: scopeLevel=${args.scopeLevel} with a bundle present is the ambiguity " +
            "design §3.2 item 2 records"
    }
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

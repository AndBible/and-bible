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

package net.bible.sharedui.ai.nav

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedcore.ai.ToolVd
import net.bible.sharedui.ai.ToolInfoScreen

/**
 * [ToolInfoScreen]'s platform-supplied slots. One such holder per destination, nested under
 * [AiNavDeps] — kept small and grouped rather than flattened, because at nine destinations a flat
 * [AiNavDeps] would mix ~40 fields (plain data, per-item lambdas, Task 9's suspend lambdas) in one
 * namespace with nothing but a naming convention telling them apart.
 */
class ToolInfoDeps(
    val readTools: List<ToolVd>,
    val writeTools: List<ToolVd>,
    val helpBody: String,
    val helpReadMoreUrl: String,
)

/**
 * Platform-supplied slots the AI destinations need but `commonMain` cannot provide: help text
 * (Android string resources today), the data each screen renders, and — via [exitHost] — the way
 * to leave the graph entirely. [exitHost] sits at the top level rather than in a per-destination
 * holder because it is graph-wide, not destination-specific: see [popOrExit]. Grows one nested
 * holder per destination as the cluster migrates.
 */
class AiNavDeps(
    val exitHost: () -> Unit,
    // — TOOL INFO —
    val toolInfo: ToolInfoDeps,
)

/**
 * Whether an up-navigation attempt that just tried to pop the back stack should fall through to
 * exiting the host outright, given [popped] (`navController.popBackStack()`'s result). Split out
 * from [popOrExit] as a plain boolean-in function — rather than folded into it — so this branch is
 * unit-testable without a real `NavHostController`: that class requires an Android `Context` to
 * construct and has no lightweight fake, while `:sharedUi` (as of this file) has no Robolectric-
 * style test runner, only plain JUnit via `kotlin("test")`. `internal` rather than `private` for
 * exactly that reason — a visible seam that is tested beats a private one that is not.
 */
internal fun popOrExitOnFailedPop(popped: Boolean, exitHost: () -> Unit) {
    if (!popped) exitHost()
}

/**
 * Up-navigation for a destination that may be the graph's START destination. `popBackStack()`
 * returns false and does nothing on a single-entry back stack, so a bare `popBackStack()` binding
 * makes the up-arrow a dead button whenever the destination was entered directly — which is the
 * normal case while `ScreenLauncher` launches each migrated screen straight into the host (today,
 * `ToolInfo` is always the graph's only entry, since nothing else is migrated yet).
 */
private fun NavHostController.popOrExit(exitHost: () -> Unit) {
    popOrExitOnFailedPop(popBackStack(), exitHost)
}

/**
 * The AI cluster's destinations. Registered into the app's single `NavHost` by the host Activity.
 *
 * All inter-screen navigation lives HERE, not in the host: the host owns platform plumbing only.
 * Up-navigation binds through [popOrExit] — note the screens spell that lambda three different
 * ways (`onUp`, `onBack`, `onNavigateUp`), which is pre-existing and not normalised here because
 * the screen signatures are frozen for this migration.
 */
fun NavGraphBuilder.aiNavGraph(navController: NavHostController, deps: AiNavDeps) {
    composable(NavRoutes.AI_TOOL_INFO) {
        ToolInfoScreen(
            readTools = deps.toolInfo.readTools,
            writeTools = deps.toolInfo.writeTools,
            onUp = { navController.popOrExit(deps.exitHost) },
            helpBody = deps.toolInfo.helpBody,
            helpReadMoreUrl = deps.toolInfo.helpReadMoreUrl,
        )
    }
}

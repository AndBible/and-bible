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
 * Platform-supplied slots the AI destinations need but `commonMain` cannot provide: help text
 * (Android string resources today), and the data each screen renders. Grows one field per
 * destination as the cluster migrates; keep the fields grouped and commented by destination.
 */
class AiNavDeps(
    // — TOOL INFO —
    val toolInfoReadTools: List<ToolVd>,
    val toolInfoWriteTools: List<ToolVd>,
    val toolInfoHelpBody: String,
    val toolInfoHelpReadMoreUrl: String,
)

/**
 * The AI cluster's destinations. Registered into the app's single `NavHost` by the host Activity.
 *
 * All inter-screen navigation lives HERE, not in the host: the host owns platform plumbing only.
 * Up-navigation binds to `navController.popBackStack()` — note the screens spell that lambda three
 * different ways (`onUp`, `onBack`, `onNavigateUp`), which is pre-existing and not normalised here
 * because the screen signatures are frozen for this migration.
 */
fun NavGraphBuilder.aiNavGraph(navController: NavHostController, deps: AiNavDeps) {
    composable(NavRoutes.AI_TOOL_INFO) {
        ToolInfoScreen(
            readTools = deps.toolInfoReadTools,
            writeTools = deps.toolInfoWriteTools,
            onUp = { navController.popBackStack() },
            helpBody = deps.toolInfoHelpBody,
            helpReadMoreUrl = deps.toolInfoHelpReadMoreUrl,
        )
    }
}

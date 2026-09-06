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
package net.bible.android.view.activity.nav

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import net.bible.android.activity.R
import net.bible.android.view.activity.base.ActivityBase
import net.bible.service.llm.tools.Tool
import net.bible.service.llm.tools.ToolRegistry
import net.bible.sharedcore.ai.ToolVd
import net.bible.sharedui.AbAppTheme
import net.bible.sharedui.ai.nav.AiNavDeps
import net.bible.sharedui.ai.nav.aiNavGraph

/**
 * The single Android host for the Compose navigation graph. Screens migrated off their own
 * Activities become destinations inside it; `ScreenLauncher` routes to it by [EXTRA_ROUTE].
 *
 * It still extends [ActivityBase] on purpose: every migrated destination inherits the base's
 * theming, locale attachment, edge-to-edge setup, `CurrentActivityHolder` registration and — the
 * one that matters for this cluster — `awaitIntent`, which the SAF flows need.
 */
class NavHostComposeActivity : ActivityBase() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val startRoute = requireNotNull(intent.getStringExtra(EXTRA_ROUTE)) {
            "NavHostComposeActivity requires EXTRA_ROUTE — launch it via NavHostComposeActivity.intentFor()"
        }
        setContent {
            AbAppTheme {
                val navController = rememberNavController()
                val allTools = remember { ToolRegistry.getAllTools() }
                val deps = remember(allTools) {
                    AiNavDeps(
                        toolInfoReadTools = allTools.filter { !it.requiresPermission }.map { it.toToolVd() },
                        toolInfoWriteTools = allTools.filter { it.requiresPermission }.map { it.toToolVd() },
                        toolInfoHelpBody = getString(R.string.help_tool_info_text),
                        toolInfoHelpReadMoreUrl =
                            "https://docs.andbible.org/en/latest/ai.html#ai-tools",
                    )
                }
                NavHost(
                    navController = navController,
                    startDestination = startRoute,
                    // Paint an opaque themed ground BEHIND the graph. navigation-compose's default
                    // transition is a crossfade, and while both screens are semi-transparent the
                    // window background shows through — which on a dark or e-ink theme reads as a
                    // flash of the wrong colour on every navigation. AMR hit exactly this.
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                ) {
                    aiNavGraph(navController, deps)
                }
            }
        }
    }

    companion object {
        const val EXTRA_ROUTE: String = "nav_route"

        fun intentFor(context: Context, route: String): Intent =
            Intent(context, NavHostComposeActivity::class.java).putExtra(EXTRA_ROUTE, route)
    }
}

private fun Tool.toToolVd() = ToolVd(
    id = agentTool.name,
    displayName = ToolRegistry.getDisplayName(this),
    description = description,
    requiresPermission = requiresPermission,
    categoryId = category.name,
)

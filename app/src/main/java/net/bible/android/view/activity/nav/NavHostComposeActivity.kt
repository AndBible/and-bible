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
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import net.bible.android.activity.R
import net.bible.android.view.activity.base.ActivityBase
import net.bible.service.llm.tools.Tool
import net.bible.service.llm.tools.ToolRegistry
import net.bible.sharedcore.ai.AiDocumentFilterController
import net.bible.sharedcore.ai.AiModelsController
import net.bible.sharedcore.ai.DocumentFilterService
import net.bible.sharedcore.ai.GlobalToolPermissionsController
import net.bible.sharedcore.ai.LlmModelService
import net.bible.sharedcore.ai.ToolPermissionService
import net.bible.sharedcore.ai.ToolVd
import net.bible.sharedui.AbAppTheme
import net.bible.sharedui.ai.nav.AiDocumentFilterDeps
import net.bible.sharedui.ai.nav.AiModelsDeps
import net.bible.sharedui.ai.nav.AiNavDeps
import net.bible.sharedui.ai.nav.GlobalToolPermissionsDeps
import net.bible.sharedui.ai.nav.ToolInfoDeps
import net.bible.sharedui.ai.nav.aiNavGraph
import org.koin.android.ext.android.inject

/**
 * The single Android host for the Compose navigation graph. Screens migrated off their own
 * Activities become destinations inside it; `ScreenLauncher` routes to it by [EXTRA_ROUTE].
 *
 * It still extends [ActivityBase] on purpose: every migrated destination inherits the base's
 * theming, locale attachment, edge-to-edge setup, `CurrentActivityHolder` registration and — the
 * one that matters for this cluster — `awaitIntent`, which the SAF flows need.
 */
class NavHostComposeActivity : ActivityBase() {
    private val documentFilterService: DocumentFilterService by inject()
    private val toolPermissionService: ToolPermissionService by inject()
    private val llmModelService: LlmModelService by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val startRoute = requireNotNull(intent.getStringExtra(EXTRA_ROUTE)) {
            "NavHostComposeActivity requires EXTRA_ROUTE — launch it via NavHostComposeActivity.intentFor()"
        }
        setContent {
            AbAppTheme {
                val navController = rememberNavController()
                val allTools = remember { ToolRegistry.getAllTools() }
                val aiDocumentFilterController = remember {
                    AiDocumentFilterController(service = documentFilterService, scope = lifecycleScope)
                }
                val globalToolPermissionsController = remember {
                    GlobalToolPermissionsController(service = toolPermissionService, scope = lifecycleScope)
                }
                val aiModelsController = remember {
                    AiModelsController(service = llmModelService, scope = lifecycleScope)
                }
                val deps = remember(allTools) {
                    AiNavDeps(
                        exitHost = { finish() },
                        toolInfo = ToolInfoDeps(
                            readTools = allTools.filter { !it.requiresPermission }.map { it.toToolVd() },
                            writeTools = allTools.filter { it.requiresPermission }.map { it.toToolVd() },
                            helpBody = getString(R.string.help_tool_info_text),
                            helpReadMoreUrl = "https://docs.andbible.org/en/latest/ai.html#ai-tools",
                        ),
                        aiDocumentFilter = AiDocumentFilterDeps(
                            controller = aiDocumentFilterController,
                            helpBody = getString(R.string.help_ai_document_filter_text),
                            helpReadMoreUrl = "https://docs.andbible.org/en/latest/ai.html#available-data-and-documents",
                        ),
                        globalToolPermissions = GlobalToolPermissionsDeps(
                            controller = globalToolPermissionsController,
                            helpBody = getString(R.string.help_global_tool_permissions_text),
                            helpReadMoreUrl = "https://docs.andbible.org/en/latest/ai.html#setting-permissions",
                        ),
                        aiModels = AiModelsDeps(
                            controller = aiModelsController,
                            providersForPicker = { llmModelService.providersForPicker() },
                            helpBody = getString(R.string.help_ai_models_text),
                            helpReadMoreUrl = "https://docs.andbible.org/en/latest/ai.html#available-models",
                            onResume = { llmModelService.refresh() },
                        ),
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

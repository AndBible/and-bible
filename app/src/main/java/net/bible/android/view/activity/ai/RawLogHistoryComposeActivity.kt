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
package net.bible.android.view.activity.ai

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import net.bible.android.activity.R
import net.bible.android.view.Screen
import net.bible.android.view.ScreenLauncher
import net.bible.android.view.activity.base.ActivityBase
import net.bible.service.common.CommonUtils
import net.bible.service.device.ScreenSettings
import net.bible.sharedcore.ai.RawLogHistoryController
import net.bible.sharedcore.ai.RawLogService
import net.bible.sharedui.ProvideAppLocals
import net.bible.sharedui.ai.RawLogHistoryScreen
import net.bible.sharedui.theme.AbTheme
import org.koin.android.ext.android.inject

/**
 * Compose host for the persisted raw-LLM-log list — the new-path twin of classic
 * [RawLogHistoryActivity]. Wires the shared [RawLogHistoryController] over [RawLogService] and renders
 * [RawLogHistoryScreen].
 *
 * **Refresh on resume.** Like classic [RawLogHistoryActivity.onResume]→`refreshList()`, this host calls
 * [RawLogService.refresh] in [onResume] so the list reflects records deleted from the detail screen /
 * added since the activity was created.
 *
 * **Opening a log.** [RawLogHistoryController.onOpenLog] routes through [ScreenLauncher]
 * ([Screen.RawLlmLog], old/new per `use_compose_ui` — Batch 9d task 11), passing the classic
 * [RawLlmLogActivity.EXTRA_LOG_RECORD_ID] extra name (reused verbatim so the extra key matches
 * classic) regardless of which host it resolves to.
 */
class RawLogHistoryComposeActivity : ActivityBase() {
    private val service: RawLogService by inject()

    private val controller by lazy {
        RawLogHistoryController(
            service = service,
            scope = lifecycleScope,
            onOpenLog = { id ->
                startActivity(
                    ScreenLauncher.intentFor(this, Screen.RawLlmLog)
                        .putExtra(RawLlmLogActivity.EXTRA_LOG_RECORD_ID, id),
                )
            },
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ProvideAppLocals {
                AbTheme(
                    darkTheme = ScreenSettings.nightMode,
                    colorMode = CommonUtils.settings.displayColorMode,
                    disableAnimations = CommonUtils.settings.disableAnimations,
                ) {
                    val summaries by controller.summaries.collectAsState()
                    val selection by controller.selection.collectAsState()
                    val selectionMode by controller.selectionMode.collectAsState()

                    RawLogHistoryScreen(
                        summaries = summaries,
                        selection = selection,
                        selectionMode = selectionMode,
                        onOpenLog = controller::openLog,
                        onToggleSelect = controller::toggleSelect,
                        onClearSelection = controller::clearSelection,
                        onDeleteSelected = controller::deleteSelected,
                        onDeleteOlderThan = controller::deleteOlderThan,
                        onDeleteAll = controller::deleteAll,
                        onHelp = { showHelp() },
                        onNavigateUp = { finish() },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        service.refresh()
    }

    private fun showHelp() {
        CommonUtils.showHelpDialog(
            activity = this,
            titleResId = R.string.help,
            messageResId = R.string.help_ai_connection_text,
            helpPath = "ai.html",
        )
    }
}

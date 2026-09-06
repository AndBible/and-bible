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
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.sharedcore.ai.RawLogHistoryController
import net.bible.sharedcore.ai.RawLogService
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedui.AbAppTheme
import net.bible.sharedui.ai.RawLogHistoryScreen
import org.koin.android.ext.android.inject

/**
 * Compose host for the persisted raw-LLM-log list — the new-path twin of classic
 * [RawLogHistoryActivity]. Wires the shared [RawLogHistoryController] over [RawLogService] and renders
 * [RawLogHistoryScreen].
 *
 * **Dead code as of Task 8 (kept for Task 10 to delete, not deleted here).** The `RawLogHistory`
 * screen is now in `ScreenLauncher.MIGRATED`, so routing to it always resolves to
 * `NavHostComposeActivity` — this class is never launched by anything any more (verified by grep:
 * no other caller names it). [onOpenLog] below was neutralized to the equivalent
 * `NavHostComposeActivity`/`NavRoutes` call rather than left in its old `ScreenLauncher`-plus-a-
 * chained-extra shape (which would otherwise trip
 * `NavHostRoutingGuardTest.migratedScreenArgumentIsNeverDroppedByAPutExtra` now that the `RawLlmLog`
 * screen is migrated too) — same call, cheaper than carving out a guard-test exclusion for dead
 * code, per Task 7's precedent (`AiConnectionSettingsComposeActivity.launchEasySetup`).
 *
 * **Refresh on resume.** Like classic [RawLogHistoryActivity.onResume]→`refreshList()`, this host calls
 * [RawLogService.refresh] in [onResume] so the list reflects records deleted from the detail screen /
 * added since the activity was created.
 */
class RawLogHistoryComposeActivity : ActivityBase() {
    private val service: RawLogService by inject()

    private val controller by lazy {
        RawLogHistoryController(
            service = service,
            scope = lifecycleScope,
            onOpenLog = { id ->
                startActivity(NavHostComposeActivity.intentFor(this, NavRoutes.rawLlmLog(logRecordId = id)))
            },
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AbAppTheme {
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
                        onNavigateUp = { finish() },
                        helpBody = getString(R.string.help_ai_connection_text),
                        helpReadMoreUrl = "https://docs.andbible.org/en/latest/ai.html",
                    )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        service.refresh()
    }
}

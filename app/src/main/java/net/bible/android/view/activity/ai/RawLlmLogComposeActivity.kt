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

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.bible.android.activity.R
import net.bible.android.control.report.AiBugReport
import net.bible.android.database.IdType
import net.bible.android.view.activity.base.ActivityBase
import net.bible.service.db.DatabaseContainer
import net.bible.service.llm.agent.AgentSessionManager
import net.bible.sharedcore.ai.RawLlmLogController
import net.bible.sharedcore.ai.RawLogService
import net.bible.sharedui.AbAppTheme
import net.bible.sharedui.ai.RawLlmLogScreen
import org.koin.android.ext.android.inject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Compose host for the raw LLM conversation log detail screen — the new-path twin of classic
 * [RawLlmLogActivity]. Wires the shared [RawLlmLogController] over [RawLogService] and renders
 * [RawLlmLogScreen].
 *
 * **Two modes, same extras as classic.** Reads [RawLlmLogActivity.EXTRA_LOG_RECORD_ID] (DB mode) or
 * [RawLlmLogActivity.EXTRA_WORKSPACE_ID] (in-memory mode) — the classic constants are reused verbatim
 * so the extra keys match. DB mode calls [RawLlmLogController.loadRecord]; in-memory mode calls
 * [RawLlmLogController.loadSession].
 *
 * **Host-derived `loading` + DB title.** The controller exposes neither an `isLoading` nor an
 * `isDbMode` flag (the screen derives DB-mode from `recordText != null`), so this host computes both:
 * - `loading` starts true. DB mode awaits the controller's `recordText` becoming non-null (the
 *   gzip-decompress) before clearing it; in-memory mode clears it immediately after kicking off the
 *   session load (classic set up the in-memory adapter synchronously, with no spinner).
 * - The DB-mode title `"<modelName> — <yyyy-MM-dd HH:mm>"` (classic) is derived from a lightweight
 *   metadata fetch of the record; in-memory mode uses the static [R.string.raw_llm_log_title].
 *
 * **Platform actions are host-side** (kept out of commonMain per the screen's kdoc): copy → clipboard
 * + toast; share → `ACTION_SEND` chooser; delete (DB only) → [RawLogService.deleteByIds] + `finish()`;
 * report-bug → [AiBugReport] (DB record id, or the in-memory session's [net.bible.service.llm.agent.RawLlmLog]).
 * The copy/share text mirrors classic `getLogText()`: the displayed DB text (with its header) or the
 * in-memory session's `format()`.
 */
class RawLlmLogComposeActivity : ActivityBase() {
    private val service: RawLogService by inject()

    private val recordId: String? by lazy { intent.getStringExtra(RawLlmLogActivity.EXTRA_LOG_RECORD_ID) }
    private val workspaceId: String? by lazy { intent.getStringExtra(RawLlmLogActivity.EXTRA_WORKSPACE_ID) }

    private val controller by lazy {
        RawLlmLogController(service = service, scope = lifecycleScope)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AbAppTheme {
                    val recordText by controller.recordText.collectAsState()
                    val entries by controller.entries.collectAsState()
                    val expandedIndices by controller.expandedIndices.collectAsState()
                    val canReportBug by controller.canReportBug.collectAsState()

                    var loading by remember { mutableStateOf(true) }
                    var title by remember { mutableStateOf(getString(R.string.raw_llm_log_title)) }

                    LaunchedEffect(Unit) {
                        val rid = recordId
                        val wid = workspaceId
                        when {
                            rid != null -> {
                                val record = withContext(Dispatchers.IO) {
                                    DatabaseContainer.instance.aiSettingsDb.llmRawLogRecordDao().getById(IdType(rid))
                                }
                                controller.loadRecord(rid)
                                if (record != null) {
                                    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                                    title = "${record.modelName} — ${dateFormat.format(Date(record.timestamp))}"
                                    // Await the decompress so we don't flash the empty state before the text arrives.
                                    controller.recordText.filterNotNull().first()
                                }
                                loading = false
                            }
                            wid != null -> {
                                controller.loadSession(wid)
                                loading = false
                            }
                            else -> loading = false
                        }
                    }

                    RawLlmLogScreen(
                        title = title,
                        loading = loading,
                        recordText = recordText,
                        entries = entries,
                        expandedIndices = expandedIndices,
                        canReportBug = canReportBug,
                        onToggleExpanded = controller::toggleExpanded,
                        onCopy = { copyLog() },
                        onShare = { shareLog() },
                        onDelete = { deleteRecord() },
                        onReportBug = { reportBug() },
                        onNavigateUp = { finish() },
                    )
            }
        }
    }

    /** Mirrors classic `getLogText()`: DB text (as displayed, header included) or the session `format()`. */
    private fun logText(): String {
        recordId?.let { return controller.recordText.value ?: "" }
        val wid = workspaceId ?: return ""
        return AgentSessionManager.getSession(IdType(wid))?.rawLlmLog?.format() ?: ""
    }

    private fun copyLog() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Raw LLM Log", logText()))
        Toast.makeText(this, R.string.raw_llm_log_copied, Toast.LENGTH_SHORT).show()
    }

    private fun shareLog() {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_TEXT, logText())
            type = "text/plain"
        }
        startActivity(Intent.createChooser(sendIntent, getString(R.string.share)))
    }

    /** DB mode only (the screen only surfaces delete when `recordText != null`). */
    private fun deleteRecord() {
        val rid = recordId ?: return
        service.deleteByIds(setOf(rid))
        finish()
    }

    private fun reportBug() {
        lifecycleScope.launch {
            val rid = recordId
            if (rid != null) {
                AiBugReport.reportAiBug(this@RawLlmLogComposeActivity, IdType(rid))
            } else {
                val wid = workspaceId ?: return@launch
                val log = AgentSessionManager.getSession(IdType(wid))?.rawLlmLog ?: return@launch
                AiBugReport.reportAiBugFromRawLog(this@RawLlmLogComposeActivity, log)
            }
        }
    }
}

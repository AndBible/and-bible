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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.bible.android.BibleApplication.Companion.application
import net.bible.android.activity.R
import net.bible.android.control.report.AiBugReport
import net.bible.android.database.IdType
import net.bible.service.db.DatabaseContainer
import net.bible.service.llm.LlmCostTracker
import net.bible.service.llm.LlmPricing
import net.bible.service.llm.LlmProvider
import net.bible.service.llm.LlmRawLogSummary
import net.bible.service.llm.agent.AgentSessionManager
import net.bible.service.llm.agent.RawLlmLog
import net.bible.service.llm.agent.RawLogEntry
import net.bible.sharedcore.ai.RawLogEntryVd
import net.bible.sharedcore.ai.RawLogService
import net.bible.sharedcore.ai.RawLogSummaryVd
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Android impl of [RawLogService] backing the raw-LLM-log screens
 * ([net.bible.sharedcore.ai.RawLogHistoryController] / [net.bible.sharedcore.ai.RawLlmLogController]),
 * the new-path twin of classic [RawLogHistoryActivity] / [RawLlmLogActivity] / [RawLlmLogAdapter].
 *
 * Registered as a Koin single. It holds two pieces of state: the [summaries] `StateFlow` (re-read
 * from [net.bible.service.llm.LlmRawLogRecordDao.allSummaries] on each [refresh], `allowMainThreadQueries`
 * as elsewhere in this layer) and [lastSessionLog] — the last in-memory [RawLlmLog] resolved by
 * [sessionEntries]. The latter exists because the [RawLogService] seam's [canReportBug] takes only a
 * nullable `recordId`, so the in-memory case (`recordId == null`) has no `workspaceId` to work from;
 * the controller always calls [sessionEntries] (which stashes the log) immediately before
 * `canReportBug(null)`, so the stash is the model source for that path — mirroring classic
 * [RawLlmLogActivity] which held the same `rawLog` field across both the render and the menu build.
 *
 * **"—" fallback ownership:** [RawLogSummaryVd.promptName] is passed through RAW (never blanked to
 * "—") — the display fallback lives solely in `RawLogHistoryScreen` (`promptName.ifBlank { "—" }`,
 * mirroring classic `RawLogHistoryAdapter.bind`, which is the ADAPTER/view layer), so it is
 * single-owned there and not duplicated here.
 */
class RawLogServiceImpl : RawLogService {
    private val dao get() = DatabaseContainer.instance.aiSettingsDb.llmRawLogRecordDao()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    /** Stashed by [sessionEntries] so `canReportBug(null)` can resolve the in-memory session model. */
    @Volatile private var lastSessionLog: RawLlmLog? = null

    private val _summaries = MutableStateFlow<List<RawLogSummaryVd>>(emptyList())
    override val summaries: StateFlow<List<RawLogSummaryVd>> = _summaries.asStateFlow()

    override fun refresh() {
        _summaries.value = dao.allSummaries().map { it.toVd() }
    }

    override fun deleteByIds(ids: Set<String>) {
        dao.deleteByIds(ids.map { IdType(it) })
        refresh()
    }

    override fun deleteOlderThan(days: Int) {
        if (days <= 0) {
            deleteAll()
            return
        }
        val cutoff = System.currentTimeMillis() - days.toLong() * 24 * 60 * 60 * 1000
        dao.deleteOlderThan(cutoff)
        refresh()
    }

    override fun deleteAll() {
        dao.deleteAll()
        refresh()
    }

    override suspend fun recordText(recordId: String): String? = withContext(Dispatchers.IO) {
        val record = dao.getById(IdType(recordId)) ?: return@withContext null
        val body = RawLlmLog.gzipDecompress(record.logData)
        // Prepend the total token/cost header line (classic rendered this in a separate TextView above
        // the log text — binding.totalCostHeader — only when there was usage to show).
        if (record.estimatedCostUsd > 0 || record.totalInputTokens > 0) {
            val costStr = if (record.estimatedCostUsd > 0) " · ${LlmCostTracker.formatCost(record.estimatedCostUsd)}" else ""
            val header = application.getString(
                R.string.raw_llm_log_total,
                LlmCostTracker.formatTokenCount(record.totalInputTokens),
                LlmCostTracker.formatTokenCount(record.totalOutputTokens),
                costStr,
            )
            "$header\n\n$body"
        } else {
            body
        }
    }

    override suspend fun sessionEntries(workspaceId: String): List<RawLogEntryVd> = withContext(Dispatchers.Default) {
        val log = AgentSessionManager.getSession(IdType(workspaceId))?.rawLlmLog
        lastSessionLog = log
        if (log == null || log.isEmpty()) return@withContext emptyList()
        val usageByIteration = log.usageByIteration
        log.getEntries().map { entry ->
            RawLogEntryVd(
                title = entryTitle(entry),
                tokenInfo = entryTokenInfo(entry, usageByIteration),
                body = entryBody(entry),
            )
        }
    }

    override fun canReportBug(recordId: String?): Boolean {
        return if (recordId != null) {
            val record = dao.getById(IdType(recordId)) ?: return false
            AiBugReport.isReportAvailable(record.modelName)
        } else {
            // In-memory: mirror classic's `rawLog != null && usageByIteration.isNotEmpty()` gate, then
            // resolve the model from the last iteration.
            val log = lastSessionLog ?: return false
            if (log.usageByIteration.isEmpty()) return false
            AiBugReport.isReportAvailable(AiBugReport.resolveModelNameFromRawLog(log))
        }
    }

    // -- Summary mapping (mirrors classic RawLogHistoryAdapter.bind) --------------------------------

    private fun LlmRawLogSummary.toVd(): RawLogSummaryVd {
        val providerLabel = providerType.takeIf { it.isNotBlank() }
            ?.let { type -> try { LlmProvider.valueOf(type).displayName } catch (_: Exception) { type } }
            ?: ""
        val modelInfo = if (providerLabel.isNotBlank()) "$providerLabel · $modelName" else modelName
        val tokenInfo = application.getString(
            R.string.raw_log_item_tokens,
            LlmCostTracker.formatTokenCount(totalInputTokens),
            LlmCostTracker.formatTokenCount(totalOutputTokens),
        )
        val costInfo = if (estimatedCostUsd > 0) LlmCostTracker.formatCost(estimatedCostUsd) else ""
        return RawLogSummaryVd(
            id = id.toString(),
            promptName = promptName, // raw — screen owns the ifBlank { "—" } fallback
            modelInfo = modelInfo,
            tokenInfo = tokenInfo,
            costInfo = costInfo,
            timestamp = dateFormat.format(Date(timestamp)),
            hasError = wasError,
        )
    }

    // -- Entry formatting (ported from RawLlmLogAdapter.getTitle/getTokenInfo/formatEntry) ----------

    private fun entryTitle(entry: RawLogEntry): String = when (entry) {
        is RawLogEntry.Message -> when (entry.role.uppercase()) {
            "SYSTEM" -> application.getString(R.string.raw_llm_log_entry_system)
            "USER" -> application.getString(R.string.raw_llm_log_entry_user)
            "ASSISTANT" -> application.getString(R.string.raw_llm_log_entry_assistant)
            else -> entry.role
        }
        is RawLogEntry.ToolCallEntry -> application.getString(R.string.raw_llm_log_entry_tool_call, entry.toolName)
        is RawLogEntry.ToolResultEntry -> application.getString(R.string.raw_llm_log_entry_tool_result, entry.id)
        is RawLogEntry.ToolDefinitionsEntry -> application.getString(R.string.raw_llm_log_entry_tool_definitions, entry.toolDefs.size)
        is RawLogEntry.RawApiResponse -> application.getString(R.string.raw_llm_log_entry_api_response, entry.iteration)
    }

    private fun entryTokenInfo(entry: RawLogEntry, usageByIteration: Map<Int, net.bible.service.llm.agent.IterationUsageData>): String = when (entry) {
        is RawLogEntry.RawApiResponse -> {
            val data = usageByIteration[entry.iteration]
            if (data != null) {
                val usage = data.usage
                val cost = LlmPricing.estimateCost(usage, data.model, data.configuredModelId)
                val costStr = if (cost != null) " · ${LlmCostTracker.formatCost(cost)}" else ""
                application.getString(
                    R.string.raw_llm_log_entry_usage,
                    LlmCostTracker.formatTokenCount(usage.inputTokens),
                    LlmCostTracker.formatTokenCount(usage.outputTokens),
                    costStr,
                )
            } else {
                application.getString(R.string.raw_llm_log_entry_tokens, LlmCostTracker.formatTokenCount(entry.estimateTokens().toLong()))
            }
        }
        else -> application.getString(R.string.raw_llm_log_entry_tokens, LlmCostTracker.formatTokenCount(entry.estimateTokens().toLong()))
    }

    private fun entryBody(entry: RawLogEntry): String = when (entry) {
        is RawLogEntry.Message -> entry.content ?: application.getString(R.string.raw_llm_log_entry_empty)
        is RawLogEntry.ToolCallEntry -> prettyFormatJson(entry.arguments)
        is RawLogEntry.ToolResultEntry -> prettyFormatJson(entry.result)
        is RawLogEntry.ToolDefinitionsEntry -> buildString {
            for (def in entry.toolDefs) {
                appendLine("--- ${def.name} ---")
                appendLine(application.getString(R.string.raw_llm_log_entry_tool_desc, def.description))
                appendLine(application.getString(R.string.raw_llm_log_entry_tool_params, prettyJson.encodeToString(def.parametersSchema)))
                appendLine()
            }
        }
        is RawLogEntry.RawApiResponse -> prettyFormatJson(entry.body)
    }

    companion object {
        private val prettyJson = Json { prettyPrint = true }

        private val longStringValueRegex = Regex(""""((?:[^"\\]|\\.){80,})"""")

        private fun prettyFormatJson(json: String): String = try {
            val trimmed = json.trim()
            val formatted = when {
                trimmed.startsWith("{") -> JSONObject(trimmed).toString(2)
                trimmed.startsWith("[") -> JSONArray(trimmed).toString(2)
                else -> json
            }
            longStringValueRegex.replace(formatted) { match ->
                match.value
                    .replace("\\n", "\n")
                    .replace("\\t", "\t")
            }
        } catch (_: Exception) {
            json
        }
    }
}

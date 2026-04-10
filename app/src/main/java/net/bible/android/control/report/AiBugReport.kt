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

package net.bible.android.control.report

import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import androidx.webkit.WebViewCompat
import net.bible.android.BibleApplication
import net.bible.android.SharedConstants
import net.bible.android.activity.BuildConfig
import net.bible.android.activity.R
import net.bible.android.database.IdType
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.base.Dialogs
import net.bible.service.common.CommonUtils
import net.bible.service.db.DatabaseContainer
import net.bible.service.llm.LlmPricing
import net.bible.service.llm.LlmProvider
import net.bible.service.llm.LlmRawLogRecord
import net.bible.service.llm.LlmUsage
import net.bible.service.llm.agent.RawLlmLog
import java.io.File
import java.util.Date

object AiBugReport {

    /** Whether bug reporting is available for a given model (supported models only). */
    fun isReportAvailable(modelName: String): Boolean = LlmProvider.isModelSupported(modelName)

    suspend fun reportAiBug(activity: ActivityBase, logRecordId: IdType) {
        val record = DatabaseContainer.instance.aiSettingsDb.llmRawLogRecordDao().getById(logRecordId) ?: return
        if (!isReportAvailable(record.modelName)) return

        val confirmed = Dialogs.simpleQuestion(
            activity,
            message = activity.getString(R.string.bug_report_email_text),
            title = activity.getString(R.string.send_ai_bug_report_title),
        )
        if (!confirmed) return

        val logDir = File(SharedConstants.internalFilesDir, "/log")
        logDir.mkdirs()
        val logFile = File(logDir, "ai_raw_log.txt.gz")
        logFile.outputStream().use { it.write(record.logData) }

        val uri = FileProvider.getUriForFile(activity, BuildConfig.APPLICATION_ID + ".provider", logFile)
        val body = buildReportBody(record)
        val subject = "AI Bug Report v${CommonUtils.applicationVersionName}: ${record.promptName} (${record.modelName})"

        val emailIntent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
            putExtra(Intent.EXTRA_EMAIL, arrayOf("errors.andbible@gmail.com"))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            type = "application/gzip"
        }
        val chooser = Intent.createChooser(emailIntent, activity.getString(R.string.send_ai_bug_report_title))
        chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        activity.awaitIntent(chooser)
    }

    /** Report from an in-memory RawLlmLog (active session). */
    suspend fun reportAiBugFromRawLog(activity: ActivityBase, rawLog: RawLlmLog) {
        if (rawLog.isEmpty()) return
        val lastIteration = rawLog.usageByIteration.values.lastOrNull() ?: return
        if (!isReportAvailable(lastIteration.model)) return

        val confirmed = Dialogs.simpleQuestion(
            activity,
            message = activity.getString(R.string.bug_report_email_text),
            title = activity.getString(R.string.send_ai_bug_report_title),
        )
        if (!confirmed) return

        val totalUsage = rawLog.usageByIteration.values.fold(LlmUsage()) { acc, d -> acc + d.usage }
        val cost = LlmPricing.estimateCost(totalUsage, lastIteration.model, lastIteration.configuredModelId) ?: 0.0
        val gzipped = RawLlmLog.gzipCompress(rawLog.format())

        val logDir = File(SharedConstants.internalFilesDir, "/log")
        logDir.mkdirs()
        val logFile = File(logDir, "ai_raw_log.txt.gz")
        logFile.outputStream().use { it.write(gzipped) }

        val uri = FileProvider.getUriForFile(activity, BuildConfig.APPLICATION_ID + ".provider", logFile)
        val model = lastIteration.model
        val resolvedProviderType = resolveProviderType(lastIteration.configuredModelId)
        val subject = "AI Bug Report v${CommonUtils.applicationVersionName}: $model"
        val body = buildReportBodyFromRawLog(model, resolvedProviderType, totalUsage, cost, rawLog.usageByIteration.size)

        val emailIntent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
            putExtra(Intent.EXTRA_EMAIL, arrayOf("errors.andbible@gmail.com"))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            type = "application/gzip"
        }
        val chooser = Intent.createChooser(emailIntent, activity.getString(R.string.send_ai_bug_report_title))
        chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        activity.awaitIntent(chooser)
    }

    private fun resolveProviderType(configuredModelId: IdType?): String {
        if (configuredModelId == null) return ""
        val db = DatabaseContainer.instance.aiSettingsDb
        val model = db.llmConfiguredModelDao().getById(configuredModelId) ?: return ""
        val provider = db.llmProviderConfigDao().getById(model.providerConfigId) ?: return ""
        return provider.providerType
    }

    /** Resolve model name from an in-memory RawLlmLog's iteration data. */
    fun resolveModelNameFromRawLog(rawLog: RawLlmLog): String =
        rawLog.usageByIteration.values.lastOrNull()?.model ?: ""

    private fun buildReportBodyFromRawLog(
        model: String, providerType: String, usage: LlmUsage, cost: Double, iterationCount: Int
    ): String {
        val app = BibleApplication.application
        return buildString {
            appendLine("--- AI Bug Report ---")
            appendLine()
            appendLine("Please describe the issue:")
            appendLine()
            appendLine()
            appendLine("--- Details ---")
            appendLine("Model: $model")
            appendLine("Provider: $providerType")
            appendLine("Timestamp: ${TIMESTAMP_FORMAT.format(Date())}")
            appendLine("Iterations: $iterationCount")
            appendLine("Tokens: ${usage.inputTokens} in / ${usage.outputTokens} out")
            if (cost > 0) appendLine("Estimated cost: \$%.4f".format(cost))
            appendLine()
            appendLine("--- Device ---")
            appendLine("App: ${CommonUtils.applicationVersionName}")
            appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("WebView: ${WebViewCompat.getCurrentWebViewPackage(app)?.versionName}")
            appendLine()
            appendLine("Attached: ai_raw_log.txt.gz (gzipped raw LLM conversation log)")
        }
    }

    private fun buildReportBody(record: LlmRawLogRecord): String {
        val app = BibleApplication.application
        return buildString {
            appendLine("--- AI Bug Report ---")
            appendLine()
            appendLine("Please describe the issue:")
            appendLine()
            appendLine()
            appendLine("--- Details ---")
            appendLine("Prompt: ${record.promptName}")
            if (!record.promptDescription.isNullOrBlank()) appendLine("Description: ${record.promptDescription}")
            appendLine("Model: ${record.modelName}")
            appendLine("Provider: ${record.providerType}")
            appendLine("Timestamp: ${TIMESTAMP_FORMAT.format(Date(record.timestamp))}")
            appendLine("Iterations: ${record.iterationCount}")
            appendLine("Tokens: ${record.totalInputTokens} in / ${record.totalOutputTokens} out")
            appendLine("Error: ${record.wasError}")
            appendLine()
            appendLine("--- Device ---")
            appendLine("App: ${CommonUtils.applicationVersionName}")
            appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("WebView: ${WebViewCompat.getCurrentWebViewPackage(app)?.versionName}")
            appendLine()
            appendLine("Attached: ai_raw_log.txt.gz (gzipped raw LLM conversation log)")
        }
    }
}

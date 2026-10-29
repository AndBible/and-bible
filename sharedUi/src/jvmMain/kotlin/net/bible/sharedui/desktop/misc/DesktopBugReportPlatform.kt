package net.bible.sharedui.desktop.misc

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.bible.android.database.IdType
import net.bible.service.db.DatabaseContainer
import net.bible.service.llm.AiBugReportRules
import net.bible.service.llm.agent.RawLlmLog
import net.bible.sharedui.app.ai.BugReportPlatform

class DesktopBugReportPlatform(private val report: DesktopBugReport) : BugReportPlatform {
    override suspend fun reportRecord(recordId: String) {
        val record = withContext(Dispatchers.Default) {
            DatabaseContainer.instance.aiSettingsDb.llmRawLogRecordDao().getById(IdType(recordId))
        } ?: return
        report.ai("${record.promptName} (${record.modelName})",
            "Prompt: ${record.promptName}\nDescription: ${record.promptDescription.orEmpty()}\nModel: ${record.modelName}\nProvider: ${record.providerType}\nTimestamp: ${record.timestamp}\nIterations: ${record.iterationCount}\nTokens: ${record.totalInputTokens} in / ${record.totalOutputTokens} out\nError: ${record.wasError}",
            record.logData, AiBugReportRules.isReportAvailable(record.modelName))
    }

    override suspend fun reportRawLog(rawLog: RawLlmLog) {
        if (rawLog.isEmpty()) return
        val last = rawLog.usageByIteration.values.lastOrNull() ?: return
        if (!AiBugReportRules.isReportAvailable(last.model)) return
        val log = withContext(Dispatchers.Default) { RawLlmLog.gzipCompress(rawLog.format()) }
        report.ai(last.model, "Model: ${last.model}\nIterations: ${rawLog.usageByIteration.size}", log, true)
    }
}

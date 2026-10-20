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
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.ios.ports

import net.bible.service.llm.agent.RawLlmLog
import net.bible.sharedui.app.ai.BugReportPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.bible.android.database.IdType
import net.bible.service.db.DatabaseContainer
import net.bible.service.llm.AiBugReportRules
import net.bible.sharedui.assembly.IosAppStartup
import net.bible.sharedui.ios.report.IosBugReport

/** AI reports use the same confirmation and retained mail/share lifecycle as general reports. */
@kotlin.native.HiddenFromObjC
class IosBugReportPlatform : BugReportPlatform {
    override suspend fun reportRecord(recordId: String) {
        val record = withContext(Dispatchers.Default) {
            DatabaseContainer.instance.aiSettingsDb.llmRawLogRecordDao().getById(IdType(recordId))
        } ?: return
        IosBugReport(IosAppStartup.stringTable).ai(
            "${record.promptName} (${record.modelName})",
            "Prompt: ${record.promptName}\nDescription: ${record.promptDescription.orEmpty()}\nModel: ${record.modelName}\nProvider: ${record.providerType}\nTimestamp: ${record.timestamp}\nIterations: ${record.iterationCount}\nTokens: ${record.totalInputTokens} in / ${record.totalOutputTokens} out\nError: ${record.wasError}",
            record.logData, AiBugReportRules.isReportAvailable(record.modelName),
        )
    }

    override suspend fun reportRawLog(rawLog: RawLlmLog) {
        if (rawLog.isEmpty()) return
        val last = rawLog.usageByIteration.values.lastOrNull() ?: return
        if (!AiBugReportRules.isReportAvailable(last.model)) return
        val log = withContext(Dispatchers.Default) { RawLlmLog.gzipCompress(rawLog.format()) }
        IosBugReport(IosAppStartup.stringTable).ai(last.model,
            "Model: ${last.model}\nIterations: ${rawLog.usageByIteration.size}", log, true)
    }
}

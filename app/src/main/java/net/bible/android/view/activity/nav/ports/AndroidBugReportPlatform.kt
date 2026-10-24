package net.bible.android.view.activity.nav.ports

import net.bible.android.control.report.AiBugReport
import net.bible.android.database.IdType
import net.bible.android.view.activity.base.ActivityBase
import net.bible.service.llm.agent.RawLlmLog
import net.bible.sharedui.app.ai.BugReportPlatform

/** The AI bug report mail, through [AiBugReport] (which asks first and attaches the gzipped log). */
class AndroidBugReportPlatform(private val activity: ActivityBase) : BugReportPlatform {
    override suspend fun reportRecord(recordId: String) = AiBugReport.reportAiBug(activity, IdType(recordId))

    override suspend fun reportRawLog(rawLog: RawLlmLog) = AiBugReport.reportAiBugFromRawLog(activity, rawLog)
}

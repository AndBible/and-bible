package net.bible.sharedcore.ai

import kotlinx.coroutines.flow.StateFlow

/**
 * Host seam for the raw LLM log screens: `RawLogHistoryActivity` (persisted list,
 * `LlmRawLogRecordDao`) and `RawLlmLogActivity` (detail — either a persisted record, decompressed,
 * or the live in-memory log of an active `AgentSession`), plus the "Report a bug" action
 * (`net.bible.android.control.report.AiBugReport`).
 */
interface RawLogService {
    /** Persisted log summaries, newest first (`LlmRawLogRecordDao.allSummaries`). */
    val summaries: StateFlow<List<RawLogSummaryVd>>

    /** Re-read [summaries] from the DB (host calls on resume / after a mutation). */
    fun refresh()

    /** Delete the given persisted records by [RawLogSummaryVd.id] (`LlmRawLogRecordDao.deleteByIds`). */
    fun deleteByIds(ids: Set<String>)

    /**
     * Delete persisted records older than [days] (`LlmRawLogRecordDao.deleteOlderThan`, cutoff =
     * `now - days*24h`). [days] is one of 7/30/90 (the classic "older than 1 week/month/3 months"
     * choices); `-1` (or any non-positive sentinel) means [deleteAll] instead.
     */
    fun deleteOlderThan(days: Int)

    /** Delete all persisted records (`LlmRawLogRecordDao.deleteAll`; classic "Delete all"). */
    fun deleteAll()

    /**
     * DB-mode detail: the gzip-decompressed formatted log text (`RawLlmLog.gzipDecompress` on
     * `LlmRawLogRecord.logData`) for a persisted record by [RawLogSummaryVd.id], or null if the
     * record no longer exists. Includes the "total tokens/cost" header line the caller needs to
     * render above the (plain-text, non-expandable) body — pre-baked into the returned string so
     * commonMain doesn't need `LlmCostTracker`/resource formatting for this path.
     */
    suspend fun recordText(recordId: String): String?

    /**
     * In-memory mode detail: the live, expandable entries of the active `AgentSession` for
     * [workspaceId] (`AgentSessionManager.getSession(workspaceId)?.rawLlmLog?.getEntries()`,
     * pre-formatted per [RawLogEntryVd]). Empty if there's no active session or its log is empty.
     */
    suspend fun sessionEntries(workspaceId: String): List<RawLogEntryVd>

    /**
     * Whether the "Report a bug" action should be enabled for the given context, mirroring
     * `AiBugReport.isReportAvailable(modelName)` (only models `LlmProvider.isModelSupported`).
     * [recordId] non-null: DB-mode record (looks up its `modelName`, `null` if missing).
     * [recordId] null: in-memory mode (resolves the model from the active session's last
     * iteration, `AiBugReport.resolveModelNameFromRawLog`; false if there's no usage yet).
     */
    fun canReportBug(recordId: String?): Boolean
}

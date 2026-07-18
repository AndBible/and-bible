package net.bible.sharedcore.ai

/**
 * View-data for the AI document filter, global tool-permission and raw-log screens.
 *
 * `ToolVd`/`ToolCategoryVd`/`ToolPermission` are already defined in `PromptModels.kt` and are
 * reused here (the PROMPT-mode `DEFAULT` value of [ToolPermission] is not used by the GLOBAL-only
 * [ToolPermissionService] — see its KDoc). Service interfaces live in their own files:
 * [DocumentFilterService], [ToolPermissionService], [RawLogService].
 */

/**
 * A document category group for the AI document filter screen.
 *
 * Mirrors `org.crosswire.jsword.book.BookCategory`: `categoryId` = the enum constant identifier
 * (e.g. `"BIBLE"`, `"COMMENTARY"`, `"DICTIONARY"`, `"GENERAL_BOOK"` — the four categories the
 * classic `AiDocumentFilterActivity` lists, in that order; empty categories are omitted),
 * `categoryLabel` = the JSword-localized display string (`BookCategory.toString()`,
 * i.e. its `externalName`, NOT the raw enum identifier).
 */
data class AiDocGroupVd(val categoryId: String, val categoryLabel: String, val docs: List<AiDocVd>)

/**
 * A single installed document row in the AI document filter screen.
 *
 * Mirrors one `org.crosswire.jsword.book.Book` from `Books.installed().books`: `initials` =
 * `Book.initials`, `name` = `Book.name`. `allowed` = `initials !in
 * GlobalAiSettings.aiExcludedDocuments` (the filter is a blacklist — checked/allowed by
 * default; unchecking excludes it). Mirrors `net.bible.service.llm.tools.AiDocumentFilter.isAllowed`.
 */
data class AiDocVd(val initials: String, val name: String, val allowed: Boolean)

/**
 * A tool-category group with its tools' current global permission, for the shared
 * ToolPermissionList composable (the CMP analogue of `ToolPermissionListBuilder`'s per-category
 * sections). Not returned directly by [ToolPermissionService.toolsByCategory] (which — like
 * `PromptService.toolsByCategory` — returns `List<Pair<ToolCategoryVd, List<ToolVd>>>` so a
 * single shape serves both the GLOBAL and PROMPT-mode tool lists); a controller assembles this
 * from that plus [ToolPermissionService.permissionFor] per tool.
 */
data class ToolPermGroupVd(val category: ToolCategoryVd, val tools: List<ToolVd>)

/**
 * A raw-log list row (lightweight projection, no log body) for `RawLogHistoryActivity`.
 *
 * Mirrors `net.bible.service.llm.LlmRawLogSummary`: `id` = `LlmRawLogRecord.id.toString()`,
 * `promptName` = `promptName` (blank -> "—" is a display concern left to the renderer),
 * `modelInfo` = pre-formatted `"<provider display name> · <modelName>"` (provider resolved via
 * `LlmProvider.valueOf(providerType).displayName`, falling back to the raw `providerType` string;
 * omitted if blank), `tokenInfo` = pre-formatted `"<in> in / <out> out"` token counts
 * (`LlmCostTracker.formatTokenCount`), `costInfo` = pre-formatted cost string
 * (`LlmCostTracker.formatCost`, `""` when `estimatedCostUsd <= 0`), `timestamp` = pre-formatted
 * date/time string (`SimpleDateFormat("yyyy-MM-dd HH:mm")`), `hasError` = `wasError`.
 */
data class RawLogSummaryVd(
    val id: String,
    val promptName: String,
    val modelInfo: String,
    val tokenInfo: String,
    val costInfo: String,
    val timestamp: String,
    val hasError: Boolean,
)

/**
 * A single expandable entry in the raw LLM conversation log (`RawLlmLogActivity`'s
 * `RawLlmLogAdapter` row), pre-formatted host-side so commonMain never sees `RawLogEntry`,
 * `R.string`, or JSON pretty-printing.
 *
 * Mirrors one `net.bible.service.llm.agent.RawLogEntry` (sealed: `Message`/`ToolCallEntry`/
 * `ToolResultEntry`/`ToolDefinitionsEntry`/`RawApiResponse`): `title` = the entry's localized
 * header (e.g. "User", "Tool call: getVerseContent", "API response (iteration 2)" —
 * `RawLlmLogAdapter.getTitle`), `tokenInfo` = pre-formatted token/cost string (usage-tracked
 * iterations show `"<in> in / <out> out · $cost"`, others a heuristic `estimateTokens()` count —
 * `RawLlmLogAdapter.getTokenInfo`), `body` = the pretty-printed/unescaped entry content shown when
 * expanded (`RawLlmLogAdapter.formatEntry`/`prettyFormatJson`).
 */
data class RawLogEntryVd(val title: String, val tokenInfo: String, val body: String)

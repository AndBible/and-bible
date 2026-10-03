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

package net.bible.service.llm.agent

import android.app.Activity
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import net.bible.android.BibleApplication.Companion.application
import net.bible.android.activity.R
import net.bible.service.llm.AgentTool
import net.bible.android.control.event.ABEventBus
import net.bible.android.database.IdType
import net.bible.android.view.activity.base.CurrentActivityHolder
import net.bible.android.view.activity.base.Dialogs
import net.bible.service.common.CommonUtils
import net.bible.service.common.useSaxBuilder
import net.bible.service.llm.tools.AiDocumentFilter
import net.bible.service.sword.OsisToPlainText
import net.bible.service.llm.AgentPrompt
import net.bible.service.llm.ChatMessage
import net.bible.service.llm.LlmApiAdapter
import net.bible.service.llm.LlmModelConfig
import net.bible.service.llm.LlmUsage
import net.bible.service.llm.LlmProcessingService
import net.bible.service.llm.ParsedResponse
import net.bible.service.llm.ToolCall
import net.bible.service.llm.ToolResultBlock
import net.bible.service.llm.tools.Tool
import net.bible.service.llm.tools.ToolRegistry
import net.bible.service.llm.tools.ToolResult
import net.bible.service.llm.tools.normalizeLlmText
import net.bible.service.llm.tools.read.GetCommentariesTool
import net.bible.service.llm.tools.read.GetInstalledDocumentsTool
import net.bible.service.llm.tools.write.AddMyDocumentPageTool
import net.bible.service.llm.tools.write.SetDocumentTitleTool
import net.bible.service.llm.tools.write.FinishWithMyDocumentPageTool
import net.bible.service.llm.tools.write.FinishWithStudyPadTool
import net.bible.service.llm.tools.write.FinishWithoutDocumentTool
import net.bible.service.llm.tools.ToolDefinition
import net.bible.android.database.bookmarks.TextContentType
import net.bible.android.database.mydocument.MyDocumentPageContent
import net.bible.service.sword.mydocument.MyDocumentBookManager
import net.bible.service.sword.mydocument.AiDocPagesChangedEvent
import net.bible.service.db.DatabaseContainer
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.index.IndexStatus
import org.json.JSONObject
import java.io.StringReader

private const val TAG = "AgentExecutor"
private const val DEFAULT_MAX_ITERATIONS = 10

/** Compact representation of a tool call for loop detection. */
internal data class ToolCallSignature(val tool: AgentTool, val argsHash: Int)

internal fun extractSignatures(toolCalls: List<ToolCall>): List<ToolCallSignature> =
    toolCalls.map { tc ->
        val normalized = try {
            val json = JSONObject(tc.arguments)
            // Sort keys for consistent hashing regardless of key order
            JSONObject().apply {
                json.keys().asSequence().sorted().forEach { key -> put(key, json.get(key)) }
            }.toString()
        } catch (_: Exception) { tc.arguments }
        ToolCallSignature(tc.tool, normalized.hashCode())
    }

/**
 * Detect repetitive tool call patterns.
 * Returns true if any single (tool, args) signature appears [threshold] or more times
 * in the last [windowSize] calls.
 */
internal fun detectLoop(
    history: List<ToolCallSignature>,
    threshold: Int = 3,
    windowSize: Int = 5
): Boolean {
    if (history.size < threshold) return false
    val window = history.takeLast(windowSize)
    return window.groupBy { it }.any { (_, v) -> v.size >= threshold }
}

/**
 * Computes the set of tools to exclude from LLM tool definitions.
 *
 * Exclusion is deny-based: tools are excluded only if they appear in [permanentlyDeniedTools]
 * or [promptDeniedTools]. [promptAvailableTools] overrides both deny sets, allowing built-in
 * prompts to re-enable tools that the user has globally disabled.
 *
 * Tool visibility (which tools the LLM can see) is controlled by [promptDeniedTools].
 * Permission auto-allow (which tools bypass the permission dialog) is controlled by
 * [AgentContext.promptAllowedTools] in [checkPermission], not here.
 *
 * Structural tools (setDocumentTitle, finishWithStudyPad, etc.) are never excluded.
 */
fun computeExcludedTools(
    permanentlyDeniedTools: Set<AgentTool>,
    promptDeniedTools: Set<AgentTool>?,
    promptAvailableTools: Set<AgentTool>?,
): Set<AgentTool> {
    val excluded = mutableSetOf<AgentTool>()
    excluded.addAll(permanentlyDeniedTools)
    promptDeniedTools?.let { excluded.addAll(it) }
    // Prompt-level available overrides both global and prompt deny
    promptAvailableTools?.let { excluded.removeAll(it) }
    excluded.removeAll(ToolRegistry.STRUCTURAL_TOOLS)
    return excluded
}

private sealed class ProcessToolsResult {
    data class Continue(
        val context: AgentContext,
        val pendingDocumentTitle: String? = null
    ) : ProcessToolsResult()
    data class FinishWithDocument(
        val title: String,
        val content: String,
        val context: AgentContext
    ) : ProcessToolsResult()
    data class FinishWithoutDocument(
        val message: String,
        val context: AgentContext
    ) : ProcessToolsResult()
    data class FinishWithStudyPad(
        val labelId: IdType,
        val scrollToEntryId: IdType?,
        val message: String,
        val context: AgentContext
    ) : ProcessToolsResult()
    data class FinishWithMyDocumentPage(
        val documentInitials: String,
        val pageKey: String,
        val message: String,
        val context: AgentContext
    ) : ProcessToolsResult()
}

/**
 * Executes agent prompts with tool calling capability.
 *
 * The executor runs an iterative loop:
 * 1. Sends the prompt and context to the LLM with available tools
 * 2. If LLM responds with tool_calls, executes them and returns results
 * 3. Repeats until LLM returns a final response (no tool calls)
 * 4. Returns the final response
 *
 * Events are emitted via a Flow to allow UI updates during execution.
 */
class AgentExecutor(
    private val maxIterations: Int = DEFAULT_MAX_ITERATIONS
) {
    fun execute(prompt: AgentPrompt, context: AgentContext, rawLlmLog: RawLlmLog? = null, modelOverrideId: IdType? = null): Flow<AgentEvent> = flow {
        try {
            val llmConfig = LlmModelConfig(modelOverrideId ?: prompt.configuredModelId)
            val resolved = LlmProcessingService.resolveFromConfig(llmConfig)
            emit(AgentEvent.Started(resolved.model))
            val messages = buildInitialMessages(prompt, context)
            val excludedTools = computeExcludedTools(prompt, context)
            val toolDefs = ToolRegistry.getToolDefinitions(excludedTools = excludedTools)

            // Capture tool definitions in raw log
            rawLlmLog?.addToolDefinitions(toolDefs)

            // Capture initial messages in raw log
            for (msg in messages) {
                rawLlmLog?.addMessage(msg.role.name, msg.content)
            }

            runAgentLoop(prompt, messages, toolDefs, resolved.adapter, context, llmConfig, rawLlmLog, resolved)

        } catch (e: CancellationException) {
            emit(AgentEvent.Cancelled)
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Agent execution failed", e)
            emit(AgentEvent.Error(e.message ?: application.getString(R.string.llm_error_unknown), e))
        }
    }

    private suspend fun FlowCollector<AgentEvent>.runAgentLoop(
        prompt: AgentPrompt,
        messages: MutableList<ChatMessage>,
        tools: List<ToolDefinition>,
        adapter: LlmApiAdapter,
        context: AgentContext,
        llmConfig: LlmModelConfig? = null,
        rawLlmLog: RawLlmLog? = null,
        preResolved: LlmProcessingService.ResolvedProvider? = null
    ) {
        var iteration = 0
        var iterationLimit = if (maxIterations > 0) maxIterations else Int.MAX_VALUE
        var currentContext = context  // Mutable context for session permission tracking
        var totalUsage = LlmUsage()
        var pendingDocumentTitle: String? = null
        val toolCallHistory = mutableListOf<ToolCallSignature>()
        val resolved = preResolved ?: LlmProcessingService.resolveFromConfig(llmConfig)
        val loopHeaders = LlmProcessingService.buildProviderExtraHeaders(resolved.providerConfig)

        loop@ while (true) {
            while (iteration < iterationLimit) {
                iteration++
                emit(AgentEvent.Iteration(iteration))
                currentCoroutineContext().ensureActive()

                val (parsed, callUsage) = callLlmAndParse(adapter, messages, tools, iteration, llmConfig, loopHeaders, rawLlmLog, resolved)
                totalUsage += callUsage
                rawLlmLog?.addUsageForIteration(iteration, callUsage, resolved.model, resolved.configuredModelId)

                // Emit per-operation usage
                if (callUsage.totalTokens > 0) {
                    emit(AgentEvent.ApiCallCompleted(callUsage, resolved.model, resolved.configuredModelId))
                }

                when (parsed) {
                    is ParsedResponse.ToolCalls -> {
                        toolCallHistory.addAll(extractSignatures(parsed.toolCalls))
                        val loopDetected = detectLoop(toolCallHistory)
                        if (loopDetected) {
                            Log.w(TAG, "Loop detected at iteration $iteration: repeated tool calls")
                        }
                        val loopHint = if (loopDetected)
                            "SYSTEM NOTE: You appear to be repeating the same tool calls without making progress. " +
                            "The tool has returned the same results multiple times. Please try a completely different " +
                            "approach, use different tools, or complete your response with the information you already have. " +
                            "Do not retry the same tool with similar arguments."
                        else null

                        when (val result = processToolCalls(prompt, adapter, parsed, messages, currentContext, rawLlmLog, loopHint)) {
                            is ProcessToolsResult.Continue -> {
                                currentContext = result.context
                                if (result.pendingDocumentTitle != null) {
                                    pendingDocumentTitle = result.pendingDocumentTitle
                                }
                                if (loopDetected) toolCallHistory.clear()
                            }
                            is ProcessToolsResult.FinishWithDocument -> {
                                Log.d(TAG, "Agent finished with document: ${result.title}")
                                emit(AgentEvent.CompletedWithDocument(
                                    title = result.title,
                                    content = result.content,
                                    totalIterations = iteration,
                                    usage = totalUsage,
                                    model = resolved.model,
                                    configuredModelId = resolved.configuredModelId
                                ))
                                return
                            }
                            is ProcessToolsResult.FinishWithoutDocument -> {
                                Log.d(TAG, "Agent finished without document: ${result.message}")
                                emit(AgentEvent.CompletedWithoutDocument(
                                    message = result.message,
                                    totalIterations = iteration,
                                    usage = totalUsage,
                                    model = resolved.model,
                                    configuredModelId = resolved.configuredModelId
                                ))
                                return
                            }
                            is ProcessToolsResult.FinishWithStudyPad -> {
                                Log.d(TAG, "Agent finished with StudyPad: ${result.labelId}")
                                emit(AgentEvent.CompletedWithStudyPad(
                                    labelId = result.labelId,
                                    scrollToEntryId = result.scrollToEntryId,
                                    message = result.message,
                                    totalIterations = iteration,
                                    usage = totalUsage,
                                    model = resolved.model,
                                    configuredModelId = resolved.configuredModelId
                                ))
                                return
                            }
                            is ProcessToolsResult.FinishWithMyDocumentPage -> {
                                Log.d(TAG, "Agent finished with My Document page: ${result.documentInitials}/${result.pageKey}")
                                emit(AgentEvent.CompletedWithMyDocumentPage(
                                    documentInitials = result.documentInitials,
                                    pageKey = result.pageKey,
                                    message = result.message,
                                    totalIterations = iteration,
                                    usage = totalUsage,
                                    model = resolved.model,
                                    configuredModelId = resolved.configuredModelId
                                ))
                                return
                            }
                        }
                    }
                    is ParsedResponse.TextResponse -> {
                        val normalizedContent = normalizeLlmText(parsed.content)
                        val title = pendingDocumentTitle
                        if (prompt.isTextTransformation && currentContext.noteEditorEntityType != null && normalizedContent.isNotBlank()) {
                            // Text transformation in note editor: route content back to the note
                            saveNoteContent(currentContext, normalizedContent)
                            Log.d(TAG, "Text transformation (text response) saved to note: ${currentContext.noteEditorEntityType}/${currentContext.noteEditorEntityId}")
                            emit(AgentEvent.CompletedWithoutDocument(
                                message = application.getString(R.string.llm_note_updated),
                                totalIterations = iteration,
                                usage = totalUsage,
                                model = resolved.model,
                                configuredModelId = resolved.configuredModelId
                            ))
                        } else if (title != null) {
                            Log.d(TAG, "LLM returned text response, combining with pending title: $title")
                            emit(AgentEvent.CompletedWithDocument(
                                title = title,
                                content = normalizedContent,
                                totalIterations = iteration,
                                usage = totalUsage,
                                model = resolved.model,
                                configuredModelId = resolved.configuredModelId
                            ))
                        } else {
                            Log.d(TAG, "LLM returned final text response without tool call")
                            emit(AgentEvent.TextResponse(
                                text = normalizedContent,
                                isFinal = true
                            ))
                            emit(AgentEvent.Completed(
                                response = normalizedContent,
                                totalIterations = iteration,
                                usage = totalUsage,
                                model = resolved.model,
                                configuredModelId = resolved.configuredModelId
                            ))
                        }
                        return
                    }
                    is ParsedResponse.ParseError -> {
                        emit(AgentEvent.Error(application.getString(R.string.llm_parse_error, parsed.error)))
                        return
                    }
                }
            }

            // Max iterations reached — ask user if they want to continue
            if (maxIterations > 0 && showContinueDialog(iteration, maxIterations, context.workspaceId)) {
                iterationLimit += maxIterations
                continue@loop
            }
            break
        }
        emit(AgentEvent.Error(application.getString(R.string.llm_error_max_iterations, iterationLimit)))
    }

    private suspend fun callLlmAndParse(
        adapter: LlmApiAdapter,
        messages: List<ChatMessage>,
        tools: List<ToolDefinition>,
        iteration: Int,
        llmConfig: LlmModelConfig? = null,
        extraHeaders: Map<String, String> = emptyMap(),
        rawLlmLog: RawLlmLog? = null,
        preResolved: LlmProcessingService.ResolvedProvider? = null
    ): Pair<ParsedResponse, LlmUsage> {
        Log.d(TAG, "Iteration $iteration: calling LLM API")
        val apiResponse = try {
            LlmProcessingService.callLlmApiWithTools(messages, tools, llmConfig, extraHeaders, preResolved)
        } catch (e: Exception) {
            rawLlmLog?.addRawApiResponse(iteration, "ERROR: ${e.message}")
            throw e
        }
        rawLlmLog?.addRawApiResponse(iteration, apiResponse.responseBody)
        val parsed = adapter.parseResponse(apiResponse.responseBody)
        return Pair(parsed, apiResponse.usage)
    }

    /**
     * All tool results are collected first and then added via [LlmApiAdapter.createToolResultMessages]
     * because Anthropic batches all tool results into a single user message.
     */
    private suspend fun FlowCollector<AgentEvent>.processToolCalls(
        prompt: AgentPrompt,
        adapter: LlmApiAdapter,
        parsed: ParsedResponse.ToolCalls,
        messages: MutableList<ChatMessage>,
        context: AgentContext,
        rawLlmLog: RawLlmLog? = null,
        loopHint: String? = null
    ): ProcessToolsResult {
        Log.d(TAG, "LLM requested ${parsed.toolCalls.size} tool calls")
        var currentContext = context
        var pendingTitle: String? = null

        parsed.content?.takeIf { it.isNotBlank() }?.let {
            emit(AgentEvent.TextResponse(it, isFinal = false))
        }

        messages.add(adapter.createAssistantToolCallMessage(parsed.toolCalls, parsed.content))

        // Execute all tools and collect results
        val toolResults = mutableListOf<ToolResultBlock>()
        var finishResult: ProcessToolsResult? = null

        for (toolCall in parsed.toolCalls) {
            currentCoroutineContext().ensureActive()

            emit(AgentEvent.ToolCalling(
                toolCallId = toolCall.id,
                tool = toolCall.tool,
                arguments = toolCall.arguments
            ))
            rawLlmLog?.addToolCall(toolCall.tool.camelCaseName, toolCall.id, toolCall.arguments)

            val execResult = executeTool(toolCall, currentContext)
            val result = execResult.result
            rawLlmLog?.addToolResult(toolCall.id, result.toJson())

            // Update session permissions based on user's dialog choice
            if (execResult.grantAllToolsPermission) {
                currentContext = currentContext.withAllToolsPermissionGranted()
            } else if (execResult.grantSessionPermission ||
                ((currentContext.promptPermissionMode ?: CommonUtils.aiSettings.agentPermissionMode) != PermissionMode.ALWAYS_ASK &&
                 result is ToolResult.Success && ToolRegistry.get(toolCall.tool)?.requiresPermission == true)) {
                currentContext = currentContext.withWritePermissionGranted()
            }

            // Track created page IDs for permission-free editing in this session
            if (toolCall.tool == AgentTool.ADD_MY_DOCUMENT_PAGE && result is ToolResult.Success) {
                val pageId = (result.data as? AddMyDocumentPageTool.Result)?.pageId
                if (pageId != null) {
                    currentContext = currentContext.copy(createdPageIds = currentContext.createdPageIds + pageId)
                }
            }

            emit(AgentEvent.ToolCompleted(toolCall.id, toolCall.tool, result))

            toolResults.add(ToolResultBlock(toolCall.id, result.toJson()))

            // Check for finish tools — record the result but continue collecting tool results
            if (finishResult == null && result is ToolResult.Success) {
                finishResult = checkForFinishResult(
                    toolCall, result, parsed.content, prompt, currentContext, toolResults
                )?.also { r ->
                    if (r is ProcessToolsResult.Continue && r.pendingDocumentTitle != null) {
                        pendingTitle = r.pendingDocumentTitle
                        finishResult = null  // Not actually finished, just set pending title
                    }
                }
            }
        }

        // Inject loop detection hint into the last tool result if needed
        if (loopHint != null && toolResults.isNotEmpty()) {
            val last = toolResults.last()
            toolResults[toolResults.lastIndex] = ToolResultBlock(last.toolCallId, last.content + "\n\n" + loopHint)
        }

        // Add all tool results to messages
        messages.addAll(adapter.createToolResultMessages(toolResults))

        return finishResult ?: ProcessToolsResult.Continue(currentContext, pendingDocumentTitle = pendingTitle)
    }

    /**
     * Check if a successful tool call represents a finish action.
     * Returns a [ProcessToolsResult] if the tool signals completion, or null to continue.
     * For setDocumentTitle with pending content, returns [ProcessToolsResult.Continue] with pendingDocumentTitle.
     */
    private fun checkForFinishResult(
        toolCall: ToolCall,
        result: ToolResult.Success,
        responseContent: String?,
        prompt: AgentPrompt,
        context: AgentContext,
        toolResults: MutableList<ToolResultBlock>
    ): ProcessToolsResult? = when (toolCall.tool) {
        AgentTool.SET_DOCUMENT_TITLE ->
            handleSetDocumentTitle(toolCall, result, responseContent, prompt, context, toolResults)
        AgentTool.FINISH_WITHOUT_DOCUMENT -> {
            val data = result.data as? FinishWithoutDocumentTool.Result
            ProcessToolsResult.FinishWithoutDocument(
                message = data?.message ?: application.getString(R.string.llm_default_task_completed),
                context = context
            )
        }
        AgentTool.FINISH_WITH_STUDY_PAD -> {
            val data = result.data as? FinishWithStudyPadTool.Result
            ProcessToolsResult.FinishWithStudyPad(
                labelId = IdType(data?.labelId ?: ""),
                scrollToEntryId = data?.scrollToEntryId?.takeIf { it.isNotBlank() }?.let { IdType(it) },
                message = data?.message ?: application.getString(R.string.llm_default_studypad_opened),
                context = context
            )
        }
        AgentTool.FINISH_WITH_MY_DOCUMENT_PAGE -> {
            val data = result.data as? FinishWithMyDocumentPageTool.Result
            ProcessToolsResult.FinishWithMyDocumentPage(
                documentInitials = data?.documentInitials ?: "",
                pageKey = data?.pageKey ?: "",
                message = data?.message ?: application.getString(R.string.llm_default_task_completed),
                context = context
            )
        }
        else -> {
            val rawArgs = try { JSONObject(toolCall.arguments) } catch (_: Exception) { null }
            if (rawArgs?.optBoolean("taskComplete", false) == true) {
                val message = rawArgs.optString("taskCompleteMessage", "").ifBlank {
                    application.getString(R.string.llm_default_task_completed)
                }
                ProcessToolsResult.FinishWithoutDocument(message = message, context = context)
            } else null
        }
    }

    /**
     * Handle setDocumentTitle tool: routes to note editor, blocks document creation,
     * or sets up document with title. Returns [ProcessToolsResult.Continue] with
     * pendingDocumentTitle when content is not yet available.
     */
    private fun handleSetDocumentTitle(
        toolCall: ToolCall,
        result: ToolResult.Success,
        responseContent: String?,
        prompt: AgentPrompt,
        context: AgentContext,
        toolResults: MutableList<ToolResultBlock>
    ): ProcessToolsResult {
        val content = responseContent?.takeIf { it.isNotBlank() }

        // Text transformation in note editor
        if (prompt.isTextTransformation && context.noteEditorEntityType != null) {
            if (content != null) {
                saveNoteContent(context, normalizeLlmText(content))
                Log.d(TAG, "Text transformation saved to note: ${context.noteEditorEntityType}/${context.noteEditorEntityId}")
                return ProcessToolsResult.FinishWithoutDocument(
                    message = application.getString(R.string.llm_note_updated),
                    context = context
                )
            }
            // Content not yet available — save pending title and wait
            val data = result.data as? SetDocumentTitleTool.Result
            toolResults[toolResults.lastIndex] = ToolResultBlock(
                toolCallId = toolCall.id, content = ToolResult.success {
                    put("titleSaved", true)
                    put("instruction", "Title accepted. Now output your transformed text in your next response.")
                }.toJson()
            )
            return ProcessToolsResult.Continue(context, pendingDocumentTitle = data?.title)
        }

        // No document creation mode
        if (context.noDocumentCreation) {
            Log.i(TAG, "setDocumentTitle blocked: noDocumentCreation is enabled")
            return ProcessToolsResult.FinishWithoutDocument(
                message = application.getString(R.string.llm_no_document_creation_intercepted),
                context = context
            )
        }

        // Normal document creation
        val data = result.data as? SetDocumentTitleTool.Result
        val title = data?.title ?: application.getString(R.string.llm_default_document_title)

        if (content == null) {
            Log.i(TAG, "setDocumentTitle called without content, saving pending title: $title")
            toolResults[toolResults.lastIndex] = ToolResultBlock(
                toolCallId = toolCall.id, content = ToolResult.success {
                    put("titleSaved", true)
                    put("title", title)
                    put("instruction", "Title accepted. Now output your document content as plain text in your next response.")
                }.toJson()
            )
            return ProcessToolsResult.Continue(context, pendingDocumentTitle = title)
        }

        Log.d(TAG, "Agent finished with document: $title (content from text response, ${content.length} chars)")
        return ProcessToolsResult.FinishWithDocument(
            title = title,
            content = normalizeLlmText(content),
            context = context
        )
    }

    private suspend fun buildInitialMessages(prompt: AgentPrompt, context: AgentContext): MutableList<ChatMessage> {
        val systemPrompt = buildSystemPrompt(prompt, context)
        val userMessage = buildUserMessage(prompt, context)
        return mutableListOf(
            ChatMessage(ChatMessage.Role.SYSTEM, content = systemPrompt),
            ChatMessage(ChatMessage.Role.USER, content = userMessage)
        )
    }

    private fun buildSystemPrompt(prompt: AgentPrompt, context: AgentContext): String {
        val appLanguage = CommonUtils.aiSettings.aiDisplayLanguage

        return buildString {
            val (customPrompt, templateRes) = if (prompt.isTextTransformation)
                CommonUtils.aiSettings.customTextTransformationSystemPrompt to R.raw.llm_text_transformation_system_prompt
            else
                CommonUtils.aiSettings.customAgentSystemPrompt to R.raw.llm_agent_system_prompt

            val basePrompt = customPrompt?.takeIf { it.isNotBlank() }
                ?: application.resources.openRawResource(templateRes)
                    .bufferedReader()
                    .use { it.readText() }
            append(basePrompt.replace("{{APP_LANGUAGE}}", appLanguage))

            // Text transformations use a minimal system prompt — no extra context needed
            if (prompt.isTextTransformation) return@buildString

            if (context.activeDocumentInitials != null) {
                append("Current active document: ${context.activeDocumentInitials}\n")
            }
            if (context.verseRefString != null) {
                append("Selected verse reference: ${context.verseRefString}\n")
            }
            if (prompt.allowedTools == null || AgentTool.SEARCH_BIBLE in prompt.allowedTools!!) {
                val defaultSearchBible = AiDocumentFilter.filterAllowed(
                    Books.installed().books.filterIsInstance<SwordBook>()
                ).firstOrNull { it.indexStatus == IndexStatus.DONE }
                if (defaultSearchBible != null) {
                    append("Default search Bible (for searchBible tool): ${defaultSearchBible.initials} (${defaultSearchBible.language?.name ?: "unknown language"})\n")
                }
            }
            if (context.selectionStartOffset != null && context.selectionEndOffset != null) {
                append("The user has highlighted specific text within a verse. " +
                    "Character offsets (startOffset/endOffset) are provided — these are character positions " +
                    "from the start of the verse text in the current translation (${context.activeDocumentInitials}). " +
                    "Use createBookmark with startOffset, endOffset, and bookInitials to create a sub-verse bookmark " +
                    "covering exactly the highlighted text, or adjust the offsets as needed.\n")
            }
            if (context.activeLabelId != null) {
                append("Active label/StudyPad ID: ${context.activeLabelId}\n")
            }

            if (context.noDocumentCreation) {
                append("\nIMPORTANT: This prompt is configured for action-only mode (no document creation). ")
                append("Do NOT call setDocumentTitle. When you are done, call finishWithoutDocument ")
                append("with a brief summary of what you did. Any text output will appear only in the activity log.\n")
            }

            if (context.noteEditorEntityType != null) {
                append("\n--- Note Editor Context ---\n")
                append("Entity type: ${context.noteEditorEntityType}\n")
                append("Entity ID: ${context.noteEditorEntityId}\n")
                append("Content type: ${context.noteEditorContentType}\n")
                when (context.noteEditorEntityType) {
                    NoteEditorEntityType.BOOKMARK_NOTE -> append("Use updateBookmarkNote with this bookmark ID to save changes.\n")
                    NoteEditorEntityType.STUDYPAD_TEXT -> append("Use updateStudyPadTextEntry with this entry ID to save changes.\n")
                    NoteEditorEntityType.MY_DOCUMENT_PAGE -> append("Use editMyDocumentPage with this page ID to save changes.\n")
                }
            }

            if (context.workspaceWindowsSummary != null) {
                append("\n--- Current Workspace ---\n")
                append(context.workspaceWindowsSummary)
            }

            val prefGreek = AiDocumentFilter.preferredStrongsGreek()
            val prefHebrew = AiDocumentFilter.preferredStrongsHebrew()
            val prefMorph = AiDocumentFilter.preferredRobinsonMorphology()
            if (prefGreek != null || prefHebrew != null || prefMorph != null) {
                append("\nPreferred reference dictionaries:\n")
                prefHebrew?.let { append("- Strong's Hebrew: $it\n") }
                prefGreek?.let { append("- Strong's Greek: $it\n") }
                prefMorph?.let { append("- Greek morphology: $it\n") }
            }
        }
    }

    private suspend fun buildUserMessage(prompt: AgentPrompt, context: AgentContext): String {
        return buildString {
            // The prompt template
            append(prompt.promptTemplate)

            // Add user's task specification from "Specify before run" dialog
            if (context.userSpecification != null) {
                append("\n\n--- User's Task Specification ---\n")
                append(context.userSpecification)
            }

            // Add highlighted text if user selected specific words/phrases
            if (context.highlightedText != null) {
                append("\n\n--- User's Highlighted Text (FOCUS ON THIS) ---\n")
                append(context.highlightedText)
                if (context.selectionStartOffset != null && context.selectionEndOffset != null) {
                    append("\n(Text offsets within verse: startOffset=${context.selectionStartOffset}, endOffset=${context.selectionEndOffset})")
                }
            }

            // Indicate which part of a non-Bible document the user selected (via §-anchors)
            if (context.selectionStartOrdinal != null) {
                val end = context.selectionEndOrdinal ?: context.selectionStartOrdinal
                append("\n\n--- User's Selection (FOCUS ON THIS) ---\n")
                if (context.selectionStartOrdinal == end) {
                    append("The user selected sentence §${context.selectionStartOrdinal} in the following document. Focus on this part.\n")
                } else {
                    append("The user selected sentences §${context.selectionStartOrdinal} to §$end in the following document. Focus on this part.\n")
                }
            }

            // Add selected content if available (converted from OSIS XML to plain text)
            if (context.selectedContent != null) {
                val plainText = try {
                    val fragment = useSaxBuilder { it.build(StringReader(context.selectedContent)).rootElement }
                    OsisToPlainText.convert(fragment, injectAnchors = !prompt.isTextTransformation)
                } catch (_: Exception) {
                    context.selectedContent
                }
                append("\n\n--- Context ---\n")
                append(plainText)
            } else if (context.selectedText != null) {
                append("\n\n--- Context ---\n")
                append(context.selectedText)
            }

            // Auto-include installed documents if enabled
            if (prompt.autoIncludeDocuments) {
                try {
                    val result = GetInstalledDocumentsTool.execute(JSONObject(), context)
                    append("\n\n--- Installed Documents (auto-included) ---\n")
                    append(result.toJson())
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to auto-include installed documents", e)
                }
            }

            // Auto-include commentaries if enabled and verse context is available
            if (prompt.autoIncludeCommentaries && context.selectedVerseRange != null) {
                val result = try {
                    val args = JSONObject().apply {
                        put("verseRef", context.selectedVerseRange.osisRef)
                    }
                    GetCommentariesTool.execute(args, context)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to auto-include commentaries", e)
                    null
                }
                // If user cancelled the commentary selection dialog during auto-include,
                // abort the entire AI run cleanly (no LLM call). Same path as the Stop button.
                if (result is ToolResult.Error && result.code == "USER_CANCELLED") {
                    throw CancellationException("User cancelled commentary selection during prompt building")
                }
                if (result != null) {
                    append("\n\n--- Commentary Entries (auto-included, same format as getCommentaries tool) ---\n\n")
                    append(result.toJson())
                }
            }

            // Add note editor content if editing a note
            if (context.noteEditorContent != null) {
                append("\n\n--- Current Note Content ---\n")
                append(context.noteEditorContent)
            }

            // For regeneration: include previous response and additional instructions
            if (context.previousResponse != null) {
                append("\n\n--- Previous Response (for reference — improve upon this) ---\n")
                append(context.previousResponse.take(10000))
            }
            if (context.additionalInstructions != null) {
                append("\n\n--- Additional Instructions ---\n")
                append(context.additionalInstructions)
            }
        }
    }

    private data class ToolExecutionResult(
        val result: ToolResult,
        val grantSessionPermission: Boolean = false,
        val grantAllToolsPermission: Boolean = false
    )

    private suspend fun executeTool(toolCall: ToolCall, context: AgentContext): ToolExecutionResult {
        val tool = ToolRegistry.get(toolCall.tool)
        if (tool == null) {
            Log.w(TAG, "Tool not found: ${toolCall.tool.camelCaseName}")
            return ToolExecutionResult(ToolResult.error(
                message = "Tool not found: ${toolCall.tool.camelCaseName}",
                code = "TOOL_NOT_FOUND"
            ))
        }

        // Parse arguments early so we can show specific details in the permission dialog
        val arguments = try {
            Log.d(TAG, "Executing tool: ${toolCall.tool.camelCaseName} with args: ${toolCall.arguments}")
            toolCall.parseArguments()
        } catch (e: Exception) {
            Log.e(TAG, "Tool argument parsing failed: ${toolCall.tool.camelCaseName}", e)
            return ToolExecutionResult(ToolResult.error(
                message = "Invalid arguments: ${e.message}",
                code = "INVALID_ARGS"
            ))
        }

        // Permission check for write tools (dynamic per-invocation check)
        var grantSession = false
        var grantAllTools = false
        if (tool.requiresPermissionForCall(arguments, context)) {
            when (checkWritePermission(tool, arguments, context)) {
                DialogResult.Allowed -> { /* proceed */ }
                DialogResult.AllowedForSession -> { grantSession = true }
                DialogResult.AllowedAllForSession -> { grantAllTools = true }
                DialogResult.Denied -> {
                    Log.d(TAG, "Permission denied for tool: ${toolCall.tool.camelCaseName}")
                    return ToolExecutionResult(ToolResult.error(
                        message = "Permission denied for ${toolCall.tool.camelCaseName}. User did not allow this operation.",
                        code = "PERMISSION_DENIED"
                    ))
                }
            }
        }

        val result = try {
            tool.execute(arguments, context)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Tool execution failed: ${toolCall.tool.camelCaseName}", e)
            ToolResult.error(
                message = "Tool execution failed: ${e.message}",
                code = "EXECUTION_ERROR"
            )
        }

        return ToolExecutionResult(result, grantSession, grantAllTools)
    }

    private sealed class DialogResult {
        object Allowed : DialogResult()
        object AllowedForSession : DialogResult()
        object AllowedAllForSession : DialogResult()
        object Denied : DialogResult()
    }

    /** Delegates to [checkPermission] for pure logic, shows dialog when needed. */
    private suspend fun checkWritePermission(tool: Tool, arguments: JSONObject, context: AgentContext): DialogResult {
        return when (checkPermission(
            tool = tool.agentTool,
            settings = PermissionSettings(
                globalMode = CommonUtils.aiSettings.agentPermissionMode,
                permanentlyAllowedTools = CommonUtils.aiSettings.permanentlyAllowedTools,
                permanentlyDeniedTools = CommonUtils.aiSettings.permanentlyDeniedTools,
            ),
            promptAllowedTools = context.promptAllowedTools,
            promptDeniedTools = context.promptDeniedTools,
            promptPermissionMode = context.promptPermissionMode,
            grantedWritePermission = context.grantedWritePermission,
            grantedAllToolsPermission = context.grantedAllToolsPermission,
        )) {
            PermissionCheckResult.Allowed -> DialogResult.Allowed
            PermissionCheckResult.Denied -> DialogResult.Denied
            PermissionCheckResult.NeedsDialog -> showPermissionDialog(tool, arguments, context.workspaceId)
        }
    }

    /**
     * Computes the set of tools to exclude from LLM tool definitions.
     * Text transformation prompts get no tools (only structural tools remain).
     * Otherwise combines globally permanently denied tools and per-prompt denied tools,
     * then removes any tools that the per-prompt allows (override).
     * Structural tools are never excluded (handled by [ToolRegistry.getToolDefinitions]).
     */
    private fun computeExcludedTools(prompt: AgentPrompt, context: AgentContext): Set<AgentTool> {
        if (prompt.isTextTransformation) {
            return AgentTool.entries.toSet() - ToolRegistry.STRUCTURAL_TOOLS
        }
        return computeExcludedTools(
            permanentlyDeniedTools = CommonUtils.aiSettings.permanentlyDeniedTools,
            promptDeniedTools = context.promptDeniedTools,
            promptAvailableTools = context.promptAvailableTools,
        )
    }

    /**
     * Waits for the current activity to become available.
     * Posts waiting/not-waiting events so the UI can show a notification.
     */
    private suspend fun awaitActivity(workspaceId: IdType? = null, toolName: String? = null): Activity {
        CurrentActivityHolder.currentActivity?.let { return it }
        Log.d(TAG, "No current activity, waiting for activity to resume...")
        if (workspaceId != null) {
            ABEventBus.post(AgentPermissionWaitingEvent(workspaceId, waiting = true, toolName = toolName))
        }
        var activity: Activity?
        do {
            delay(500)
            activity = CurrentActivityHolder.currentActivity
        } while (activity == null)
        if (workspaceId != null) {
            ABEventBus.post(AgentPermissionWaitingEvent(workspaceId, waiting = false))
        }
        Log.d(TAG, "Activity resumed")
        return activity
    }

    /** "Always allow" persists tool to permanentlyAllowedTools after confirmation dialog. */
    private suspend fun showPermissionDialog(tool: Tool, arguments: JSONObject, workspaceId: IdType? = null): DialogResult {
        val toolDisplayName = ToolRegistry.getDisplayName(tool)
        val activity = awaitActivity(workspaceId, toolDisplayName)
        val actionDescription = try {
            tool.formatActionDescription(arguments)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to format action description for ${tool.agentTool.camelCaseName}", e)
            null
        }
        return when (Dialogs.agentPermissionDialog(activity, toolDisplayName, tool.description, actionDescription)) {
            Dialogs.AgentPermissionResult.ALLOW -> DialogResult.Allowed
            Dialogs.AgentPermissionResult.ALLOW_FOR_SESSION -> DialogResult.AllowedForSession
            Dialogs.AgentPermissionResult.ALLOW_ALL_SESSION -> DialogResult.AllowedAllForSession
            Dialogs.AgentPermissionResult.ALLOW_ALWAYS -> {
                // Show confirmation dialog
                val confirmed = Dialogs.simpleQuestion(
                    activity,
                    message = activity.getString(R.string.permission_always_allow_confirm, toolDisplayName) +
                        "\n\n" + activity.getString(R.string.permission_always_allow_confirm_reset_hint),
                    title = activity.getString(R.string.permission_always_allow_confirm_title)
                )
                if (confirmed) {
                    val aiSettings = CommonUtils.aiSettings
                    aiSettings.permanentlyAllowedTools += tool.agentTool
                    // Also remove from denied set if present
                    aiSettings.permanentlyDeniedTools -= tool.agentTool
                }
                // Allow this operation regardless of confirmation
                DialogResult.Allowed
            }
            Dialogs.AgentPermissionResult.DENY -> DialogResult.Denied
        }
    }

    /**
     * Shows a dialog asking the user whether to continue execution after reaching the iteration limit.
     * Follows the same activity-lookup pattern as [showPermissionDialog].
     */
    private suspend fun showContinueDialog(currentIteration: Int, increment: Int, workspaceId: IdType? = null): Boolean {
        val activity = awaitActivity(workspaceId)
        return Dialogs.simpleQuestion(
            activity,
            message = application.getString(R.string.llm_continue_iterations_message, currentIteration, increment),
            title = application.getString(R.string.llm_continue_iterations_title)
        )
    }

    /**
     * Routes transformed text back to the appropriate note entity based on [AgentContext.noteEditorEntityType].
     */
    private fun saveNoteContent(context: AgentContext, content: String) {
        val entityId = context.noteEditorEntityId ?: return
        val bookmarkControl = application.applicationComponent.bookmarkControl()

        when (context.noteEditorEntityType) {
            NoteEditorEntityType.BOOKMARK_NOTE -> {
                val bookmark = bookmarkControl.bibleBookmarkById(IdType(entityId)) ?: return
                bookmark.notes = content
                bookmark.notesContentType = TextContentType.MARKDOWN
                bookmarkControl.addOrUpdateBibleBookmark(bookmark, updateNotes = true)
            }
            NoteEditorEntityType.STUDYPAD_TEXT -> {
                bookmarkControl.updateStudyPadTextEntryText(IdType(entityId), content)
            }
            NoteEditorEntityType.MY_DOCUMENT_PAGE -> {
                val dao = DatabaseContainer.instance.myDocumentDb.myDocumentDao()
                val pageId = IdType(entityId)
                val page = dao.pageById(pageId) ?: return
                page.updatedAt = System.currentTimeMillis()
                dao.update(page)
                dao.insertOrUpdateContent(MyDocumentPageContent(pageId = pageId, content = content))

                val document = dao.documentById(page.documentId)
                if (document != null) {
                    MyDocumentBookManager.refreshDocument(document.initials)
                    val cacheEntry = dao.getCacheEntry(pageId)
                    val start = cacheEntry?.kjvOrdinalStart
                    val end = cacheEntry?.kjvOrdinalEnd
                    if (start != null && end != null) {
                        val markers = dao.aiDocMarkersForRange(start, end)
                        ABEventBus.post(AiDocPagesChangedEvent(markers))
                    }
                }
            }
            null -> {}
        }
    }
}

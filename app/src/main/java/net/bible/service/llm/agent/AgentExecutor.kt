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
import net.bible.android.database.IdType
import net.bible.android.view.activity.base.CurrentActivityHolder
import net.bible.android.view.activity.base.Dialogs
import net.bible.service.common.CommonUtils
import net.bible.service.common.useSaxBuilder
import net.bible.service.llm.tools.OsisToPlainText
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
import net.bible.service.llm.tools.write.SetDocumentTitleTool
import net.bible.service.llm.tools.write.FinishWithStudyPadTool
import net.bible.service.llm.tools.write.FinishWithoutDocumentTool
import net.bible.service.llm.tools.ToolDefinition
import org.json.JSONObject
import java.io.StringReader
import java.util.Locale

private const val TAG = "AgentExecutor"
private const val DEFAULT_MAX_ITERATIONS = 10

/**
 * Computes the set of tools to exclude from LLM tool definitions.
 *
 * Priority: globally denied + per-prompt denied, minus per-prompt allowed (override).
 * This allows per-prompt settings to re-enable globally disabled tools.
 */
fun computeExcludedTools(
    permanentlyDeniedTools: Set<AgentTool>,
    promptDeniedTools: Set<AgentTool>?,
    promptAllowedTools: Set<AgentTool>?,
): Set<AgentTool> {
    val excluded = mutableSetOf<AgentTool>()
    excluded.addAll(permanentlyDeniedTools)
    promptDeniedTools?.let { excluded.addAll(it) }
    promptAllowedTools?.let { excluded.removeAll(it) }
    return excluded
}

private sealed class ProcessToolsResult {
    data class Continue(
        val context: AgentContext
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
    fun execute(prompt: AgentPrompt, context: AgentContext, rawLlmLog: RawLlmLog? = null): Flow<AgentEvent> = flow {
        emit(AgentEvent.Started)

        try {
            val llmConfig = LlmModelConfig.fromPrompt(prompt)
            val adapter = LlmProcessingService.resolveAdapter(llmConfig)
            val messages = buildInitialMessages(prompt, context)
            val excludedTools = computeExcludedTools(context)
            val toolDefs = ToolRegistry.getToolDefinitions(excludedTools = excludedTools)

            // Capture tool definitions in raw log
            rawLlmLog?.addToolDefinitions(toolDefs)

            // Capture initial messages in raw log
            for (msg in messages) {
                rawLlmLog?.addMessage(msg.role.name, msg.content)
            }

            runAgentLoop(messages, toolDefs, adapter, context, llmConfig, rawLlmLog)

        } catch (e: CancellationException) {
            emit(AgentEvent.Cancelled)
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Agent execution failed", e)
            emit(AgentEvent.Error(e.message ?: application.getString(R.string.llm_error_unknown), e))
        }
    }

    private suspend fun FlowCollector<AgentEvent>.runAgentLoop(
        messages: MutableList<ChatMessage>,
        tools: List<ToolDefinition>,
        adapter: LlmApiAdapter,
        context: AgentContext,
        llmConfig: LlmModelConfig? = null,
        rawLlmLog: RawLlmLog? = null
    ) {
        var iteration = 0
        var currentContext = context  // Mutable context for session permission tracking
        var totalUsage = LlmUsage()
        val resolved = LlmProcessingService.resolveFromConfig(llmConfig)
        val loopHeaders = LlmProcessingService.buildProviderExtraHeaders(resolved.providerConfig)

        while (iteration < maxIterations) {
            iteration++
            emit(AgentEvent.Iteration(iteration))
            currentCoroutineContext().ensureActive()

            val (parsed, callUsage) = callLlmAndParse(adapter, messages, tools, iteration, llmConfig, loopHeaders, rawLlmLog)
            totalUsage += callUsage

            // Emit per-operation usage
            if (callUsage.totalTokens > 0) {
                emit(AgentEvent.ApiCallCompleted(callUsage, resolved.model))
            }

            when (parsed) {
                is ParsedResponse.ToolCalls -> {
                    when (val result = processToolCalls(adapter, parsed, messages, currentContext, rawLlmLog)) {
                        is ProcessToolsResult.Continue -> {
                            currentContext = result.context
                        }
                        is ProcessToolsResult.FinishWithDocument -> {
                            Log.d(TAG, "Agent finished with document: ${result.title}")
                            emit(AgentEvent.CompletedWithDocument(
                                title = result.title,
                                content = result.content,
                                totalIterations = iteration,
                                usage = totalUsage,
                                model = resolved.model
                            ))
                            return
                        }
                        is ProcessToolsResult.FinishWithoutDocument -> {
                            Log.d(TAG, "Agent finished without document: ${result.message}")
                            emit(AgentEvent.CompletedWithoutDocument(
                                message = result.message,
                                totalIterations = iteration,
                                usage = totalUsage,
                                model = resolved.model
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
                                model = resolved.model
                            ))
                            return
                        }
                    }
                }
                is ParsedResponse.TextResponse -> {
                    Log.d(TAG, "LLM returned final text response without tool call")
                    val normalizedContent = normalizeLlmText(parsed.content)
                    emit(AgentEvent.TextResponse(
                        text = normalizedContent,
                        isFinal = true
                    ))
                    emit(AgentEvent.Completed(
                        response = normalizedContent,
                        totalIterations = iteration,
                        usage = totalUsage,
                        model = resolved.model
                    ))
                    return
                }
                is ParsedResponse.ParseError -> {
                    emit(AgentEvent.Error(application.getString(R.string.llm_parse_error, parsed.error)))
                    return
                }
            }
        }

        emit(AgentEvent.Error(application.getString(R.string.llm_error_max_iterations, maxIterations)))
    }

    private suspend fun callLlmAndParse(
        adapter: LlmApiAdapter,
        messages: List<ChatMessage>,
        tools: List<ToolDefinition>,
        iteration: Int,
        llmConfig: LlmModelConfig? = null,
        extraHeaders: Map<String, String> = emptyMap(),
        rawLlmLog: RawLlmLog? = null
    ): Pair<ParsedResponse, LlmUsage> {
        Log.d(TAG, "Iteration $iteration: calling LLM API")
        val apiResponse = LlmProcessingService.callLlmApiWithTools(messages, tools, llmConfig, extraHeaders)
        rawLlmLog?.addRawApiResponse(iteration, apiResponse.responseBody)
        val parsed = adapter.parseResponse(apiResponse.responseBody)
        return Pair(parsed, apiResponse.usage)
    }

    /**
     * All tool results are collected first and then added via [LlmApiAdapter.createToolResultMessages]
     * because Anthropic batches all tool results into a single user message.
     */
    private suspend fun FlowCollector<AgentEvent>.processToolCalls(
        adapter: LlmApiAdapter,
        parsed: ParsedResponse.ToolCalls,
        messages: MutableList<ChatMessage>,
        context: AgentContext,
        rawLlmLog: RawLlmLog? = null
    ): ProcessToolsResult {
        Log.d(TAG, "LLM requested ${parsed.toolCalls.size} tool calls")
        var currentContext = context

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
                ((currentContext.promptPermissionMode ?: CommonUtils.settings.agentPermissionMode) != PermissionMode.ALWAYS_ASK &&
                 result is ToolResult.Success && ToolRegistry.get(toolCall.tool)?.requiresPermission == true)) {
                currentContext = currentContext.withWritePermissionGranted()
            }

            emit(AgentEvent.ToolCompleted(toolCall.id, toolCall.tool, result))

            toolResults.add(ToolResultBlock(toolCall.id, result.toJson()))

            // Check for finish tools — record the result but continue collecting tool results
            if (finishResult == null && result is ToolResult.Success) {
                when (toolCall.tool) {
                    AgentTool.SET_DOCUMENT_TITLE -> {
                        val data = result.data as? SetDocumentTitleTool.Result
                        val title = data?.title ?: application.getString(R.string.llm_default_document_title)
                        val content = parsed.content?.takeIf { it.isNotBlank() }

                        if (content == null) {
                            Log.w(TAG, "setDocumentTitle called but no text content provided alongside the tool call")
                            toolResults[toolResults.lastIndex] = ToolResultBlock(
                                toolCallId = toolCall.id, content = ToolResult.error(
                                    "Content is required. Output your markdown content as text alongside the setDocumentTitle tool call.",
                                    "MISSING_CONTENT"
                                ).toJson()
                            )
                        } else {
                            Log.d(TAG, "Agent finished with document: $title (content from text response, ${content.length} chars)")
                            finishResult = ProcessToolsResult.FinishWithDocument(
                                title = title,
                                content = normalizeLlmText(content),
                                context = currentContext
                            )
                        }
                    }
                    AgentTool.FINISH_WITHOUT_DOCUMENT -> {
                        val data = result.data as? FinishWithoutDocumentTool.Result
                        val message = data?.message ?: application.getString(R.string.llm_default_task_completed)
                        finishResult = ProcessToolsResult.FinishWithoutDocument(
                            message = message,
                            context = currentContext
                        )
                    }
                    AgentTool.FINISH_WITH_STUDY_PAD -> {
                        val data = result.data as? FinishWithStudyPadTool.Result
                        val labelId = IdType(data?.labelId ?: "")
                        val scrollToEntryId = data?.scrollToEntryId?.takeIf { it.isNotBlank() }?.let { IdType(it) }
                        val message = data?.message ?: application.getString(R.string.llm_default_studypad_opened)
                        finishResult = ProcessToolsResult.FinishWithStudyPad(
                            labelId = labelId,
                            scrollToEntryId = scrollToEntryId,
                            message = message,
                            context = currentContext
                        )
                    }
                    else -> { /* Not a finish tool — no special handling */ }
                }
            }
        }

        // Add all tool results to messages
        messages.addAll(adapter.createToolResultMessages(toolResults))

        return finishResult ?: ProcessToolsResult.Continue(currentContext)
    }

    private fun buildInitialMessages(prompt: AgentPrompt, context: AgentContext): MutableList<ChatMessage> {
        val systemPrompt = buildSystemPrompt(prompt, context)
        val userMessage = buildUserMessage(prompt, context)
        return mutableListOf(
            ChatMessage(ChatMessage.Role.SYSTEM, content = systemPrompt),
            ChatMessage(ChatMessage.Role.USER, content = userMessage)
        )
    }

    private fun buildSystemPrompt(prompt: AgentPrompt, context: AgentContext): String {
        val appLanguage = Locale.getDefault().displayLanguage

        return buildString {
            val template = application.resources.openRawResource(R.raw.llm_agent_system_prompt)
                .bufferedReader()
                .use { it.readText() }
            append(template.replace("{{APP_LANGUAGE}}", appLanguage))

            if (context.activeDocumentInitials != null) {
                append("Current active document: ${context.activeDocumentInitials}\n")
            }
            if (context.verseRefString != null) {
                append("Selected verse reference: ${context.verseRefString}\n")
            }
            if (context.activeLabelId != null) {
                append("Active label/StudyPad ID: ${context.activeLabelId}\n")
            }
        }
    }

    private fun buildUserMessage(prompt: AgentPrompt, context: AgentContext): String {
        return buildString {
            // The prompt template
            append(prompt.promptTemplate)

            // Add highlighted text if user selected specific words/phrases
            if (context.highlightedText != null) {
                append("\n\n--- User's Highlighted Text (FOCUS ON THIS) ---\n")
                append(context.highlightedText)
            }

            // Add selected content if available (converted from OSIS XML to plain text)
            if (context.selectedContent != null) {
                val plainText = try {
                    val fragment = useSaxBuilder { it.build(StringReader(context.selectedContent)).rootElement }
                    OsisToPlainText.convert(fragment)
                } catch (_: Exception) {
                    context.selectedContent
                }
                append("\n\n--- Context ---\n")
                append(plainText)
            } else if (context.selectedText != null) {
                append("\n\n--- Context ---\n")
                append(context.selectedText)
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

        // Permission check for write tools
        var grantSession = false
        var grantAllTools = false
        if (tool.requiresPermission) {
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
                globalMode = CommonUtils.settings.agentPermissionMode,
                permanentlyAllowedTools = CommonUtils.settings.permanentlyAllowedTools,
                permanentlyDeniedTools = CommonUtils.settings.permanentlyDeniedTools,
            ),
            promptAllowedTools = context.promptAllowedTools,
            promptDeniedTools = context.promptDeniedTools,
            promptPermissionMode = context.promptPermissionMode,
            grantedWritePermission = context.grantedWritePermission,
            grantedAllToolsPermission = context.grantedAllToolsPermission,
        )) {
            PermissionCheckResult.Allowed -> DialogResult.Allowed
            PermissionCheckResult.Denied -> DialogResult.Denied
            PermissionCheckResult.NeedsDialog -> showPermissionDialog(tool, arguments)
        }
    }

    /**
     * Computes the set of tools to exclude from LLM tool definitions.
     * Combines globally permanently denied tools and per-prompt denied tools,
     * then removes any tools that the per-prompt allows (override).
     * Structural tools are never excluded (handled by [ToolRegistry.getToolDefinitions]).
     */
    private fun computeExcludedTools(context: AgentContext): Set<AgentTool> =
        computeExcludedTools(
            permanentlyDeniedTools = CommonUtils.settings.permanentlyDeniedTools,
            promptDeniedTools = context.promptDeniedTools,
            promptAllowedTools = context.promptAllowedTools,
        )

    /** "Always allow" persists tool to permanentlyAllowedTools after confirmation dialog. */
    private suspend fun showPermissionDialog(tool: Tool, arguments: JSONObject): DialogResult {
        var activity = CurrentActivityHolder.currentActivity
        if (activity == null) {
            Log.d(TAG, "No current activity, waiting for activity to resume...")
            while (activity == null) {
                delay(500)
                activity = CurrentActivityHolder.currentActivity
            }
            Log.d(TAG, "Activity resumed, showing permission dialog")
        }
        val toolDisplayName = ToolRegistry.getDisplayName(tool)
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
                    val settings = CommonUtils.settings
                    settings.permanentlyAllowedTools += tool.agentTool
                    // Also remove from denied set if present
                    settings.permanentlyDeniedTools -= tool.agentTool
                }
                // Allow this operation regardless of confirmation
                DialogResult.Allowed
            }
            Dialogs.AgentPermissionResult.DENY -> DialogResult.Denied
        }
    }
}

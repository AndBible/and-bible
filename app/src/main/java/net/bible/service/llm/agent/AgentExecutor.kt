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
import net.bible.android.activity.R
import net.bible.service.llm.AgentTool
import net.bible.android.database.IdType
import net.bible.android.view.activity.base.CurrentActivityHolder
import net.bible.android.view.activity.base.Dialogs
import net.bible.service.common.CommonUtils
import net.bible.service.llm.AgentPrompt
import net.bible.service.llm.LlmApiAdapter
import net.bible.service.llm.LlmModelConfig
import net.bible.service.llm.LlmUsage
import net.bible.service.llm.PromptRepository
import net.bible.service.llm.LlmProcessingService
import net.bible.service.llm.tools.Tool
import net.bible.service.llm.tools.ToolRegistry
import net.bible.service.llm.tools.ToolResult
import net.bible.service.llm.tools.normalizeLlmText
import net.bible.service.llm.tools.write.SetDocumentTitleTool
import net.bible.service.llm.tools.write.FinishWithStudyPadTool
import net.bible.service.llm.tools.write.FinishWithoutDocumentTool
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

private const val TAG = "AgentExecutor"
private const val DEFAULT_MAX_ITERATIONS = 10

/**
 * Result of processing tool calls.
 */
private sealed class ProcessToolsResult {
    /** Continue iterating with updated context */
    data class Continue(val context: AgentContext) : ProcessToolsResult()
    /** Finish with a document */
    data class FinishWithDocument(val title: String, val content: String, val context: AgentContext) : ProcessToolsResult()
    /** Finish without creating a document */
    data class FinishWithoutDocument(val message: String, val context: AgentContext) : ProcessToolsResult()
    /** Finish by opening a StudyPad */
    data class FinishWithStudyPad(val labelId: IdType, val scrollToEntryId: IdType?, val message: String, val context: AgentContext) : ProcessToolsResult()
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
    /**
     * Execute an agent prompt with the given context.
     *
     * @param promptId ID of the AgentPrompt to execute
     * @param context Execution context with selection info, etc.
     * @return Flow of AgentEvents showing progress and final result
     */
    fun execute(promptId: IdType, context: AgentContext): Flow<AgentEvent> = flow {
        emit(AgentEvent.Started)

        try {
            val prompt = PromptRepository.promptById(promptId)
            if (prompt == null) {
                emit(AgentEvent.Error("Prompt not found: $promptId"))
                return@flow
            }

            val llmConfig = LlmModelConfig.fromPrompt(prompt)
            val adapter = LlmProcessingService.resolveAdapter(llmConfig)
            val messages = buildInitialMessages(prompt, context)
            val tools = adapter.buildToolsArray(ToolRegistry.getToolDefinitions(includeWriteTools = true))

            runAgentLoop(messages, tools, adapter, context, llmConfig)

        } catch (e: CancellationException) {
            emit(AgentEvent.Cancelled)
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Agent execution failed", e)
            emit(AgentEvent.Error(e.message ?: "Unknown error", e))
        }
    }

    /**
     * Run the main agent loop until completion or max iterations.
     */
    private suspend fun FlowCollector<AgentEvent>.runAgentLoop(
        messages: JSONArray,
        tools: JSONArray,
        adapter: LlmApiAdapter,
        context: AgentContext,
        llmConfig: LlmModelConfig? = null
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

            val (parsed, callUsage) = callLlmAndParse(adapter, messages, tools, iteration, llmConfig, loopHeaders)
            totalUsage += callUsage

            // Emit per-operation usage
            if (callUsage.totalTokens > 0) {
                emit(AgentEvent.ApiCallCompleted(callUsage, resolved.model))
            }

            when (parsed) {
                is ParsedResponse.ToolCalls -> {
                    when (val result = processToolCalls(adapter, parsed, messages, currentContext)) {
                        is ProcessToolsResult.Continue -> {
                            currentContext = result.context
                        }
                        is ProcessToolsResult.FinishWithDocument -> {
                            Log.d(TAG, "Agent finished with document: ${result.title}")
                            emit(AgentEvent.CompletedWithDocument(result.title, result.content, iteration, totalUsage, resolved.model))
                            return
                        }
                        is ProcessToolsResult.FinishWithoutDocument -> {
                            Log.d(TAG, "Agent finished without document: ${result.message}")
                            emit(AgentEvent.CompletedWithoutDocument(result.message, iteration, totalUsage, resolved.model))
                            return
                        }
                        is ProcessToolsResult.FinishWithStudyPad -> {
                            Log.d(TAG, "Agent finished with StudyPad: ${result.labelId}")
                            emit(AgentEvent.CompletedWithStudyPad(result.labelId, result.scrollToEntryId, result.message, iteration, totalUsage, resolved.model))
                            return
                        }
                    }
                }
                is ParsedResponse.TextResponse -> {
                    Log.d(TAG, "LLM returned final text response without tool call")
                    val normalizedContent = normalizeLlmText(parsed.content)
                    emit(AgentEvent.TextResponse(normalizedContent, isFinal = true))
                    emit(AgentEvent.Completed(normalizedContent, iteration, totalUsage, resolved.model))
                    return
                }
                is ParsedResponse.ParseError -> {
                    emit(AgentEvent.Error("Failed to parse LLM response: ${parsed.error}"))
                    return
                }
            }
        }

        emit(AgentEvent.Error("Maximum iterations ($maxIterations) reached without completion"))
    }

    /**
     * Call LLM API and parse the response.
     *
     * @return Pair of parsed response and token usage from this call
     */
    private suspend fun callLlmAndParse(
        adapter: LlmApiAdapter,
        messages: JSONArray,
        tools: JSONArray,
        iteration: Int,
        llmConfig: LlmModelConfig? = null,
        extraHeaders: Map<String, String> = emptyMap()
    ): Pair<ParsedResponse, LlmUsage> {
        Log.d(TAG, "Iteration $iteration: calling LLM API")
        val apiResponse = LlmProcessingService.callLlmApiWithTools(messages, tools, llmConfig, extraHeaders)
        val parsed = adapter.parseResponse(apiResponse.json)
        return Pair(parsed, apiResponse.usage)
    }

    /**
     * Process tool calls: execute each tool and add results to messages.
     * Returns ProcessToolsResult indicating whether to continue or finish without document.
     *
     * All tool results are collected first and then added to messages via
     * [LlmApiAdapter.createToolResultMessages] — this is required because Anthropic
     * batches all tool results into a single user message.
     */
    private suspend fun FlowCollector<AgentEvent>.processToolCalls(
        adapter: LlmApiAdapter,
        parsed: ParsedResponse.ToolCalls,
        messages: JSONArray,
        context: AgentContext
    ): ProcessToolsResult {
        Log.d(TAG, "LLM requested ${parsed.toolCalls.size} tool calls")
        var currentContext = context

        parsed.content?.takeIf { it.isNotBlank() }?.let {
            emit(AgentEvent.TextResponse(it, isFinal = false))
        }

        messages.put(adapter.createAssistantToolCallMessage(parsed.toolCalls, parsed.content))

        // Execute all tools and collect results
        val toolResults = mutableListOf<Pair<String, String>>()
        var finishResult: ProcessToolsResult? = null

        for (toolCall in parsed.toolCalls) {
            currentCoroutineContext().ensureActive()

            emit(AgentEvent.ToolCalling(toolCall.id, toolCall.name, toolCall.arguments))

            val execResult = executeTool(toolCall, currentContext)
            val result = execResult.result

            // Update session permissions based on user's dialog choice
            if (execResult.grantAllToolsPermission) {
                currentContext = currentContext.withAllToolsPermissionGranted()
            } else if (execResult.grantSessionPermission ||
                ((currentContext.promptPermissionMode ?: CommonUtils.settings.agentPermissionMode) != PermissionMode.ALWAYS_ASK &&
                 result is ToolResult.Success && ToolRegistry.get(toolCall.name)?.requiresPermission == true)) {
                currentContext = currentContext.withWritePermissionGranted()
            }

            emit(AgentEvent.ToolCompleted(toolCall.id, toolCall.name, result))

            toolResults.add(toolCall.id to result.toJson())

            // Check for finish tools — record the result but continue collecting tool results
            if (finishResult == null) {
                if (toolCall.name == SetDocumentTitleTool.name && result is ToolResult.Success) {
                    val data = result.data as? JSONObject
                    val title = data?.optString("title") ?: "AI Response"
                    val content = parsed.content?.takeIf { it.isNotBlank() }

                    if (content == null) {
                        Log.w(TAG, "setDocumentTitle called but no text content provided alongside the tool call")
                        // Replace last result with error
                        toolResults[toolResults.lastIndex] = toolCall.id to ToolResult.error(
                            "Content is required. Output your markdown content as text alongside the setDocumentTitle tool call.",
                            "MISSING_CONTENT"
                        ).toJson()
                        // Don't set finishResult — let the loop continue
                    } else {
                        Log.d(TAG, "Agent finished with document: $title (content from text response, ${content.length} chars)")
                        finishResult = ProcessToolsResult.FinishWithDocument(title, normalizeLlmText(content), currentContext)
                    }
                } else if (toolCall.name == FinishWithoutDocumentTool.name && result is ToolResult.Success) {
                    val data = result.data as? JSONObject
                    val message = data?.optString("message") ?: "Task completed"
                    finishResult = ProcessToolsResult.FinishWithoutDocument(message, currentContext)
                } else if (toolCall.name == FinishWithStudyPadTool.name && result is ToolResult.Success) {
                    val data = result.data as? JSONObject
                    val labelId = IdType(data?.optString("labelId") ?: "")
                    val scrollToEntryId = data?.optString("scrollToEntryId")?.takeIf { it.isNotBlank() }?.let { IdType(it) }
                    val message = data?.optString("message") ?: "StudyPad opened"
                    finishResult = ProcessToolsResult.FinishWithStudyPad(labelId, scrollToEntryId, message, currentContext)
                }
            }
        }

        // Add all tool results to messages
        for (msg in adapter.createToolResultMessages(toolResults)) {
            messages.put(msg)
        }

        return finishResult ?: ProcessToolsResult.Continue(currentContext)
    }

    /**
     * Build the initial messages for the LLM conversation.
     */
    private fun buildInitialMessages(prompt: AgentPrompt, context: AgentContext): JSONArray {
        val messages = JSONArray()

        // System message with context and available tools info
        val systemPrompt = buildSystemPrompt(prompt, context)
        messages.put(JSONObject().apply {
            put("role", "system")
            put("content", systemPrompt)
        })

        // User message with the actual request
        val userMessage = buildUserMessage(prompt, context)
        messages.put(JSONObject().apply {
            put("role", "user")
            put("content", userMessage)
        })

        return messages
    }

    /**
     * Build the system prompt with context information.
     */
    private fun buildSystemPrompt(prompt: AgentPrompt, context: AgentContext): String {
        val appLanguage = Locale.getDefault().displayLanguage

        return buildString {
            append("You are a Bible study assistant integrated with the AndBible application. ")
            append("You have access to tools that can read Bible content, search, and manage bookmarks and notes.\n\n")

            append("IMPORTANT: Always respond in $appLanguage (the user's app language).\n\n")

            append("Guidelines:\n")
            append("- Use tools to gather information when needed\n")
            append("- Be concise and helpful in your responses\n")
            append("- If you need to read verse content, use the appropriate tool\n")
            append("\n")

            append("IMPORTANT - Finishing your response:\n")
            append("When you are done and want to provide a written response:\n")
            append("1. Output your complete markdown content as text (NOT as a tool argument)\n")
            append("2. Call setDocumentTitle tool with a short, plain text title\n")
            append("\n")
            append("You MUST call setDocumentTitle to give your document a proper title.\n")
            append("\n")
            append("Example - output both text and tool call in the same response:\n")
            append("```\n")
            append("# [Rom. 8:28](sword:///Rom.8.28) - God's Promise\n\n")
            append("This verse teaches about God's sovereignty and providence...\n")
            append("[...full analysis in markdown...]\n")
            append("```\n")
            append("Then call: setDocumentTitle(title: \"Romans 8:28 - God's Promise\")\n")
            append("\n")
            append("CRITICAL: The title MUST be plain text — NO markdown, NO links, NO formatting.\n")
            append("Output the markdown content as text in the SAME response where you call setDocumentTitle.\n")
            append("Do NOT put content in the tool argument. Do NOT use XML tags or function_call syntax.\n")
            append("\n")

            append("If your task involves creating or modifying a StudyPad, use finishWithStudyPad instead of setDocumentTitle.\n")
            append("First create/populate the StudyPad using createLabel + addStudyPadEntry tools, then call:\n")
            append("  finishWithStudyPad(labelId: \"...\", message: \"Created study notes on Romans 8\")\n")
            append("Optionally scroll to a specific entry:\n")
            append("  finishWithStudyPad(labelId: \"...\", scrollToEntryId: \"...\", message: \"...\")\n")
            append("\n")

            // Link formatting instructions
            append("CRITICAL - Bible Reference Links:\n")
            append("EVERY Bible reference in your response MUST be a clickable link. NO EXCEPTIONS.\n")
            append("This applies to ALL references: in headings, inline text, lists, parentheses, everywhere.\n")
            append("\n")
            append("Format: [Display Text](sword:///OSIS.Reference) - note three slashes (empty module)\n")
            append("\n")
            append("Examples of CORRECT formatting:\n")
            append("  - \"As [John 3:16](sword:///John.3.16) teaches...\" (inline)\n")
            append("  - \"See also [Rom. 8:28](sword:///Rom.8.28)\" (reference)\n")
            append("  - \"([Matt. 5:3-12](sword:///Matt.5.3-12))\" (parenthetical)\n")
            append("  - \"# [Genesis 1:1](sword:///Gen.1.1) - Creation\" (heading)\n")
            append("\n")
            append("WRONG (never do this):\n")
            append("  - \"John 3:16 teaches...\" (missing link!)\n")
            append("  - \"See Romans 8:28\" (missing link!)\n")
            append("\n")
            append("OSIS book abbreviations: Gen, Exod, Lev, Num, Deut, Josh, Judg, Ruth, 1Sam, 2Sam, 1Kgs, 2Kgs, ")
            append("1Chr, 2Chr, Ezra, Neh, Esth, Job, Ps, Prov, Eccl, Song, Isa, Jer, Lam, Ezek, Dan, Hos, Joel, ")
            append("Amos, Obad, Jonah, Mic, Nah, Hab, Zeph, Hag, Zech, Mal, Matt, Mark, Luke, John, Acts, Rom, ")
            append("1Cor, 2Cor, Gal, Eph, Phil, Col, 1Thess, 2Thess, 1Tim, 2Tim, Titus, Phlm, Heb, Jas, 1Pet, ")
            append("2Pet, 1John, 2John, 3John, Jude, Rev\n")
            append("\n")
            append("Only specify a module (sword://MHC/Matt.5.3) for commentaries or specific documents.\n")
            append("\n")

            // StudyPad link format
            append("StudyPad links:\n")
            append("- [StudyPad Name](journal://?id=LABEL_ID) — links to a StudyPad\n")
            append("- [Entry](journal://?id=LABEL_ID&entryId=ENTRY_ID) — links to a specific entry in a StudyPad\n")
            append("\n")

            // Source attribution instructions
            append("IMPORTANT - Source Attribution:\n")
            append("When summarizing content from commentaries, dictionaries, or other reference works:\n")
            append("\n")
            append("1. ALWAYS cite the source by name when using its content:\n")
            append("   - \"Matthew Henry's Commentary (MHC) explains...\"\n")
            append("   - \"According to MHC, this means...\"\n")
            append("   - \"Strong's Greek Dictionary (StrongsGreek) defines...\"\n")
            append("\n")
            append("2. Include clickable links to specific commentary/dictionary entries:\n")
            append("   - Commentary: [MHC](sword://MHC/Matt.5.3)\n")
            append("   - Dictionary: [Strong's G2316](sword://StrongsGreek/G2316)\n")
            append("\n")
            append("3. When using multiple sources, compare their perspectives and cite each one.\n")
            append("\n")
            append("4. End documents with a 'Sources' section listing all used sources with links.\n")
            append("\n")

            // Add context information
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

    /**
     * Build the user message with the prompt and selection.
     */
    private fun buildUserMessage(prompt: AgentPrompt, context: AgentContext): String {
        return buildString {
            // The prompt template
            append(prompt.promptTemplate)

            // Add highlighted text if user selected specific words/phrases
            if (context.highlightedText != null) {
                append("\n\n--- User's Highlighted Text (FOCUS ON THIS) ---\n")
                append(context.highlightedText)
            }

            // Add selected content if available
            if (context.selectedContent != null) {
                append("\n\n--- Context (OSIS XML) ---\n")
                append(context.selectedContent)
            } else if (context.selectedText != null) {
                append("\n\n--- Context ---\n")
                append(context.selectedText)
            }
        }
    }

    /**
     * Result of tool execution including permission info.
     */
    private data class ToolExecutionResult(
        val result: ToolResult,
        val grantSessionPermission: Boolean = false,
        val grantAllToolsPermission: Boolean = false
    )

    /**
     * Execute a single tool call.
     */
    private suspend fun executeTool(toolCall: ToolCall, context: AgentContext): ToolExecutionResult {
        val tool = ToolRegistry.get(toolCall.name)
        if (tool == null) {
            Log.w(TAG, "Tool not found: ${toolCall.name}")
            return ToolExecutionResult(ToolResult.error("Tool not found: ${toolCall.name}", "TOOL_NOT_FOUND"))
        }

        // Permission check for write tools
        var grantSession = false
        var grantAllTools = false
        if (tool.requiresPermission) {
            when (checkWritePermission(tool, context)) {
                DialogResult.Allowed -> { /* proceed */ }
                DialogResult.AllowedForSession -> { grantSession = true }
                DialogResult.AllowedAllForSession -> { grantAllTools = true }
                DialogResult.Denied -> {
                    Log.d(TAG, "Permission denied for tool: ${toolCall.name}")
                    return ToolExecutionResult(ToolResult.error(
                        "Permission denied for ${toolCall.name}. User did not allow this operation.",
                        "PERMISSION_DENIED"
                    ))
                }
            }
        }

        val result = try {
            Log.d(TAG, "Executing tool: ${toolCall.name} with args: ${toolCall.arguments}")
            val arguments = toolCall.parseArguments()
            tool.execute(arguments, context)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Tool execution failed: ${toolCall.name}", e)
            ToolResult.error("Tool execution failed: ${e.message}", "EXECUTION_ERROR")
        }

        return ToolExecutionResult(result, grantSession, grantAllTools)
    }

    /**
     * Result of dialog-based permission check (extends the pure logic result with session grants).
     */
    private sealed class DialogResult {
        object Allowed : DialogResult()
        object AllowedForSession : DialogResult()
        object AllowedAllForSession : DialogResult()
        object Denied : DialogResult()
    }

    /**
     * Check if write permission should be granted based on current mode and context.
     * Delegates pure decision logic to [PermissionChecker] and handles dialog when needed.
     */
    private suspend fun checkWritePermission(tool: Tool, context: AgentContext): DialogResult {
        val agentTool = AgentTool.fromToolName(tool.name)
            ?: return DialogResult.Denied // Unknown tool
        return when (PermissionChecker.check(
            tool = agentTool,
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
            PermissionCheckResult.NeedsDialog -> showPermissionDialog(tool, agentTool)
        }
    }

    /**
     * Show the permission dialog to the user.
     *
     * If user selects "Always allow", shows a confirmation dialog. On confirm,
     * persists the tool to permanentlyAllowedTools. The operation is allowed
     * regardless of confirmation result.
     */
    private suspend fun showPermissionDialog(tool: Tool, agentTool: AgentTool): DialogResult {
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
        return when (Dialogs.agentPermissionDialog(activity, toolDisplayName, tool.description)) {
            Dialogs.AgentPermissionResult.ALLOW -> DialogResult.Allowed
            Dialogs.AgentPermissionResult.ALLOW_FOR_SESSION -> DialogResult.AllowedForSession
            Dialogs.AgentPermissionResult.ALLOW_ALL_SESSION -> DialogResult.AllowedAllForSession
            Dialogs.AgentPermissionResult.ALLOW_ALWAYS -> {
                // Show confirmation dialog
                val confirmed = Dialogs.simpleQuestion(
                    activity,
                    message = activity.getString(R.string.permission_always_allow_confirm, toolDisplayName),
                    title = activity.getString(R.string.permission_always_allow_confirm_title)
                )
                if (confirmed) {
                    val settings = CommonUtils.settings
                    settings.permanentlyAllowedTools = settings.permanentlyAllowedTools + agentTool
                    // Also remove from denied set if present
                    settings.permanentlyDeniedTools = settings.permanentlyDeniedTools - agentTool
                }
                // Allow this operation regardless of confirmation
                DialogResult.Allowed
            }
            Dialogs.AgentPermissionResult.DENY -> DialogResult.Denied
        }
    }
}

/*
 * Copyright (c) 2024 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import net.bible.android.database.IdType
import net.bible.android.view.activity.base.CurrentActivityHolder
import net.bible.android.view.activity.base.Dialogs
import net.bible.service.common.CommonUtils
import net.bible.service.db.DatabaseContainer
import net.bible.service.llm.AgentPrompt
import net.bible.service.llm.LlmProcessingService
import net.bible.service.llm.tools.Tool
import net.bible.service.llm.tools.ToolRegistry
import net.bible.service.llm.tools.ToolResult
import org.json.JSONArray
import org.json.JSONObject

private const val TAG = "AgentExecutor"
private const val DEFAULT_MAX_ITERATIONS = 10

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
    private val promptDao get() = DatabaseContainer.instance.llmProcessingDb.agentPromptDao()

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
            val prompt = promptDao.promptById(promptId)
            if (prompt == null) {
                emit(AgentEvent.Error("Prompt not found: $promptId"))
                return@flow
            }

            val messages = buildInitialMessages(prompt, context)
            val tools = ToolRegistry.toOpenAiToolsArray(includeWriteTools = true)

            runAgentLoop(messages, tools, context)

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
        context: AgentContext
    ) {
        var iteration = 0
        var currentContext = context  // Mutable context for session permission tracking

        while (iteration < maxIterations) {
            iteration++
            emit(AgentEvent.Iteration(iteration))
            currentCoroutineContext().ensureActive()

            when (val parsed = callLlmAndParse(messages, tools, iteration)) {
                is ParsedResponse.ToolCalls -> {
                    currentContext = processToolCalls(parsed, messages, currentContext)
                }
                is ParsedResponse.TextResponse -> {
                    Log.d(TAG, "LLM returned final response")
                    emit(AgentEvent.TextResponse(parsed.content, isFinal = true))
                    emit(AgentEvent.Completed(parsed.content, iteration))
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
     */
    private suspend fun callLlmAndParse(
        messages: JSONArray,
        tools: JSONArray,
        iteration: Int
    ): ParsedResponse {
        Log.d(TAG, "Iteration $iteration: calling LLM API")
        val response = LlmProcessingService.callLlmApiWithTools(messages, tools)

        val assistantMessage = response
            .getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")

        return ToolCallParser.parseMessage(assistantMessage)
    }

    /**
     * Process tool calls: execute each tool and add results to messages.
     * Returns the updated context (with permission granted if a write tool was allowed).
     */
    private suspend fun FlowCollector<AgentEvent>.processToolCalls(
        parsed: ParsedResponse.ToolCalls,
        messages: JSONArray,
        context: AgentContext
    ): AgentContext {
        Log.d(TAG, "LLM requested ${parsed.toolCalls.size} tool calls")
        var currentContext = context

        parsed.content?.takeIf { it.isNotBlank() }?.let {
            emit(AgentEvent.TextResponse(it, isFinal = false))
        }

        messages.put(ToolCallParser.createAssistantToolCallMessage(parsed.toolCalls, parsed.content))

        for (toolCall in parsed.toolCalls) {
            currentCoroutineContext().ensureActive()

            emit(AgentEvent.ToolCalling(toolCall.id, toolCall.name, toolCall.arguments))

            val result = executeTool(toolCall, currentContext)

            // If write operation succeeded and user allowed, mark permission as granted
            if (result is ToolResult.Success && ToolRegistry.get(toolCall.name)?.requiresPermission == true) {
                currentContext = currentContext.withWritePermissionGranted()
            }

            emit(AgentEvent.ToolCompleted(toolCall.id, toolCall.name, result))

            messages.put(ToolCallParser.createToolResultMessage(toolCall.id, result.toJson()))
        }
        return currentContext
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
        return buildString {
            append("You are a Bible study assistant integrated with the AndBible application. ")
            append("You have access to tools that can read Bible content, search, and manage bookmarks and notes.\n\n")

            append("Guidelines:\n")
            append("- Use tools to gather information when needed\n")
            append("- Be concise and helpful in your responses\n")
            append("- If you need to read verse content, use the appropriate tool\n")
            append("- IMPORTANT: Always start your response with a descriptive title as a markdown H1 heading (# Title)\n")
            append("  The title should be short (max 60 chars) and describe the content, e.g.:\n")
            append("  # The Beatitudes: Path to True Blessing\n")
            append("  # Understanding Grace in Romans 8\n")
            append("\n")

            // Link formatting instructions
            append("IMPORTANT - Cross-reference formatting:\n")
            append("When referencing Bible verses in your response, ALWAYS format them as clickable markdown links using the sword:// protocol:\n")
            append("- Format: [Display Text](sword:///Reference) - note the empty module (three slashes)\n")
            append("- Use OSIS reference format: Book.Chapter.Verse or Book.Chapter.StartVerse-EndVerse\n")
            append("- Examples:\n")
            append("  - [Matt. 5:3](sword:///Matt.5.3)\n")
            append("  - [John 3:16-17](sword:///John.3.16-17)\n")
            append("  - [Genesis 1:1-3](sword:///Gen.1.1-3)\n")
            append("  - [Rom. 8:28](sword:///Rom.8.28)\n")
            append("- Leave module empty so the user's active Bible is used\n")
            append("- Only specify a module (e.g., sword://MHC/Matt.5.3) for commentaries or specific documents\n")
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

            // Add selected content if available
            if (context.selectedContent != null) {
                append("\n\n--- Selected Content (OSIS XML) ---\n")
                append(context.selectedContent)
            } else if (context.selectedText != null) {
                append("\n\n--- Selected Text ---\n")
                append(context.selectedText)
            }
        }
    }

    /**
     * Execute a single tool call.
     */
    private suspend fun executeTool(toolCall: ToolCall, context: AgentContext): ToolResult {
        val tool = ToolRegistry.get(toolCall.name)
        if (tool == null) {
            Log.w(TAG, "Tool not found: ${toolCall.name}")
            return ToolResult.error("Tool not found: ${toolCall.name}", "TOOL_NOT_FOUND")
        }

        // Permission check for write tools
        if (tool.requiresPermission && !checkWritePermission(tool, context)) {
            Log.d(TAG, "Permission denied for tool: ${toolCall.name}")
            return ToolResult.error(
                "Permission denied for ${toolCall.name}. User did not allow this operation.",
                "PERMISSION_DENIED"
            )
        }

        return try {
            Log.d(TAG, "Executing tool: ${toolCall.name} with args: ${toolCall.arguments}")
            val arguments = toolCall.parseArguments()
            tool.execute(arguments, context)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Tool execution failed: ${toolCall.name}", e)
            ToolResult.error("Tool execution failed: ${e.message}", "EXECUTION_ERROR")
        }
    }

    /**
     * Check if write permission should be granted based on current mode and context.
     */
    private suspend fun checkWritePermission(tool: Tool, context: AgentContext): Boolean {
        val mode = CommonUtils.settings.agentPermissionMode

        return when (mode) {
            PermissionMode.ALLOW_ALL -> true
            PermissionMode.DENY_ALL -> false
            PermissionMode.ASK_ONCE_PER_RUN -> {
                if (context.grantedWritePermission) {
                    true
                } else {
                    showPermissionDialog(tool)
                }
            }
            PermissionMode.ALWAYS_ASK -> showPermissionDialog(tool)
        }
    }

    /**
     * Show the permission dialog to the user.
     * Returns true if permission was granted, false otherwise.
     */
    private suspend fun showPermissionDialog(tool: Tool): Boolean {
        val activity = CurrentActivityHolder.currentActivity
        if (activity == null) {
            Log.w(TAG, "No current activity, allowing tool by default")
            return true  // Allow if no activity (e.g., background process)
        }
        return Dialogs.agentPermissionDialog(activity, tool.name, tool.description)
    }
}

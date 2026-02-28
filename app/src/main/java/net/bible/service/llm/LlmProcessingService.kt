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

package net.bible.service.llm

import android.app.AlertDialog
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.bible.android.BibleApplication.Companion.application
import net.bible.android.activity.R
import net.bible.android.control.event.ABEventBus
import net.bible.service.common.CommonUtils
import net.bible.service.db.DatabaseContainer
import net.bible.android.database.IdType
import net.bible.service.llm.agent.AgentContext
import net.bible.service.llm.agent.AgentLogEntry
import net.bible.service.llm.agent.AgentSessionManager
import net.bible.service.llm.agent.EntryStatus
import net.bible.service.llm.agent.LogEntryType
import net.bible.service.llm.agent.ParsedResponse
import net.bible.service.llm.agent.ToolCallParser
import net.bible.service.llm.processors.PromptProcessor
import net.bible.service.llm.processors.TranslationProcessor
import net.bible.service.llm.tools.ToolRegistry
import net.bible.service.llm.tools.ToolResult
import net.bible.service.llm.tools.formatJsonForLog
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val TAG = "LlmProcessingService"
private const val CONNECT_TIMEOUT_SECONDS = 120L
private const val READ_TIMEOUT_SECONDS = 90L  // 90s per API call; generous for reasoning models
private const val WRITE_TIMEOUT_SECONDS = 120L
private const val LLM_TEMPERATURE = 0.3

/** Event posted when LLM operations start or complete */
class LlmEvent(val running: Boolean)

/** Exception thrown when LLM processing fails */
class LlmProcessingError(message: String) : Exception(message)

/** Silent exception - request was superseded, no error display needed */
class LlmRequestSuperseded : Exception("Request superseded")

/** Tracks state of a pending request for a specific document:key */
private data class RequestState(
    val deferred: CompletableDeferred<String>,
    val job: Job,
    var dialog: AlertDialog? = null  // Reference to confirmation dialog if shown
)


/**
 * Generic service for processing document content through LLM.
 *
 * Supports multiple processor types (translation, summarization, etc.)
 * through the LlmProcessor interface.
 */
object LlmProcessingService {
    private val activeRequests = AtomicInteger(0)

    /** Check if any LLM processing is currently active */
    val isRunning: Boolean get() = activeRequests.get() > 0

    // Track pending requests per full cache key (doc:key:type:params)
    // Used for request deduplication - same request returns same Deferred
    private val pendingRequests = ConcurrentHashMap<String, RequestState>()

    // Mutex for coroutine-safe state management
    private val requestsMutex = Mutex()

    private val client = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    private val dao get() = DatabaseContainer.instance.llmProcessingDb.llmProcessingDao()

    /** Registered processors by their ID */
    private val processors = ConcurrentHashMap<String, LlmProcessor>()

    init {
        // Register built-in processors
        registerProcessor(TranslationProcessor)
        registerProcessor(PromptProcessor)
    }

    /**
     * Register a processor for use with this service.
     */
    fun registerProcessor(processor: LlmProcessor) {
        processors[processor.processorId] = processor
        Log.i(TAG, "Registered processor: ${processor.processorId}")
    }

    /**
     * Get a processor by ID.
     */
    fun getProcessor(processorId: String): LlmProcessor? = processors[processorId]

    /**
     * Result of checking cache for processed content.
     */
    data class CacheResult(
        val processedXml: String?,
        val documentNeeded: Boolean
    )

    /**
     * Check if cached processed content exists.
     */
    fun getCached(cacheKey: CacheKey): CacheResult {
        if (!CommonUtils.settings.llmConfigured) {
            return CacheResult(null, true)
        }

        val cached = dao.get(
            cacheKey.documentInitials,
            cacheKey.keyName,
            cacheKey.processingType,
            cacheKey.processingParams,
            cacheKey.modelId
        )

        return if (cached != null) {
            Log.d(TAG, "Cache hit for ${cacheKey.documentInitials}:${cacheKey.keyName} [${cacheKey.processingType}/${cacheKey.processingParams}]")
            CacheResult(cached.processedXml, false)
        } else {
            Log.d(TAG, "Cache miss for ${cacheKey.documentInitials}:${cacheKey.keyName} [${cacheKey.processingType}/${cacheKey.processingParams}]")
            CacheResult(null, true)
        }
    }

    /**
     * Check if a chapter-level cache entry exists for a verse-level key.
     * Derives the chapter key from the verse key (e.g., "Isa.65.9" → "Isa.65")
     * and looks up the chapter cache entry. This avoids redundant API calls when
     * synced windows or cross-references request individual verses from an
     * already-processed chapter.
     */
    fun getCachedChapter(cacheKey: CacheKey): CacheResult {
        if (!CommonUtils.settings.llmConfigured) return CacheResult(null, true)

        // Derive chapter key: "Gen.1.5" → "Gen.1", "Isa.65.9" → "Isa.65"
        val chapterKey = cacheKey.keyName.substringBeforeLast('.', cacheKey.keyName)
        if (chapterKey == cacheKey.keyName) return CacheResult(null, true) // Already a chapter key

        val cached = dao.get(cacheKey.documentInitials, chapterKey, cacheKey.processingType, cacheKey.processingParams, cacheKey.modelId)
        return if (cached != null) {
            Log.d(TAG, "Chapter cache hit for ${cacheKey.keyName} via chapter $chapterKey")
            CacheResult(cached.processedXml, false)
        } else {
            CacheResult(null, true)
        }
    }

    /**
     * Process content with tool calling support.
     *
     * This method sends the content to the LLM with read-only tool access,
     * allowing the LLM to call tools like getVerseContent and getInstalledDocuments
     * during processing. Used by LlmProcessedBackend for chapter-level processing.
     *
     * Features:
     * - Tool calling loop (up to maxIterations)
     * - Read-only tools only (no write tools)
     * - Agent log entries for the current workspace session
     * - Request deduplication (same key reuses existing request)
     * - Result caching
     *
     * @param processor The LLM processor providing the system prompt
     * @param cacheKey Cache key for the content
     * @param xmlContent The XML content to process
     * @param maxIterations Maximum number of tool-calling iterations
     * @return The processed XML content
     */
    suspend fun processWithTools(
        processor: LlmProcessor,
        cacheKey: CacheKey,
        xmlContent: String,
        maxIterations: Int = 5,
        modelOverride: String? = null
    ): String {
        val settings = CommonUtils.settings
        if (!settings.llmConfigured) {
            Log.d(TAG, "LLM not configured, returning original content")
            return xmlContent
        }

        // Request deduplication
        val requestKey = "${cacheKey.documentInitials}:${cacheKey.keyName}:${cacheKey.processingType}:${cacheKey.processingParams}:${cacheKey.modelId}"

        Log.d(TAG, "processWithTools START: key=${cacheKey.keyName}, requestKey=$requestKey")

        var isNewRequest = false
        val requestState = requestsMutex.withLock {
            val existing = pendingRequests[requestKey]
            if (existing != null) {
                Log.d(TAG, "processWithTools: REUSING existing request for $requestKey")
                existing
            } else {
                val deferred = CompletableDeferred<String>()
                val state = RequestState(deferred, Job())
                pendingRequests[requestKey] = state
                isNewRequest = true
                Log.d(TAG, "processWithTools: created NEW request for $requestKey")
                state
            }
        }

        if (!isNewRequest) {
            return requestState.deferred.await()
        }

        val resultDeferred = requestState.deferred

        return coroutineScope {
            val job = async {
                try {
                    // Update RequestState with the real Job so cancelAllPendingRequests() works
                    requestsMutex.withLock {
                        pendingRequests[requestKey]?.let {
                            pendingRequests[requestKey] = it.copy(job = coroutineContext[Job]!!)
                        }
                    }
                    doProcessWithTools(processor, cacheKey, xmlContent, maxIterations, modelOverride)
                } finally {
                    requestsMutex.withLock {
                        pendingRequests.remove(requestKey)
                    }
                }
            }

            try {
                val result = job.await()
                resultDeferred.complete(result)
                result
            } catch (e: CancellationException) {
                val superseded = LlmRequestSuperseded()
                resultDeferred.completeExceptionally(superseded)
                throw superseded
            } catch (e: Exception) {
                resultDeferred.completeExceptionally(e)
                throw e
            }
        }
    }

    /**
     * Internal implementation of processWithTools — handles the tool-calling loop.
     */
    private suspend fun doProcessWithTools(
        processor: LlmProcessor,
        cacheKey: CacheKey,
        xmlContent: String,
        maxIterations: Int,
        modelOverride: String? = null
    ): String {
        val session = AgentSessionManager.getCurrentSession()
        // Only manage session lifecycle if no agent is already running
        // (to avoid clearing an active agent's log)
        val manageSession = session != null && !session.isRunning
        val systemPrompt = processor.getSystemPrompt(cacheKey.processingParams)
        val tools = ToolRegistry.toOpenAiToolsArray(includeWriteTools = false)
        val modelId = modelOverride?.takeIf { it.isNotBlank() } ?: CommonUtils.settings.llmModel

        Log.d(TAG, "doProcessWithTools: ${cacheKey.documentInitials}:${cacheKey.keyName} with ${processor.processorId}, tools=${tools.length()}")

        if (manageSession) {
            session!!.start(AgentContext(promptId = IdType.empty()))
            session.addLogEntry(AgentLogEntry.info("Processing ${cacheKey.keyName}..."))
        }

        val messages = JSONArray().apply {
            put(JSONObject().apply { put("role", "system"); put("content", systemPrompt) })
            put(JSONObject().apply { put("role", "user"); put("content", xmlContent) })
        }

        val startTime = System.currentTimeMillis()
        val loopHeaders = buildProviderHeaders()

        if (activeRequests.incrementAndGet() == 1) {
            ABEventBus.post(LlmEvent(running = true))
        }

        try {
            for (iteration in 0 until maxIterations) {
                coroutineContext.ensureActive()  // Stop promptly if cancelled between iterations
                Log.d(TAG, "doProcessWithTools: iteration ${iteration + 1}")
                session?.addLogEntry(AgentLogEntry.info("Processing ${cacheKey.keyName}: iteration ${iteration + 1}"))

                val response = callLlmApiWithTools(messages, tools, modelOverride, loopHeaders)
                val assistantMessage = response.getJSONArray("choices")
                    .getJSONObject(0).getJSONObject("message")
                val parsed = ToolCallParser.parseMessage(assistantMessage)

                when (parsed) {
                    is ParsedResponse.TextResponse -> {
                        val result = cleanXmlResponse(parsed.content)
                        val duration = System.currentTimeMillis() - startTime
                        Log.d(TAG, "doProcessWithTools completed in ${duration}ms (output: ${result.length} chars)")

                        // Cache result
                        dao.insert(LlmProcessingCacheEntry(
                            documentInitials = cacheKey.documentInitials,
                            keyName = cacheKey.keyName,
                            processingType = cacheKey.processingType,
                            processingParams = cacheKey.processingParams,
                            modelId = modelId,
                            processedXml = result,
                            createdAt = System.currentTimeMillis(),
                            languageCode = processor.getLanguageCode(cacheKey.processingParams)
                        ))

                        session?.addLogEntry(AgentLogEntry.info("Processing ${cacheKey.keyName} complete"))
                        if (manageSession) session!!.stop("Processing complete")
                        return result
                    }
                    is ParsedResponse.ToolCalls -> {
                        messages.put(ToolCallParser.createAssistantToolCallMessage(
                            parsed.toolCalls, parsed.content))

                        for (toolCall in parsed.toolCalls) {
                            val tool = ToolRegistry.get(toolCall.name)
                            val displayName = tool?.let { ToolRegistry.getDisplayName(it) } ?: toolCall.name

                            val argsDetails = tool?.let {
                                val args = try { JSONObject(toolCall.arguments) } catch (_: Exception) { null }
                                args?.let { a -> it.formatArgsForLog(a) }
                            } ?: formatJsonForLog(toolCall.arguments)
                            session?.addLogEntry(AgentLogEntry.action(
                                "Tool: $displayName", details = argsDetails))

                            if (tool == null || tool.requiresPermission) {
                                val errorJson = """{"status":"error","message":"Tool not available: ${toolCall.name}"}"""
                                messages.put(ToolCallParser.createToolResultMessage(
                                    toolCall.id, errorJson))
                                session?.addLogEntry(AgentLogEntry.error("Tool not available: $displayName"))
                                continue
                            }

                            val context = AgentContext(promptId = IdType.empty())
                            val toolResult = tool.execute(toolCall.parseArguments(), context)
                            messages.put(ToolCallParser.createToolResultMessage(
                                toolCall.id, toolResult.toJson()))

                            val isSuccess = toolResult is ToolResult.Success
                            session?.addLogEntry(AgentLogEntry(
                                type = LogEntryType.ACTION,
                                message = if (isSuccess) "\u2713 $displayName" else "\u2717 $displayName",
                                status = if (isSuccess) EntryStatus.COMPLETED else EntryStatus.FAILED
                            ))
                        }
                    }
                    is ParsedResponse.ParseError -> {
                        session?.addLogEntry(AgentLogEntry.error(parsed.error))
                        if (manageSession) session!!.stop()
                        throw LlmProcessingError(parsed.error)
                    }
                }
            }

            session?.addLogEntry(AgentLogEntry.error("Max iterations reached for ${cacheKey.keyName}"))
            if (manageSession) session!!.stop()
            throw LlmProcessingError("Max tool iterations reached")
        } catch (e: CancellationException) {
            if (manageSession) session!!.stop()
            throw e
        } catch (e: LlmProcessingError) {
            throw e
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            Log.e(TAG, "doProcessWithTools failed after ${duration}ms: ${e.javaClass.simpleName}: ${e.message}")
            session?.addLogEntry(AgentLogEntry.error(e.message ?: "Unknown error"))
            if (manageSession) session!!.stop()
            throw LlmProcessingError(application.getString(R.string.llm_processing_failed, e.message))
        } finally {
            if (activeRequests.decrementAndGet() == 0) {
                ABEventBus.post(LlmEvent(running = false))
            }
        }
    }

    /**
     * Clean up LLM response that should be XML.
     * Removes markdown code blocks that the LLM might have added.
     */
    private fun cleanXmlResponse(content: String): String {
        return content.trim()
            .removePrefix("```xml")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
    }

    /**
     * Call LLM API with tool calling support.
     *
     * This method is used by the agent executor to make API calls that can
     * include tool definitions and receive tool call responses.
     *
     * @param messages The conversation messages (system, user, assistant, tool)
     * @param tools The tools array in OpenAI function calling format
     * @return The full response JSON object (contains choices[0].message with content or tool_calls)
     */
    /**
     * Build provider-specific HTTP headers for prompt caching optimization.
     * UUID is generated once per call — callers in loops should call this once
     * before the loop and reuse the result across iterations.
     */
    internal fun buildProviderHeaders(): Map<String, String> {
        val provider = try {
            LlmProvider.valueOf(CommonUtils.settings.llmProvider)
        } catch (_: IllegalArgumentException) {
            LlmProvider.fromEndpoint(CommonUtils.settings.llmEndpoint)
        }
        return when (provider) {
            LlmProvider.XAI -> mapOf("x-grok-conv-id" to UUID.randomUUID().toString())
            else -> emptyMap()
        }
    }

    suspend fun callLlmApiWithTools(messages: JSONArray, tools: JSONArray, modelOverride: String? = null, extraHeaders: Map<String, String> = emptyMap()): JSONObject {
        val settings = CommonUtils.settings

        if (!settings.llmConfigured) {
            throw LlmProcessingError("LLM not configured")
        }

        val effectiveModel = modelOverride?.takeIf { it.isNotBlank() } ?: settings.llmModel
        val endpoint = "${settings.llmEndpoint}/chat/completions"

        val requestBody = JSONObject().apply {
            put("model", effectiveModel)
            put("messages", messages)
            if (tools.length() > 0) {
                put("tools", tools)
            }
            put("temperature", LLM_TEMPERATURE)
        }

        Log.d(TAG, "LLM API with tools: $endpoint, model: $effectiveModel, tools: ${tools.length()}")

        val request = Request.Builder()
            .url(endpoint)
            .addHeader("Authorization", "Bearer ${settings.llmApiKey}")
            .addHeader("Content-Type", "application/json")
            .apply { for ((key, value) in extraHeaders) addHeader(key, value) }
            .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        if (activeRequests.incrementAndGet() == 1) {
            ABEventBus.post(LlmEvent(running = true))
        }

        return try {
            suspendCancellableCoroutine { continuation ->
                val call = client.newCall(request)

                continuation.invokeOnCancellation {
                    call.cancel()
                }

                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        Log.e(TAG, "LLM API call with tools failed: ${e.message}")
                        continuation.resumeWithException(e)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        response.use { resp ->
                            if (!resp.isSuccessful) {
                                val errorBody = resp.body?.string() ?: "No error body"
                                Log.e(TAG, "LLM API error: ${resp.code} - $errorBody")
                                continuation.resumeWithException(
                                    LlmProcessingError("LLM API error: ${resp.code} - $errorBody")
                                )
                                return
                            }

                            try {
                                val responseBody = resp.body?.string()
                                    ?: throw LlmProcessingError("Empty response body")
                                val responseJson = JSONObject(responseBody)
                                Log.d(TAG, "LLM API response received")
                                continuation.resume(responseJson)
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to parse LLM response: ${e.message}")
                                continuation.resumeWithException(e)
                            }
                        }
                    }
                })
            }
        } finally {
            if (activeRequests.decrementAndGet() == 0) {
                ABEventBus.post(LlmEvent(running = false))
            }
        }
    }

    /**
     * Cancel all pending LLM requests.
     * Called when user navigates away and in-flight chapters become stale.
     */
    fun cancelAllPendingRequests() {
        val requests = pendingRequests.toMap()
        if (requests.isEmpty()) return
        Log.i(TAG, "Cancelling ${requests.size} pending LLM request(s)")
        for ((key, state) in requests) {
            Log.d(TAG, "Cancelling stale request: $key")
            state.dialog?.dismiss()
            state.job.cancel()
        }
    }

    fun clearCache() {
        dao.deleteAll()
        Log.i(TAG, "LLM processing cache cleared")
    }

    fun clearCacheByType(processingType: String) {
        dao.deleteByType(processingType)
        Log.i(TAG, "LLM processing cache cleared for type: $processingType")
    }

    fun getCacheCount(): Int = dao.count()

    fun getCacheCountByType(processingType: String): Int = dao.countByType(processingType)
}

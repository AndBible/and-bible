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

package net.bible.service.llm

import android.util.Log
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import net.bible.android.BibleApplication.Companion.application
import net.bible.android.activity.R
import net.bible.service.common.CommonUtils
import net.bible.service.db.DatabaseContainer
import net.bible.service.llm.agent.AgentLogEntry
import net.bible.service.llm.agent.AgentSessionManager
import net.bible.service.llm.tools.ToolDefinition
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.bible.android.database.IdType
import net.bible.service.llm.agent.AgentContext
import net.bible.service.llm.agent.EntryStatus
import net.bible.service.llm.agent.LogEntryType
import net.bible.service.llm.processors.PromptProcessor
import net.bible.service.llm.processors.TranslationProcessor
import net.bible.service.llm.tools.ToolRegistry
import net.bible.service.llm.tools.ToolResult
import net.bible.service.llm.tools.formatJsonForLog
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

/** Wrapper for LLM API response with usage information */
data class LlmApiResponse(
    val responseBody: String,
    val usage: LlmUsage
)

/** Exception thrown when LLM processing fails */
class LlmProcessingError(message: String) : Exception(message)

/** Silent exception - request was superseded, no error display needed */
class LlmRequestSuperseded : Exception("Request superseded")

/** Tracks state of a pending request for a specific document:key */
private data class RequestState(
    val deferred: CompletableDeferred<String>,
    val job: Job,
)

/**
 * Service for LLM API communication and document processing.
 *
 * Provides the core API call infrastructure used by AgentExecutor
 * for agent-based LLM interactions, and document-level processing
 * for LLM Mode (AI Text Processing).
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

    private val dao get() = DatabaseContainer.instance.aiSettingsDb.llmProcessingDao()

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
        if (!isConfiguredAny()) {
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
     */
    fun getCachedChapter(cacheKey: CacheKey): CacheResult {
        if (!isConfiguredAny()) return CacheResult(null, true)

        val chapterKey = cacheKey.keyName.substringBeforeLast('.', cacheKey.keyName)
        if (chapterKey == cacheKey.keyName) return CacheResult(null, true)

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
     * Used by LlmProcessedBackend for chapter-level processing.
     */
    suspend fun processWithTools(
        processor: LlmProcessor,
        cacheKey: CacheKey,
        xmlContent: String,
        maxIterations: Int = 5,
        llmConfig: LlmModelConfig? = null
    ): String {
        if (!isConfigured(llmConfig)) {
            Log.d(TAG, "LLM not configured, returning original content")
            return xmlContent
        }

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
                    requestsMutex.withLock {
                        pendingRequests[requestKey]?.let {
                            pendingRequests[requestKey] = it.copy(job = coroutineContext[Job]!!)
                        }
                    }
                    doProcessWithTools(processor, cacheKey, xmlContent, maxIterations, llmConfig)
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
     * Internal implementation of processWithTools -- handles the tool-calling loop.
     * Uses the ChatMessage-based API for provider-agnostic message handling.
     */
    private suspend fun doProcessWithTools(
        processor: LlmProcessor,
        cacheKey: CacheKey,
        xmlContent: String,
        maxIterations: Int,
        llmConfig: LlmModelConfig? = null
    ): String {
        val session = AgentSessionManager.getCurrentSession()
        val manageSession = session != null && !session.isRunning
        val systemPrompt = processor.getSystemPrompt(cacheKey.processingParams)
        val resolved = resolveFromConfig(llmConfig)
        val adapter = resolved.adapter
        val toolDefs = ToolRegistry.getToolDefinitions(includeWriteTools = false)
        val modelId = resolved.model

        Log.d(TAG, "doProcessWithTools: ${cacheKey.documentInitials}:${cacheKey.keyName} with ${processor.processorId}, tools=${toolDefs.size}")

        if (manageSession) {
            session!!.start(AgentContext(promptId = IdType.empty()))
            session.addLogEntry(AgentLogEntry.info("Processing ${cacheKey.keyName}..."))
        }

        val messages = mutableListOf(
            ChatMessage(ChatMessage.Role.SYSTEM, systemPrompt),
            ChatMessage(ChatMessage.Role.USER, xmlContent)
        )

        val startTime = System.currentTimeMillis()
        val loopHeaders = buildProviderExtraHeaders(resolved.providerConfig)
        var totalUsage = LlmUsage()

        try {
            for (iteration in 0 until maxIterations) {
                currentCoroutineContext().ensureActive()
                Log.d(TAG, "doProcessWithTools: iteration ${iteration + 1}")
                session?.addLogEntry(AgentLogEntry.info("Processing ${cacheKey.keyName}: iteration ${iteration + 1}"))

                val apiResponse = callLlmApiWithTools(messages, toolDefs, llmConfig, loopHeaders)
                val parsed = adapter.parseResponse(apiResponse.responseBody)
                totalUsage += apiResponse.usage

                if (apiResponse.usage.totalTokens > 0) {
                    val cost = LlmPricing.estimateCost(apiResponse.usage, modelId)
                    if (cost != null) {
                        session?.setLastEntryCost(LlmCostTracker.formatCost(cost))
                    }
                }

                when (parsed) {
                    is ParsedResponse.TextResponse -> {
                        val result = cleanXmlResponse(parsed.content)
                        val duration = System.currentTimeMillis() - startTime
                        Log.d(TAG, "doProcessWithTools completed in ${duration}ms (output: ${result.length} chars)")

                        if (result.isBlank()) {
                            Log.w(TAG, "LLM returned empty/blank content for ${cacheKey.keyName}")
                            session?.addLogEntry(AgentLogEntry.error("Empty response for ${cacheKey.keyName}"))
                            if (manageSession) session!!.stop()
                            throw LlmProcessingError(application.getString(R.string.llm_empty_llm_response))
                        }

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
                        if (totalUsage.totalTokens > 0) {
                            val totalCost = LlmPricing.estimateCost(totalUsage, modelId)
                            if (totalCost != null) {
                                session?.setLastEntryCost(application.getString(R.string.llm_cost_total, LlmCostTracker.formatCost(totalCost)), isTotalCost = true)
                            }
                        }
                        if (manageSession) session!!.stop("Processing complete")
                        return result
                    }
                    is ParsedResponse.ToolCalls -> {
                        // Add assistant message with tool calls using the adapter helper
                        messages.add(adapter.createAssistantToolCallMessage(parsed.toolCalls, parsed.content))

                        val toolResultBlocks = mutableListOf<ToolResultBlock>()

                        for (toolCall in parsed.toolCalls) {
                            val tool = ToolRegistry.get(toolCall.tool)
                            val displayName = tool?.let { ToolRegistry.getDisplayName(it) } ?: toolCall.tool.camelCaseName

                            val argsDetails = tool?.let {
                                val args = try { JSONObject(toolCall.arguments) } catch (_: Exception) { null }
                                args?.let { a -> it.formatArgsForLog(a) }
                            } ?: formatJsonForLog(toolCall.arguments)
                            session?.addLogEntry(AgentLogEntry.action("Tool: $displayName", details = argsDetails))

                            if (tool == null || tool.requiresPermission) {
                                val errorJson = """{"status":"error","message":"Tool not available: ${toolCall.tool.camelCaseName}"}"""
                                toolResultBlocks.add(ToolResultBlock(toolCall.id, errorJson))
                                session?.addLogEntry(AgentLogEntry.error("Tool not available: $displayName"))
                                continue
                            }

                            val context = AgentContext(promptId = IdType.empty())
                            val toolResult = tool.execute(toolCall.parseArguments(), context)
                            toolResultBlocks.add(ToolResultBlock(toolCall.id, toolResult.toJson()))

                            val isSuccess = toolResult is ToolResult.Success
                            session?.addLogEntry(AgentLogEntry(
                                type = LogEntryType.ACTION,
                                message = if (isSuccess) "\u2713 $displayName" else "\u2717 $displayName",
                                status = if (isSuccess) EntryStatus.COMPLETED else EntryStatus.FAILED
                            ))
                        }

                        // Add tool result messages using the adapter helper
                        messages.addAll(adapter.createToolResultMessages(toolResultBlocks))
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
            if (manageSession) session!!.stop(application.getString(R.string.agent_log_cancelled))
            throw e
        } catch (e: LlmProcessingError) {
            throw e
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            Log.e(TAG, "doProcessWithTools failed after ${duration}ms: ${e.javaClass.simpleName}: ${e.message}")
            session?.addLogEntry(AgentLogEntry.error(e.message ?: "Unknown error"))
            if (manageSession) session!!.stop()
            throw LlmProcessingError(application.getString(R.string.llm_processing_failed, e.message))
        }
    }

    /**
     * Clean up LLM response that should be XML.
     */
    private fun cleanXmlResponse(content: String): String {
        return content.trim()
            .removePrefix("```xml")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
    }

    /**
     * Resolved provider/model/adapter triple, used to thread resolved state through the call chain.
     */
    internal data class ResolvedProvider(
        val providerConfig: LlmProviderConfig,
        val adapter: LlmApiAdapter,
        val model: String,
        val apiKey: String,
        val endpoint: String,
    )

    /**
     * Check if LLM is configured at all (any provider configs exist).
     */
    private fun isConfiguredAny(): Boolean =
        CommonUtils.settings.llmConfigured

    /**
     * Check if a specific llmConfig (or the global default) is configured.
     */
    private fun isConfigured(llmConfig: LlmModelConfig?): Boolean {
        if (llmConfig != null) {
            val providerConfig = llmConfig.resolveProviderConfig()
            if (providerConfig != null) return providerConfig.getApiKey().isNotBlank()
        }
        return isConfiguredAny()
    }

    /**
     * Resolve provider, model, adapter, API key, and endpoint from an LlmModelConfig.
     */
    internal fun resolveFromConfig(llmConfig: LlmModelConfig? = null): ResolvedProvider {
        val providerConfig = llmConfig?.resolveProviderConfig()
            ?: DatabaseContainer.instance.aiSettingsDb.llmProviderConfigDao().getDefault()
            ?: throw IllegalStateException("No LLM provider configured")

        val model = llmConfig?.resolveModel(providerConfig) ?: providerConfig.resolveDefaultModel()
        return ResolvedProvider(
            providerConfig = providerConfig,
            adapter = providerConfig.resolveAdapter(),
            model = model,
            apiKey = providerConfig.getApiKey(),
            endpoint = providerConfig.resolveEndpoint(),
        )
    }

    /**
     * Resolve the current provider and return its API adapter.
     */
    internal fun resolveAdapter(llmConfig: LlmModelConfig? = null): LlmApiAdapter {
        return resolveFromConfig(llmConfig).adapter
    }

    /**
     * Build provider-specific extra HTTP headers (beyond auth/content-type).
     * UUID is generated once per call — callers in loops should call this once
     * before the loop and reuse the result across iterations.
     */
    internal fun buildProviderExtraHeaders(providerConfig: LlmProviderConfig): Map<String, String> {
        val provider = providerConfig.resolveProvider()
        return when (provider) {
            LlmProvider.XAI -> mapOf("x-grok-conv-id" to UUID.randomUUID().toString())
            else -> emptyMap()
        }
    }

    /** Result of a single HTTP call to the LLM API. */
    private sealed class HttpCallResult {
        data class Success(val body: String) : HttpCallResult()
        data class Error(
            val code: Int,
            val bodyText: String,
            val retryAfterSeconds: Double?
        ) : HttpCallResult()
    }

    /**
     * Execute a single HTTP call to the LLM API.
     * Returns [HttpCallResult.Success] or [HttpCallResult.Error] instead of throwing on non-2xx.
     * [IOException] (network errors) are still thrown directly.
     */
    private suspend fun executeHttpCall(request: Request): HttpCallResult {
        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)

            continuation.invokeOnCancellation {
                call.cancel()
            }

            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e(TAG, "LLM API call failed: ${e.message}")
                    continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use { resp ->
                        if (!resp.isSuccessful) {
                            val retryAfter = LlmRetryPolicy.parseRetryAfterHeader(resp.header("retry-after"))
                            val errorBody = resp.body?.string() ?: "No error body"
                            continuation.resume(HttpCallResult.Error(resp.code, errorBody, retryAfter))
                            return
                        }

                        try {
                            val responseBody = resp.body?.string()
                                ?: throw LlmProcessingError("Empty response body")
                            Log.d(TAG, "LLM API response received")
                            continuation.resume(HttpCallResult.Success(responseBody))
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to read LLM response: ${e.message}")
                            continuation.resumeWithException(e)
                        }
                    }
                }
            })
        }
    }

    /**
     * Call LLM API with tool calling support.
     *
     * Uses the provider's API adapter for endpoint URL, headers, and request body format.
     * Extracts token usage from the response and adds it to cumulative tracking.
     * Retries on rate-limit and transient server errors (429, 502, 503, 529) with
     * exponential backoff, respecting the Retry-After header when present.
     *
     * @param messages The conversation messages (system, user, assistant, tool)
     * @param toolDefs Tool definitions to include in the request
     * @param llmConfig Optional per-prompt provider+model override
     * @param extraHeaders Provider-specific extra headers (e.g. xAI conv-id)
     * @return LlmApiResponse containing the response body string and extracted token usage
     */
    suspend fun callLlmApiWithTools(messages: List<ChatMessage>, toolDefs: List<ToolDefinition>, llmConfig: LlmModelConfig? = null, extraHeaders: Map<String, String> = emptyMap()): LlmApiResponse {
        val resolved = resolveFromConfig(llmConfig)

        if (resolved.apiKey.isBlank()) {
            throw LlmProcessingError("LLM not configured")
        }

        val adapter = resolved.adapter
        val effectiveModel = resolved.model
        val endpoint = adapter.buildEndpointUrl(resolved.endpoint)

        val bodyString = adapter.buildRequestBody(effectiveModel, messages, toolDefs, LLM_TEMPERATURE)
        val systemLen = messages.firstOrNull { it.role == ChatMessage.Role.SYSTEM }?.content?.length ?: 0
        val userLen = messages.firstOrNull { it.role == ChatMessage.Role.USER }?.content?.length ?: 0
        val safeEndpoint = endpoint.substringBefore('?')
        Log.d(TAG, "LLM API with tools: $safeEndpoint, model: $effectiveModel, tools: ${toolDefs.size}, body: ${bodyString.length} bytes, system: $systemLen chars, user: $userLen chars")

        val headers = adapter.buildHeaders(resolved.apiKey, extraHeaders)
        val request = Request.Builder()
            .url(endpoint)
            .apply { for ((key, value) in headers) addHeader(key, value) }
            .post(bodyString.toRequestBody("application/json".toMediaType()))
            .build()

        activeRequests.incrementAndGet()

        return try {
            val session = AgentSessionManager.getCurrentSession()
            var lastError: HttpCallResult.Error? = null

            for (attempt in 0..LlmRetryPolicy.MAX_RETRIES) {
                currentCoroutineContext().ensureActive()

                if (attempt > 0) {
                    val prev = lastError!!
                    val delayMs = LlmRetryPolicy.calculateDelayMs(attempt - 1, prev.retryAfterSeconds)
                    val delaySec = "%.1f".format(delayMs / 1000.0)
                    Log.w(TAG, "Rate limited (HTTP ${prev.code}). Retry $attempt/${LlmRetryPolicy.MAX_RETRIES} after ${delayMs}ms")
                    session?.addLogEntry(AgentLogEntry.info(
                        application.getString(R.string.llm_rate_limited_retrying, delaySec)
                    ))
                    delay(delayMs)
                }

                when (val result = executeHttpCall(request)) {
                    is HttpCallResult.Success -> {
                        val usage = adapter.extractUsage(result.body)
                        if (usage.totalTokens > 0) {
                            LlmCostTracker.addUsage(usage, effectiveModel, resolved.providerConfig.id)
                        }
                        return LlmApiResponse(result.body, usage)
                    }
                    is HttpCallResult.Error -> {
                        if (LlmRetryPolicy.isRetryable(result.code) && attempt < LlmRetryPolicy.MAX_RETRIES) {
                            Log.w(TAG, "LLM API retryable error: ${result.code} - ${result.bodyText.take(200)}")
                            lastError = result
                            continue
                        }
                        Log.e(TAG, "LLM API error: ${result.code} - ${result.bodyText}")
                        throw LlmProcessingError("LLM API error: ${result.code} - ${result.bodyText}")
                    }
                }
            }

            // Should not reach here, but just in case
            throw LlmProcessingError("LLM API error: ${lastError!!.code} - ${lastError.bodyText}")
        } finally {
            activeRequests.decrementAndGet()
        }
    }

    /**
     * Cancel all pending LLM requests.
     */
    fun cancelAllPendingRequests() {
        val requests = pendingRequests.toMap()
        if (requests.isEmpty()) return
        Log.i(TAG, "Cancelling ${requests.size} pending LLM request(s)")
        for ((key, state) in requests) {
            Log.d(TAG, "Cancelling stale request: $key")
            state.job.cancel()
        }
    }

    fun clearCache() {
        dao.deleteAll()
        clearAllProcessedBooks()
        Log.i(TAG, "LLM processing cache cleared")
    }

    fun clearCacheByType(processingType: String) {
        dao.deleteByType(processingType)
        Log.i(TAG, "LLM processing cache cleared for type: $processingType")
    }

    fun getCacheCount(): Int = dao.count()

    fun getCacheCountByType(processingType: String): Int = dao.countByType(processingType)

}

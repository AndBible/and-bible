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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import net.bible.android.BibleApplication.Companion.application
import net.bible.android.activity.R
import net.bible.android.control.event.ABEventBus
import net.bible.android.view.activity.base.CurrentActivityHolder
import net.bible.service.common.CommonUtils
import net.bible.service.db.DatabaseContainer
import net.bible.service.llm.processors.TranslationProcessor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

private const val TAG = "LlmProcessingService"

/** Event posted when LLM operations start or complete */
class LlmEvent(val running: Boolean)

/** Exception thrown when LLM processing fails */
class LlmProcessingError(message: String) : Exception(message)

/** Tracks state of a pending request for a specific document:key */
private data class RequestState(
    val deferred: CompletableDeferred<String>,
    val job: Job,
    var inApiCall: Boolean = false,  // True when API call has started (can't be cancelled)
    var dialog: AlertDialog? = null  // Reference to confirmation dialog if shown
)

/** Tracks current active request for a document (without specific key) */
private data class DocumentState(
    val currentKeyName: String,
    val requestState: RequestState
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

    // Track current request per document (doc:type:params, without key)
    // Used for document-level coordination
    private val documentStates = ConcurrentHashMap<String, DocumentState>()

    private val client = OkHttpClient.Builder()
        .connectTimeout(120, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)  // 5 min for reasoning models
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    private val dao get() = DatabaseContainer.instance.llmProcessingDb.llmProcessingDao()

    /** Registered processors by their ID */
    private val processors = ConcurrentHashMap<String, LlmProcessor>()

    init {
        // Register built-in processors
        registerProcessor(TranslationProcessor)
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
            cacheKey.processingParams
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
     * Show confirmation dialog before LLM API call if setting is enabled.
     */
    private suspend fun confirmLlmCall(
        processor: LlmProcessor,
        cacheKey: CacheKey,
        contentSize: Int,
        requestState: RequestState
    ): Boolean {
        val settings = CommonUtils.settings
        if (!settings.llmConfirmBeforeCall) {
            return true  // No confirmation needed
        }

        val activity = CurrentActivityHolder.currentActivity ?: return true  // No activity, allow

        return withContext(Dispatchers.Main) {
            suspendCoroutine { continuation ->
                val description = processor.getDescription(cacheKey.processingParams)
                val message = activity.getString(
                    R.string.llm_confirm_dialog_message,
                    description,
                    cacheKey.documentInitials,
                    cacheKey.keyName,
                    contentSize,
                    settings.llmModel
                )

                val dialog = AlertDialog.Builder(activity)
                    .setTitle(R.string.llm_confirm_dialog_title)
                    .setMessage(message)
                    .setPositiveButton(R.string.okay) { _, _ -> continuation.resume(true) }
                    .setNegativeButton(R.string.cancel) { _, _ -> continuation.resume(false) }
                    .setOnCancelListener { continuation.resume(false) }
                    .create()

                // Store dialog reference for cancellation
                requestState.dialog = dialog
                dialog.show()
            }
        }
    }

    /**
     * Process content using the specified processor and cache the result.
     *
     * Features:
     * - Request deduplication: same doc:key:type:params reuses existing request
     * - Document coordination: cancels pre-API requests when new key comes for same doc
     * - Debounce: delays API call when confirmation is disabled
     */
    suspend fun processAndCache(
        processor: LlmProcessor,
        cacheKey: CacheKey,
        xmlContent: String
    ): String {
        val settings = CommonUtils.settings
        if (!settings.llmConfigured) {
            Log.d(TAG, "LLM not configured, returning original content")
            return xmlContent
        }

        // Full request key includes all cache key components
        val requestKey = "${cacheKey.documentInitials}:${cacheKey.keyName}:${cacheKey.processingType}:${cacheKey.processingParams}"

        // Document key (without keyName) for coordination
        val documentKey = "${cacheKey.documentInitials}:${cacheKey.processingType}:${cacheKey.processingParams}"

        // Check if exact same request is already in flight - reuse it
        pendingRequests[requestKey]?.let { existingRequest ->
            Log.d(TAG, "Reusing existing request for $requestKey")
            return existingRequest.deferred.await()
        }

        return coroutineScope {
            val resultDeferred = CompletableDeferred<String>()
            val job = async {
                try {
                    // Handle document-level coordination - cancel pre-API requests for different keys
                    documentStates[documentKey]?.let { currentState ->
                        if (currentState.currentKeyName != cacheKey.keyName && !currentState.requestState.inApiCall) {
                            // Different key, pre-API phase - cancel it
                            Log.d(TAG, "Cancelling pre-API request for $documentKey (key: ${currentState.currentKeyName})")
                            withContext(Dispatchers.Main) {
                                currentState.requestState.dialog?.dismiss()
                            }
                            currentState.requestState.job.cancel()
                        }
                        // If inApiCall, let it continue in background - don't wait
                    }

                    // Register our request
                    val requestState = RequestState(resultDeferred, coroutineContext[Job]!!)
                    pendingRequests[requestKey] = requestState
                    documentStates[documentKey] = DocumentState(cacheKey.keyName, requestState)

                    // Debounce delay only if confirmation is disabled
                    if (!settings.llmConfirmBeforeCall) {
                        val debounceMs = settings.llmDebounceMs.toLong()
                        if (debounceMs > 0) {
                            delay(debounceMs)
                            ensureActive()  // Check if cancelled during delay
                        }
                    }

                    // Confirm with user if setting enabled
                    if (!confirmLlmCall(processor, cacheKey, xmlContent.length, requestState)) {
                        throw LlmProcessingError(application.getString(R.string.llm_user_cancelled))
                    }

                    ensureActive()

                    // Mark as in API call phase (can't be cancelled anymore)
                    requestState.inApiCall = true

                    // Proceed with actual API call
                    doProcessAndCache(processor, cacheKey, xmlContent)
                } finally {
                    pendingRequests.remove(requestKey)
                    // Only remove document state if we're still the current request
                    if (documentStates[documentKey]?.currentKeyName == cacheKey.keyName) {
                        documentStates.remove(documentKey)
                    }
                }
            }

            try {
                val result = job.await()
                resultDeferred.complete(result)
                result
            } catch (e: CancellationException) {
                Log.d(TAG, "Request cancelled for $requestKey")
                resultDeferred.completeExceptionally(
                    LlmProcessingError(application.getString(R.string.llm_request_superseded))
                )
                throw LlmProcessingError(application.getString(R.string.llm_request_superseded))
            } catch (e: Exception) {
                resultDeferred.completeExceptionally(e)
                throw e
            }
        }
    }

    /**
     * Internal implementation of processAndCache - handles actual API call and caching.
     */
    private suspend fun doProcessAndCache(
        processor: LlmProcessor,
        cacheKey: CacheKey,
        xmlContent: String
    ): String {
        val modelId = CommonUtils.settings.llmModel

        Log.d(TAG, "Processing ${cacheKey.documentInitials}:${cacheKey.keyName} with ${processor.processorId}/${cacheKey.processingParams}")
        val startTime = System.currentTimeMillis()

        if (activeRequests.incrementAndGet() == 1) {
            ABEventBus.post(LlmEvent(running = true))
        }

        return try {
            val systemPrompt = processor.getSystemPrompt(cacheKey.processingParams)
            val processed = callLlmApi(systemPrompt, xmlContent)
            val duration = System.currentTimeMillis() - startTime
            Log.d(TAG, "LLM processing completed in ${duration}ms (output size: ${processed.length} chars)")

            dao.insert(LlmProcessingCacheEntry(
                documentInitials = cacheKey.documentInitials,
                keyName = cacheKey.keyName,
                processingType = cacheKey.processingType,
                processingParams = cacheKey.processingParams,
                modelId = modelId,
                processedXml = processed,
                createdAt = System.currentTimeMillis()
            ))

            Log.d(TAG, "Processing successful, saved to cache")
            processed
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            Log.e(TAG, "Processing failed after ${duration}ms: ${e.javaClass.simpleName}: ${e.message}")
            throw LlmProcessingError("LLM processing failed: ${e.message}")
        } finally {
            if (activeRequests.decrementAndGet() == 0) {
                ABEventBus.post(LlmEvent(running = false))
            }
        }
    }

    private suspend fun callLlmApi(systemPrompt: String, userContent: String): String =
        withContext(Dispatchers.IO) {
            val settings = CommonUtils.settings
            val endpoint = "${settings.llmEndpoint}/chat/completions"

            val requestBody = JSONObject().apply {
                put("model", settings.llmModel)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", systemPrompt)
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", userContent)
                    })
                })
                put("temperature", 0.3)
            }

            Log.d(TAG, "LLM API: $endpoint, model: ${settings.llmModel}")

            val request = Request.Builder()
                .url(endpoint)
                .addHeader("Authorization", "Bearer ${settings.llmApiKey}")
                .addHeader("Content-Type", "application/json")
                .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "No error body"
                Log.e(TAG, "LLM API error: ${response.code} - $errorBody")
                throw Exception("LLM API error: ${response.code}")
            }

            val responseBody = response.body?.string() ?: throw Exception("Empty response body")
            val responseJson = JSONObject(responseBody)

            val content = responseJson
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")

            // Clean up any markdown code blocks that the LLM might have added
            content.trim()
                .removePrefix("```xml")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()
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

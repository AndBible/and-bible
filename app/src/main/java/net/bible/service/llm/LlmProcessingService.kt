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
import net.bible.android.database.IdType
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

import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val TAG = "LlmProcessingService"
private const val CONNECT_TIMEOUT_SECONDS = 120L
private const val READ_TIMEOUT_SECONDS = 90L  // 90s per API call; generous for reasoning models
private const val WRITE_TIMEOUT_SECONDS = 120L
private const val LLM_TEMPERATURE = 0.3
private const val PREF_NO_TEMPERATURE_MODELS = "llm_no_temperature_models"

/** Wrapper for LLM API response with usage information */
data class LlmApiResponse(
    val responseBody: String,
    val usage: LlmUsage
)

/** Exception thrown when LLM processing fails */
class LlmProcessingError(message: String) : Exception(message)


/**
 * Service for LLM API communication.
 *
 * Provides the core API call infrastructure used by AgentExecutor
 * for agent-based LLM interactions.
 */
object LlmProcessingService {
    private val activeRequests = AtomicInteger(0)

    /** Check if any LLM processing is currently active */
    val isRunning: Boolean get() = activeRequests.get() > 0

    private val client = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    private val testClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * Resolved provider/model/adapter triple, used to thread resolved state through the call chain.
     */
    internal data class ResolvedProvider(
        val providerConfig: LlmProviderConfig,
        val adapter: LlmApiAdapter,
        val model: String,
        val apiKey: String,
        val endpoint: String,
        val configuredModelId: IdType? = null,
    )

    /**
     * Resolve provider, model, adapter, API key, and endpoint from an LlmModelConfig.
     */
    internal fun resolveFromConfig(llmConfig: LlmModelConfig? = null): ResolvedProvider {
        val configuredModel = (llmConfig ?: LlmModelConfig()).resolveConfiguredModel()
            ?: throw IllegalStateException("No LLM model configured")

        val providerConfig = DatabaseContainer.instance.aiSettingsDb.llmProviderConfigDao()
            .getById(configuredModel.providerConfigId)
            ?: throw IllegalStateException("LLM provider not found for model ${configuredModel.modelId}")

        return ResolvedProvider(
            providerConfig = providerConfig,
            adapter = providerConfig.resolveAdapter(),
            model = configuredModel.modelId,
            apiKey = providerConfig.getApiKey(),
            endpoint = providerConfig.resolveEndpoint(),
            configuredModelId = configuredModel.id,
        )
    }

    /**
     * Test API connection by sending a minimal request. Throws on failure.
     * Used by Easy Setup to validate API keys before saving.
     */
    fun testApiConnection(provider: LlmProvider, modelId: String, apiKey: String) {
        val providerConfig = LlmProviderConfig(
            providerType = provider.name,
            displayName = provider.displayName,
        )
        val adapter = providerConfig.resolveAdapter()
        val endpoint = providerConfig.resolveEndpoint()
        val messages = listOf(ChatMessage(ChatMessage.Role.USER, "Hi"))
        val body = adapter.buildRequestBody(modelId, messages, emptyList(), temperature = null)
        val url = adapter.buildEndpointUrl(endpoint)
        val headers = adapter.buildHeaders(apiKey, emptyMap())
        val request = Request.Builder().url(url).apply {
            headers.forEach { (k, v) -> addHeader(k, v) }
            addHeader("Content-Type", "application/json")
            post(body.toRequestBody("application/json".toMediaType()))
        }.build()
        val response = testClient.newCall(request).execute()
        response.use {
            if (!it.isSuccessful) {
                val errorBody = it.body.string().take(200)
                throw LlmProcessingError("HTTP ${it.code}: $errorBody (URL: $url)")
            }
        }
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
    internal suspend fun callLlmApiWithTools(messages: List<ChatMessage>, toolDefs: List<ToolDefinition>, llmConfig: LlmModelConfig? = null, extraHeaders: Map<String, String> = emptyMap(), preResolved: ResolvedProvider? = null): LlmApiResponse {
        val resolved = preResolved ?: resolveFromConfig(llmConfig)

        if (resolved.apiKey.isBlank()) {
            throw LlmProcessingError("LLM not configured")
        }

        val adapter = resolved.adapter
        val effectiveModel = resolved.model
        val endpoint = adapter.buildEndpointUrl(resolved.endpoint)
        val headers = adapter.buildHeaders(resolved.apiKey, extraHeaders)
        val noTempModels = CommonUtils.settings.getStringSet(PREF_NO_TEMPERATURE_MODELS)
        val temperature: Double? = if (effectiveModel in noTempModels) null else LLM_TEMPERATURE

        fun buildRequest(temp: Double?): Request {
            val bodyString = adapter.buildRequestBody(effectiveModel, messages, toolDefs, temp)
            val systemLen = messages.firstOrNull { it.role == ChatMessage.Role.SYSTEM }?.content?.length ?: 0
            val userLen = messages.firstOrNull { it.role == ChatMessage.Role.USER }?.content?.length ?: 0
            val safeEndpoint = endpoint.substringBefore('?')
            Log.d(TAG, "LLM API with tools: $safeEndpoint, model: $effectiveModel, tools: ${toolDefs.size}, body: ${bodyString.length} bytes, system: $systemLen chars, user: $userLen chars, temperature: $temp")
            return Request.Builder()
                .url(endpoint)
                .apply { for ((key, value) in headers) addHeader(key, value) }
                .post(bodyString.toRequestBody("application/json".toMediaType()))
                .build()
        }

        var request = buildRequest(temperature)

        activeRequests.incrementAndGet()

        return try {
            val session = AgentSessionManager.getCurrentSession()
            var lastError: HttpCallResult.Error? = null
            var temperatureRetried = false

            for (attempt in 0..LlmRetryPolicy.MAX_RETRIES) {
                currentCoroutineContext().ensureActive()

                if (attempt > 0 && lastError != null) {
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
                            if (resolved.configuredModelId != null) {
                                LlmCostTracker.addUsage(usage, effectiveModel, resolved.configuredModelId)
                            }
                        }
                        return LlmApiResponse(result.body, usage)
                    }
                    is HttpCallResult.Error -> {
                        // If 400 error mentions "temperature", retry without it and remember
                        if (result.code == 400 && !temperatureRetried && temperature != null
                            && result.bodyText.contains("temperature", ignoreCase = true)) {
                            Log.w(TAG, "Temperature not supported by model $effectiveModel, retrying without it")
                            val updated = noTempModels.toMutableSet().apply { add(effectiveModel) }
                            CommonUtils.settings.setStringSet(PREF_NO_TEMPERATURE_MODELS, updated)
                            temperatureRetried = true
                            request = buildRequest(null)
                            lastError = null
                            continue
                        }
                        if (LlmRetryPolicy.isRetryable(result.code) && attempt < LlmRetryPolicy.MAX_RETRIES) {
                            Log.w(TAG, "LLM API retryable error: ${result.code} - ${result.bodyText.take(200)}")
                            lastError = result
                            continue
                        }
                        Log.e(TAG, "LLM API error: ${result.code} - ${result.bodyText} (URL: $endpoint)")
                        throw LlmProcessingError("LLM API error: ${result.code} - ${result.bodyText} (URL: $endpoint)")
                    }
                }
            }

            // Should not reach here, but just in case
            throw LlmProcessingError("LLM API error: ${lastError!!.code} - ${lastError.bodyText} (URL: $endpoint)")
        } finally {
            activeRequests.decrementAndGet()
        }
    }

}

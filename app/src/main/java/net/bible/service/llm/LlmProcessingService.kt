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
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import net.bible.android.BibleApplication.Companion.application
import net.bible.android.activity.R
import net.bible.service.common.CommonUtils
import net.bible.service.db.DatabaseContainer
import net.bible.service.llm.agent.AgentLogEntry
import net.bible.service.llm.agent.AgentSessionManager
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

/** Wrapper for LLM API response with usage information */
data class LlmApiResponse(val json: JSONObject, val usage: LlmUsage)

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

    /**
     * Resolved provider/model/adapter triple, used to thread resolved state through the call chain.
     */
    internal data class ResolvedProvider(
        val providerConfig: LlmProviderConfig?,
        val adapter: LlmApiAdapter,
        val model: String,
        val apiKey: String,
        val endpoint: String,
    )

    /**
     * Check if LLM is configured at all (legacy or new provider configs exist).
     */
    private fun isConfiguredAny(): Boolean =
        CommonUtils.settings.llmConfigured || CommonUtils.settings.llmHasProviderConfigs

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
     * Falls back to legacy global settings if no provider config is found.
     */
    internal fun resolveFromConfig(llmConfig: LlmModelConfig? = null): ResolvedProvider {
        val providerConfig = llmConfig?.resolveProviderConfig()
            ?: DatabaseContainer.instance.aiSettingsDb.llmProviderConfigDao().getDefault()

        return if (providerConfig != null) {
            val model = llmConfig?.resolveModel(providerConfig) ?: providerConfig.resolveDefaultModel()
            ResolvedProvider(
                providerConfig = providerConfig,
                adapter = providerConfig.resolveAdapter(),
                model = model,
                apiKey = providerConfig.getApiKey(),
                endpoint = providerConfig.resolveEndpoint(),
            )
        } else {
            // Legacy fallback: use old global settings
            val settings = CommonUtils.settings
            val provider = resolveProviderLegacy()
            ResolvedProvider(
                providerConfig = null,
                adapter = provider.apiAdapter,
                model = llmConfig?.model?.takeIf { it.isNotBlank() } ?: settings.llmModel,
                apiKey = settings.llmApiKey,
                endpoint = settings.llmEndpoint,
            )
        }
    }

    /**
     * Resolve the current provider and return its API adapter.
     * Uses the default provider config if available, else falls back to legacy settings.
     */
    internal fun resolveAdapter(llmConfig: LlmModelConfig? = null): LlmApiAdapter {
        return resolveFromConfig(llmConfig).adapter
    }

    /**
     * Resolve the current LLM provider from legacy settings.
     */
    private fun resolveProviderLegacy(): LlmProvider {
        return try {
            LlmProvider.valueOf(CommonUtils.settings.llmProvider)
        } catch (_: IllegalArgumentException) {
            LlmProvider.fromEndpoint(CommonUtils.settings.llmEndpoint)
        }
    }

    /**
     * Build provider-specific extra HTTP headers (beyond auth/content-type).
     * UUID is generated once per call — callers in loops should call this once
     * before the loop and reuse the result across iterations.
     */
    internal fun buildProviderExtraHeaders(providerConfig: LlmProviderConfig? = null): Map<String, String> {
        val provider = providerConfig?.resolveProvider() ?: resolveProviderLegacy()
        return when (provider) {
            LlmProvider.XAI -> mapOf("x-grok-conv-id" to UUID.randomUUID().toString())
            else -> emptyMap()
        }
    }

    /** Result of a single HTTP call to the LLM API. */
    private sealed class HttpCallResult {
        data class Success(val bodyJson: JSONObject) : HttpCallResult()
        data class Error(val code: Int, val bodyText: String, val retryAfterSeconds: Double?) : HttpCallResult()
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
                            val json = JSONObject(responseBody)
                            Log.d(TAG, "LLM API response received")
                            continuation.resume(HttpCallResult.Success(json))
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to parse LLM response: ${e.message}")
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
     * @param tools The tools array in provider-specific format
     * @param llmConfig Optional per-prompt provider+model override
     * @param extraHeaders Provider-specific extra headers (e.g. xAI conv-id)
     * @return LlmApiResponse containing the raw JSON and extracted token usage
     */
    suspend fun callLlmApiWithTools(messages: JSONArray, tools: JSONArray, llmConfig: LlmModelConfig? = null, extraHeaders: Map<String, String> = emptyMap()): LlmApiResponse {
        val resolved = resolveFromConfig(llmConfig)

        if (resolved.apiKey.isBlank()) {
            throw LlmProcessingError("LLM not configured")
        }

        val adapter = resolved.adapter
        val effectiveModel = resolved.model
        val endpoint = adapter.buildEndpointUrl(resolved.endpoint)

        val requestBody = adapter.buildRequestBody(effectiveModel, messages, tools, LLM_TEMPERATURE)

        val bodyString = requestBody.toString()
        val systemLen = messages.optJSONObject(0)?.optString("content")?.length ?: 0
        val userLen = messages.optJSONObject(1)?.optString("content")?.length ?: 0
        val safeEndpoint = endpoint.substringBefore('?')
        Log.d(TAG, "LLM API with tools: $safeEndpoint, model: $effectiveModel, tools: ${tools.length()}, body: ${bodyString.length} bytes, system: $systemLen chars, user: $userLen chars")

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
                coroutineContext.ensureActive()

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
                        val usage = adapter.extractUsage(result.bodyJson)
                        if (usage.totalTokens > 0) {
                            LlmCostTracker.addUsage(usage, effectiveModel, resolved.providerConfig?.id)
                        }
                        return LlmApiResponse(result.bodyJson, usage)
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

}

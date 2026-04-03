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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.bible.android.BibleApplication
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

private const val TAG = "DynamicModelService"
private const val CACHE_MAX_AGE_MS = 7 * 24 * 60 * 60 * 1000L  // 7 days

private val lenientJson = Json { ignoreUnknownKeys = true }

/** OpenAI-compatible /v1/models response wrapper. */
@Serializable
private data class ModelsResponse(val data: List<ApiModel> = emptyList())

@Serializable
private data class ApiModel(
    val id: String,
    val name: String = "",
    val pricing: ApiPricing? = null,
)

/** Pricing extension (returned by OpenRouter, absent from most other providers). */
@Serializable
private data class ApiPricing(
    val prompt: String = "0",
    val completion: String = "0",
)

/**
 * Fetches and caches model lists from OpenAI-compatible `/v1/models` endpoints.
 *
 * Works with any provider that supports the standard endpoint (Gemini, OpenAI, Mistral,
 * DeepSeek, Groq, Alibaba, OpenRouter, etc.). Pricing is extracted when present
 * (e.g. OpenRouter) and ignored otherwise.
 *
 * Each provider's cache is stored as a separate file keyed by provider ID.
 */
object DynamicModelService {

    data class DynamicModel(
        val id: String,
        val name: String,
        val pricing: ModelPricing?
    ) {
        /** Provider/category prefix, e.g. "anthropic" from "anthropic/claude-sonnet-4". */
        val category: String get() = id.substringBefore('/', "")
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    // In-memory cache: providerId → (models, timestampMs)
    private val cache = mutableMapOf<String, Pair<List<DynamicModel>, Long>>()

    private fun cacheFile(providerId: String): File =
        File(BibleApplication.application.cacheDir, "models_cache_${providerId}.json")

    /** Cache timestamp for a provider (epoch ms), or 0 if never fetched. */
    fun cacheTimestamp(providerId: String): Long {
        cache[providerId]?.let { return it.second }
        val file = cacheFile(providerId)
        return if (file.exists()) file.lastModified() else 0L
    }

    /** Whether the cache is missing or older than 7 days. */
    fun needsRefresh(providerId: String): Boolean {
        val ts = cacheTimestamp(providerId)
        return ts == 0L || System.currentTimeMillis() - ts > CACHE_MAX_AGE_MS
    }

    /** Get cached models without network calls. Loads from file on first access. */
    fun getCachedModels(providerId: String): List<DynamicModel>? {
        cache[providerId]?.let { return it.first }
        return loadFromDisk(providerId)
    }

    /**
     * Fetch models from an OpenAI-compatible `/models` endpoint.
     * @param endpoint Provider's base API endpoint (e.g. "https://api.openai.com/v1")
     * @param apiKey API key for authentication.
     * @param providerId Cache key (e.g. "OPENAI", "OPENROUTER", or config ID for CUSTOM).
     */
    suspend fun fetchModels(endpoint: String, apiKey: String, providerId: String): List<DynamicModel>? =
        withContext(Dispatchers.IO) {
            try {
                val modelsUrl = buildModelsUrl(endpoint)
                val request = Request.Builder()
                    .url(modelsUrl)
                    .addHeader("Authorization", "Bearer $apiKey")
                    .build()
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    Log.w(TAG, "[$providerId] API returned ${response.code}")
                    return@withContext null
                }
                val body = response.body?.string() ?: return@withContext null
                val models = parseModels(body)
                if (models.isNotEmpty()) {
                    saveToDisk(providerId, body)
                    cache[providerId] = models to System.currentTimeMillis()
                }
                models
            } catch (e: Exception) {
                Log.w(TAG, "[$providerId] Failed to fetch models", e)
                null
            }
        }

    /** Whether all disk cache files have been loaded into memory. */
    private var allDiskCachesLoaded = false

    private fun ensureAllDiskCachesLoaded() {
        if (allDiskCachesLoaded) return
        cacheFile("").parentFile?.listFiles { f -> f.name.startsWith("models_cache_") && f.name.endsWith(".json") }
            ?.forEach { file ->
                val pid = file.name.removePrefix("models_cache_").removeSuffix(".json")
                if (pid !in cache) {
                    loadFromDisk(pid)
                }
            }
        allDiskCachesLoaded = true
    }

    /** Get pricing for a model from any provider's cache. */
    fun getPricingForModel(modelId: String): ModelPricing? {
        // Quick check in already-loaded caches
        for ((_, entry) in cache) {
            entry.first.find { it.id == modelId }?.pricing?.let { return it }
        }
        // Load remaining disk caches and retry
        ensureAllDiskCachesLoaded()
        for ((_, entry) in cache) {
            entry.first.find { it.id == modelId }?.pricing?.let { return it }
        }
        return null
    }

    private fun buildModelsUrl(endpoint: String): String {
        val base = endpoint.trimEnd('/')
        return "$base/models"
    }

    /**
     * Non-chat model patterns filtered out from dynamic model lists.
     * OpenAI's /v1/models returns all model types (image, audio, embedding, etc.)
     * but we only want chat/completion models.
     */
    private val NON_CHAT_PATTERN = Regex(
        "audio|realtime|tts|image|transcribe|search|codex|embedding|moderation|whisper|dall-e|sora|babbage|davinci|instruct|robotics|computer-use|\\baqa\\b|\\blive\\b",
        RegexOption.IGNORE_CASE
    )

    private fun parseModels(json: String): List<DynamicModel> {
        return try {
            lenientJson.decodeFromString<ModelsResponse>(json).data
                .filter { it.id.isNotBlank() && !NON_CHAT_PATTERN.containsMatchIn(it.id) }
                .map { api ->
                    val prompt = api.pricing?.prompt?.toDoubleOrNull() ?: 0.0
                    val completion = api.pricing?.completion?.toDoubleOrNull() ?: 0.0
                    val pricing = if (prompt > 0 || completion > 0) {
                        ModelPricing(prompt * 1_000_000, completion * 1_000_000)
                    } else null
                    DynamicModel(api.id, api.name.ifBlank { api.id }, pricing)
                }
                .sortedBy { it.id }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse models response", e)
            emptyList()
        }
    }

    private fun loadFromDisk(providerId: String): List<DynamicModel>? {
        val file = cacheFile(providerId)
        if (!file.exists()) return null
        return try {
            val models = parseModels(file.readText())
            if (models.isNotEmpty()) {
                cache[providerId] = models to file.lastModified()
                models
            } else null
        } catch (e: Exception) {
            Log.w(TAG, "[$providerId] Failed to read cache file", e)
            null
        }
    }

    private fun saveToDisk(providerId: String, json: String) {
        try {
            cacheFile(providerId).writeText(json)
        } catch (e: Exception) {
            Log.w(TAG, "[$providerId] Failed to write cache file", e)
        }
    }
}

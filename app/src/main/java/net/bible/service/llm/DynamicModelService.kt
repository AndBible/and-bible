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

import net.bible.sharedlogic.util.currentTimeMillis
import net.bible.sharedcore.platform.AppEnvironment

import net.bible.sharedcore.log.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

import net.bible.sharedcore.llm.http.LlmHttpTransport
import net.bible.sharedcore.llm.http.LlmTimeouts
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.coroutines.cancellation.CancellationException

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
object DynamicModelService : KoinComponent {
    private val transport: LlmHttpTransport by inject()
    private val environment: AppEnvironment get() = getKoin().get()

    data class DynamicModel(
        val id: String,
        val name: String,
        val pricing: ModelPricing?
    ) {
        /** Provider/category prefix, e.g. "anthropic" from "anthropic/claude-sonnet-4". */
        val category: String get() = id.substringBefore('/', "")
    }

    // In-memory cache: providerId → (models, timestampMs)
    private val cache = mutableMapOf<String, Pair<List<DynamicModel>, Long>>()

    private val fileSystem get() = FileSystem.SYSTEM

    private fun cacheFile(providerId: String): Path =
        environment.cacheDir.toPath() / "models_cache_${providerId}.json"

    /** Cache timestamp for a provider (epoch ms), or 0 if never fetched. */
    fun cacheTimestamp(providerId: String): Long {
        cache[providerId]?.let { return it.second }
        return lastModified(cacheFile(providerId)) ?: 0L
    }

    /** Modification time in epoch ms, or null when the file does not exist. */
    private fun lastModified(file: Path): Long? =
        fileSystem.metadataOrNull(file)?.lastModifiedAtMillis

    /** Whether the cache is missing or older than 7 days. */
    fun needsRefresh(providerId: String): Boolean {
        val ts = cacheTimestamp(providerId)
        return ts == 0L || currentTimeMillis() - ts > CACHE_MAX_AGE_MS
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
    suspend fun fetchModels(endpoint: String, apiKey: String, providerId: String): List<DynamicModel>? {
        return try {
            val response = transport.get(buildModelsUrl(endpoint), mapOf("Authorization" to "Bearer $apiKey"), LlmTimeouts.MODELS)
            if (!response.isSuccessful) {
                Log.w(TAG, "[$providerId] API returned ${response.code}")
                return null
            }
            val models = parseModels(response.body)
            if (models.isNotEmpty()) {
                withContext(Dispatchers.IO) { saveToDisk(providerId, response.body) }
                cache[providerId] = models to currentTimeMillis()
            }
            models
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "[$providerId] Failed to fetch models", e)
            null
        }
    }

    /** Whether all disk cache files have been loaded into memory. */
    private var allDiskCachesLoaded = false

    private fun ensureAllDiskCachesLoaded() {
        if (allDiskCachesLoaded) return
        val dir = environment.cacheDir.toPath()
        // java.io.File.listFiles returned null for a missing directory, a non-directory or an I/O error
        // (nothing to load); okio's list throws, so listOrNull keeps that behaviour.
        fileSystem.listOrNull(dir).orEmpty().filter { it.name.startsWith("models_cache_") && it.name.endsWith(".json") }
            .forEach { file ->
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
        if (!fileSystem.exists(file)) return null
        return try {
            val models = parseModels(fileSystem.read(file) { readUtf8() })
            if (models.isNotEmpty()) {
                cache[providerId] = models to (lastModified(file) ?: 0L)
                models
            } else null
        } catch (e: Exception) {
            Log.w(TAG, "[$providerId] Failed to read cache file", e)
            null
        }
    }

    private fun saveToDisk(providerId: String, json: String) {
        try {
            fileSystem.write(cacheFile(providerId)) { writeUtf8(json) }
        } catch (e: Exception) {
            Log.w(TAG, "[$providerId] Failed to write cache file", e)
        }
    }
}

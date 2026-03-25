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

private const val TAG = "OpenRouterModelService"
private const val MODELS_URL = "https://openrouter.ai/api/v1/models"
private const val CACHE_FILENAME = "openrouter_models.json"

private val lenientJson = Json { ignoreUnknownKeys = true }

/** API response wrapper. */
@Serializable
private data class ModelsResponse(val data: List<ApiModel> = emptyList())

@Serializable
private data class ApiModel(
    val id: String,
    val name: String = "",
    val pricing: ApiPricing? = null,
)

@Serializable
private data class ApiPricing(
    val prompt: String = "0",
    val completion: String = "0",
)

/**
 * Fetches and caches model information from the OpenRouter API.
 *
 * Cached data is persisted to a file in the app's internal storage.
 * The file modification time serves as the cache timestamp.
 */
object OpenRouterModelService {

    data class OpenRouterModel(
        val id: String,
        val name: String,
        val pricing: ModelPricing?
    ) {
        /** Provider/category prefix, e.g. "anthropic" from "anthropic/claude-sonnet-4". */
        val category: String get() = id.substringBefore('/', "")
    }

    @Volatile
    private var cachedModels: List<OpenRouterModel>? = null

    @Volatile
    private var cacheTimestampMs: Long = 0

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val cacheFile: File
        get() = File(BibleApplication.application.filesDir, CACHE_FILENAME)

    /** Timestamp (epoch ms) of when the cached model list was last fetched, or 0 if never. */
    val cacheTimestamp: Long get() {
        if (cacheTimestampMs == 0L) {
            val file = cacheFile
            if (file.exists()) {
                cacheTimestampMs = file.lastModified()
            }
        }
        return cacheTimestampMs
    }

    /** Get cached models without making any network calls. Loads from file on first access. */
    fun getCachedModels(): List<OpenRouterModel>? {
        cachedModels?.let { return it }
        val loaded = loadFromDisk()
        if (loaded != null) {
            cachedModels = loaded
        }
        return loaded
    }

    /**
     * Fetch models from the OpenRouter API.
     * @param apiKey Optional API key for authenticated access.
     * @return List of models, or null if the fetch failed.
     */
    suspend fun fetchModels(apiKey: String? = null): List<OpenRouterModel>? = withContext(Dispatchers.IO) {
        try {
            val requestBuilder = Request.Builder().url(MODELS_URL)
            if (!apiKey.isNullOrBlank()) {
                requestBuilder.addHeader("Authorization", "Bearer $apiKey")
            }
            val response = client.newCall(requestBuilder.build()).execute()
            if (!response.isSuccessful) {
                Log.w(TAG, "OpenRouter API returned ${response.code}")
                return@withContext null
            }
            val body = response.body?.string() ?: return@withContext null
            val models = parseModels(body)
            if (models.isNotEmpty()) {
                saveToDisk(body)
                cachedModels = models
            }
            models
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch OpenRouter models", e)
            null
        }
    }

    /** Get pricing for a specific OpenRouter model from the cache. */
    fun getPricingForModel(modelId: String): ModelPricing? {
        val models = getCachedModels() ?: return null
        return models.find { it.id == modelId }?.pricing
    }

    private fun parseModels(json: String): List<OpenRouterModel> {
        return try {
            lenientJson.decodeFromString<ModelsResponse>(json).data
                .filter { it.id.isNotBlank() }
                .map { api ->
                    val prompt = api.pricing?.prompt?.toDoubleOrNull() ?: 0.0
                    val completion = api.pricing?.completion?.toDoubleOrNull() ?: 0.0
                    val pricing = if (prompt > 0 || completion > 0) {
                        ModelPricing(prompt * 1_000_000, completion * 1_000_000)
                    } else null
                    OpenRouterModel(api.id, api.name.ifBlank { api.id }, pricing)
                }
                .sortedBy { it.id }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse OpenRouter models response", e)
            emptyList()
        }
    }

    private fun loadFromDisk(): List<OpenRouterModel>? {
        val file = cacheFile
        if (!file.exists()) return null
        return try {
            val json = file.readText()
            cacheTimestampMs = file.lastModified()
            parseModels(json).ifEmpty { null }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read OpenRouter cache file", e)
            null
        }
    }

    private fun saveToDisk(json: String) {
        try {
            cacheFile.writeText(json)
            cacheTimestampMs = System.currentTimeMillis()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to write OpenRouter cache file", e)
        }
    }
}

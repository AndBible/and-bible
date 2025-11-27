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

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.bible.android.database.translation.TranslationCacheEntry
import net.bible.service.common.CommonUtils
import net.bible.service.db.DatabaseContainer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

private const val TAG = "LlmTranslationService"

object LlmTranslationService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val dao get() = DatabaseContainer.instance.translationDb.translationDao()

    fun computeHash(xmlContent: String, targetLanguage: String, modelId: String): String {
        val input = "$xmlContent|$targetLanguage|$modelId"
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    suspend fun translateXml(xmlContent: String, targetLanguage: String): String {
        val settings = CommonUtils.settings
        if (!settings.llmConfigured) {
            Log.d(TAG, "LLM not configured, returning original content")
            return xmlContent
        }

        val modelId = settings.llmModel
        val hash = computeHash(xmlContent, targetLanguage, modelId)

        // Check cache first
        val cached = dao.getByHash(hash)
        if (cached != null) {
            Log.d(TAG, "Cache hit for hash: $hash")
            dao.updateLastAccessed(hash, System.currentTimeMillis())
            return cached.translatedXml
        }

        // Call LLM API
        Log.d(TAG, "Cache miss, calling LLM API for translation to $targetLanguage")
        return try {
            val translated = callLlmApi(xmlContent, targetLanguage)

            // Save to cache
            dao.insert(TranslationCacheEntry(
                contentHash = hash,
                targetLanguage = targetLanguage,
                modelId = modelId,
                originalXml = xmlContent,
                translatedXml = translated,
                createdAt = System.currentTimeMillis(),
                lastAccessedAt = System.currentTimeMillis()
            ))

            Log.d(TAG, "Translation successful, saved to cache")
            translated
        } catch (e: Exception) {
            Log.e(TAG, "Translation failed", e)
            xmlContent // Return original on error
        }
    }

    private suspend fun callLlmApi(xmlContent: String, targetLanguage: String): String =
        withContext(Dispatchers.IO) {
            val settings = CommonUtils.settings
            val endpoint = "${settings.llmEndpoint}/chat/completions"

            val systemPrompt = """You are a translator. Translate the text content within the XML document to $targetLanguage.
IMPORTANT RULES:
1. Preserve ALL XML tags, attributes, and structure exactly as they are
2. Only translate the text content between tags
3. Do not add any explanations, comments, or markdown formatting
4. Return ONLY the translated XML document, nothing else
5. Keep verse numbers, references, and other metadata unchanged"""

            val requestBody = JSONObject().apply {
                put("model", settings.llmModel)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", systemPrompt)
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", xmlContent)
                    })
                })
                put("temperature", 0.3)
            }

            Log.d(TAG, "Calling LLM API at $endpoint with model ${settings.llmModel}")

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
        Log.i(TAG, "Translation cache cleared")
    }

    fun getCacheCount(): Int = dao.count()

    fun evictOldEntries(maxAgeDays: Int = 30) {
        val threshold = System.currentTimeMillis() - (maxAgeDays * 24 * 60 * 60 * 1000L)
        dao.evictOlderThan(threshold)
        Log.i(TAG, "Evicted cache entries older than $maxAgeDays days")
    }
}

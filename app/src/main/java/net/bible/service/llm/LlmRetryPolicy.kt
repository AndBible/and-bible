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

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.math.pow
import kotlin.random.Random

/**
 * Retry policy for LLM API calls.
 * Handles HTTP 429 (rate limited), 502 (bad gateway), 503 (unavailable), 529 (overloaded)
 * with exponential backoff and retry-after header support.
 *
 * Pure logic class with no Android dependencies — safe for unit testing.
 */
object LlmRetryPolicy {
    const val MAX_RETRIES = 3
    private const val BASE_DELAY_MS = 2000L
    private const val JITTER_FRACTION = 0.3

    private val RETRYABLE_CODES = setOf(429, 502, 503, 529)

    /** Whether the given HTTP status code is retryable. */
    fun isRetryable(code: Int): Boolean = code in RETRYABLE_CODES

    /**
     * Calculate delay before the next retry attempt.
     *
     * If the server provided a retry-after value, uses that (with a minimum of 1 second).
     * Otherwise uses exponential backoff: 2s, 4s, 8s with ±30% jitter.
     *
     * @param attempt Zero-based attempt index (0 = first retry)
     * @param retryAfterSeconds Parsed retry-after header value in seconds, or null
     * @return Delay in milliseconds
     */
    fun calculateDelayMs(attempt: Int, retryAfterSeconds: Double?): Long {
        if (retryAfterSeconds != null && retryAfterSeconds > 0) {
            val baseMs = (retryAfterSeconds * 1000).toLong().coerceAtLeast(1000L)
            return addJitter(baseMs)
        }
        val baseMs = BASE_DELAY_MS * 2.0.pow(attempt).toLong()
        return addJitter(baseMs)
    }

    private fun addJitter(baseMs: Long): Long {
        val jitter = (baseMs * JITTER_FRACTION * (Random.nextDouble() * 2 - 1)).toLong()
        return (baseMs + jitter).coerceAtLeast(1000L)
    }

    /**
     * Parse the HTTP Retry-After header value.
     *
     * Supports two formats per RFC 7231:
     * - Seconds: "30" → 30.0
     * - HTTP-date: "Fri, 28 Feb 2026 12:00:00 GMT" → seconds until that time
     *
     * @return Seconds to wait, or null if the header is missing/unparseable
     */
    fun parseRetryAfterHeader(headerValue: String?): Double? {
        if (headerValue.isNullOrBlank()) return null

        // Try as integer seconds first
        headerValue.trim().toDoubleOrNull()?.let { return it }

        // Try as HTTP-date (RFC 7231 format)
        return try {
            val formatter = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US)
            formatter.timeZone = TimeZone.getTimeZone("GMT")
            val date = formatter.parse(headerValue.trim()) ?: return null
            val delayMs = date.time - System.currentTimeMillis()
            if (delayMs > 0) delayMs / 1000.0 else null
        } catch (_: Exception) {
            null
        }
    }
}

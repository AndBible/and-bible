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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [LlmRetryPolicy].
 * Pure JUnit — no Android or Robolectric dependencies.
 */
class LlmRetryPolicyTest {

    // --- isRetryable ---

    @Test
    fun `isRetryable returns true for 429`() {
        assertTrue(LlmRetryPolicy.isRetryable(429))
    }

    @Test
    fun `isRetryable returns true for 502`() {
        assertTrue(LlmRetryPolicy.isRetryable(502))
    }

    @Test
    fun `isRetryable returns true for 503`() {
        assertTrue(LlmRetryPolicy.isRetryable(503))
    }

    @Test
    fun `isRetryable returns true for 529`() {
        assertTrue(LlmRetryPolicy.isRetryable(529))
    }

    @Test
    fun `isRetryable returns false for 200`() {
        assertFalse(LlmRetryPolicy.isRetryable(200))
    }

    @Test
    fun `isRetryable returns false for 400`() {
        assertFalse(LlmRetryPolicy.isRetryable(400))
    }

    @Test
    fun `isRetryable returns false for 500`() {
        assertFalse(LlmRetryPolicy.isRetryable(500))
    }

    @Test
    fun `isRetryable returns false for 401`() {
        assertFalse(LlmRetryPolicy.isRetryable(401))
    }

    // --- parseRetryAfterHeader ---

    @Test
    fun `parseRetryAfterHeader returns null for null`() {
        assertNull(LlmRetryPolicy.parseRetryAfterHeader(null))
    }

    @Test
    fun `parseRetryAfterHeader returns null for empty string`() {
        assertNull(LlmRetryPolicy.parseRetryAfterHeader(""))
    }

    @Test
    fun `parseRetryAfterHeader returns null for blank string`() {
        assertNull(LlmRetryPolicy.parseRetryAfterHeader("   "))
    }

    @Test
    fun `parseRetryAfterHeader parses integer seconds`() {
        assertEquals(30.0, LlmRetryPolicy.parseRetryAfterHeader("30")!!, 0.001)
    }

    @Test
    fun `parseRetryAfterHeader parses decimal seconds`() {
        assertEquals(1.5, LlmRetryPolicy.parseRetryAfterHeader("1.5")!!, 0.001)
    }

    @Test
    fun `parseRetryAfterHeader parses seconds with whitespace`() {
        assertEquals(60.0, LlmRetryPolicy.parseRetryAfterHeader("  60  ")!!, 0.001)
    }

    @Test
    fun `parseRetryAfterHeader returns null for garbage`() {
        assertNull(LlmRetryPolicy.parseRetryAfterHeader("not-a-date-or-number"))
    }

    // --- calculateDelayMs ---

    @Test
    fun `calculateDelayMs with retryAfter uses server value`() {
        val delay = LlmRetryPolicy.calculateDelayMs(0, 10.0)
        // 10s = 10000ms, with ±30% jitter → 7000..13000
        assertTrue("Expected delay around 10000ms, got $delay", delay in 7000..13000)
    }

    @Test
    fun `calculateDelayMs with retryAfter floors at 1 second`() {
        val delay = LlmRetryPolicy.calculateDelayMs(0, 0.1)
        // 0.1s → 1000ms minimum, with jitter → 700..1300
        assertTrue("Expected delay >= 700ms, got $delay", delay >= 700)
    }

    @Test
    fun `calculateDelayMs attempt 0 gives around 2 seconds`() {
        val delay = LlmRetryPolicy.calculateDelayMs(0, null)
        // 2000ms ± 30% → 1400..2600
        assertTrue("Expected delay in 1000..2600, got $delay", delay in 1000..2600)
    }

    @Test
    fun `calculateDelayMs attempt 1 gives around 4 seconds`() {
        val delay = LlmRetryPolicy.calculateDelayMs(1, null)
        // 4000ms ± 30% → 2800..5200
        assertTrue("Expected delay in 2800..5200, got $delay", delay in 2800..5200)
    }

    @Test
    fun `calculateDelayMs attempt 2 gives around 8 seconds`() {
        val delay = LlmRetryPolicy.calculateDelayMs(2, null)
        // 8000ms ± 30% → 5600..10400
        assertTrue("Expected delay in 5600..10400, got $delay", delay in 5600..10400)
    }

    @Test
    fun `calculateDelayMs always returns at least 1 second`() {
        // Even with negative retryAfter
        val delay = LlmRetryPolicy.calculateDelayMs(0, -5.0)
        assertTrue("Expected delay >= 1000ms, got $delay", delay >= 1000)
    }
}

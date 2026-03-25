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

import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class LlmCostTrackingTest {

    // --- LlmUsage ---

    @Test
    fun totalTokensSumsAllFields() {
        val usage = LlmUsage(inputTokens = 100, outputTokens = 50, cacheCreationTokens = 20, cacheReadTokens = 10)
        assertEquals(180L, usage.totalTokens)
    }

    @Test
    fun totalTokensDefaultsToZero() {
        val usage = LlmUsage()
        assertEquals(0L, usage.totalTokens)
    }

    @Test
    fun plusCombinesUsages() {
        val a = LlmUsage(inputTokens = 100, outputTokens = 50, cacheCreationTokens = 10, cacheReadTokens = 5)
        val b = LlmUsage(inputTokens = 200, outputTokens = 100, cacheCreationTokens = 30, cacheReadTokens = 15)
        val sum = a + b
        assertEquals(300L, sum.inputTokens)
        assertEquals(150L, sum.outputTokens)
        assertEquals(40L, sum.cacheCreationTokens)
        assertEquals(20L, sum.cacheReadTokens)
    }

    @Test
    fun plusWithZeroUsage() {
        val a = LlmUsage(inputTokens = 100, outputTokens = 50)
        val zero = LlmUsage()
        val sum = a + zero
        assertEquals(100L, sum.inputTokens)
        assertEquals(50L, sum.outputTokens)
        assertEquals(0L, sum.cacheCreationTokens)
        assertEquals(0L, sum.cacheReadTokens)
    }

    // --- LlmPricing.estimateCost ---

    @Test
    fun estimateCostKnownModel() {
        val usage = LlmUsage(inputTokens = 1_000_000, outputTokens = 1_000_000)
        val cost = LlmPricing.estimateCost(usage, "gpt-4o-mini")
        assertNotNull(cost)
        // gpt-4o-mini: input=0.15, output=0.60 per million
        assertEquals(0.75, cost!!, 0.001)
    }

    @Test
    fun estimateCostUnknownModelReturnsNull() {
        val usage = LlmUsage(inputTokens = 1000, outputTokens = 500)
        val cost = LlmPricing.estimateCost(usage, "nonexistent-model-xyz")
        assertNull(cost)
    }

    @Test
    fun estimateCostZeroTokens() {
        val usage = LlmUsage()
        val cost = LlmPricing.estimateCost(usage, "gpt-4o-mini")
        assertNotNull(cost)
        assertEquals(0.0, cost!!, 0.0001)
    }

    @Test
    fun estimateCostWithCacheTokens() {
        // gemini-2.5-flash: input=0.15, output=0.60, cacheCreate=0.15, cacheRead=0.0375
        val usage = LlmUsage(
            inputTokens = 1_000_000,
            outputTokens = 1_000_000,
            cacheCreationTokens = 1_000_000,
            cacheReadTokens = 1_000_000
        )
        val cost = LlmPricing.estimateCost(usage, "gemini-2.5-flash")
        assertNotNull(cost)
        // 0.15 + 0.60 + 0.15 + 0.0375 = 0.9375
        assertEquals(0.9375, cost!!, 0.001)
    }

    // --- LlmPricing.isKnownModel ---

    @Test
    fun isKnownModelTrue() {
        assertTrue(LlmPricing.isKnownModel("gpt-4o-mini"))
    }

    @Test
    fun isKnownModelFalse() {
        assertFalse(LlmPricing.isKnownModel("nonexistent-model"))
    }

    // --- formatCost ---

    @Test
    fun formatCostSmallAmount() {
        assertEquals("\$0.005", LlmCostTracker.formatCost(0.005))
    }

    @Test
    fun formatCostNormalAmount() {
        assertEquals("\$1.50", LlmCostTracker.formatCost(1.50))
    }

    @Test
    fun formatCostZero() {
        assertEquals("\$0.00", LlmCostTracker.formatCost(0.0))
    }

    @Test
    fun formatCostExactlyOneCent() {
        assertEquals("\$0.01", LlmCostTracker.formatCost(0.01))
    }

    // --- formatUsageSummary ---

    @Test
    fun formatUsageSummaryKnownModel() {
        val usage = LlmUsage(inputTokens = 1000, outputTokens = 500)
        val summary = LlmCostTracker.formatUsageSummary(usage, "gpt-4o-mini")
        assertTrue(summary.contains("1000 in"))
        assertTrue(summary.contains("500 out"))
        assertTrue(summary.contains("~"))
        assertTrue(summary.contains("$"))
    }

    @Test
    fun formatUsageSummaryUnknownModel() {
        val usage = LlmUsage(inputTokens = 1000, outputTokens = 500)
        val summary = LlmCostTracker.formatUsageSummary(usage, "unknown-model")
        assertEquals("1000 in / 500 out", summary)
    }
}

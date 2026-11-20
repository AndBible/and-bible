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
package net.bible.android.control.widget

/**
 * Local time zone as a port: the offset from UTC that applies at an instant (DST included). Android and the desktop
 * adapt `java.util.TimeZone`, iOS `NSTimeZone`; tests supply rules directly. Kept apart from a datetime library on
 * purpose: `kotlinx-datetime` would need core library desugaring in `:app` for minSdk 23.
 */
fun interface LocalZone {
    /** Milliseconds to add to [epochMs] (UTC) to get local wall-clock time. */
    fun offsetMsAt(epochMs: Long): Long
}

/**
 * Pure period and selection maths of the Verse widget, so a widget shows the same verse for a whole period (stable
 * across redraws and reboots) and changes exactly once at the boundary.
 */
object VerseSelector {
    private const val HOUR_MS = 3_600_000L
    private const val DAY_MS = 24 * HOUR_MS
    private const val SLOT_HOURS = 3

    private fun localMs(nowEpochMs: Long, zone: LocalZone) = nowEpochMs + zone.offsetMsAt(nowEpochMs)

    /**
     * The index of the period containing [nowEpochMs]: DAILY is the local epoch day, EVERY_3_HOURS is
     * `epochDay * 8 + localHour / 3` and ON_SHUFFLE is always 0.
     */
    fun periodIndex(nowEpochMs: Long, zone: LocalZone, frequency: VerseChangeFrequency): Long {
        val local = localMs(nowEpochMs, zone)
        val epochDay = Math2.floorDiv(local, DAY_MS)
        return when (frequency) {
            VerseChangeFrequency.DAILY -> epochDay
            VerseChangeFrequency.EVERY_3_HOURS -> {
                val hour = Math2.floorDiv(Math2.floorMod(local, DAY_MS), HOUR_MS)
                epochDay * (24 / SLOT_HOURS) + hour / SLOT_HOURS
            }
            VerseChangeFrequency.ON_SHUFFLE -> 0L
        }
    }

    /** UTC epoch ms at which the next period starts, or null for ON_SHUFFLE. Always greater than [nowEpochMs]. */
    fun nextPeriodStart(nowEpochMs: Long, zone: LocalZone, frequency: VerseChangeFrequency): Long? {
        val step = when (frequency) {
            VerseChangeFrequency.DAILY -> DAY_MS
            VerseChangeFrequency.EVERY_3_HOURS -> SLOT_HOURS * HOUR_MS
            VerseChangeFrequency.ON_SHUFFLE -> return null
        }
        val local = localMs(nowEpochMs, zone)
        var boundaryLocal = (Math2.floorDiv(local, step) + 1) * step
        // A boundary that does not exist (spring-forward gap) resolves to the instant the clock jumps over it.
        repeat(3) {
            val t = localToUtc(boundaryLocal, zone)
            if (t > nowEpochMs) return t
            boundaryLocal += step
        }
        return localToUtc(boundaryLocal, zone)
    }

    /**
     * Converts a local wall-clock time to UTC, using the offsets in force a day before and a day after (a zone changes
     * its offset at most once in between). A time that happens twice (clocks back) resolves to its first occurrence;
     * one that does not exist (clocks forward) resolves to the instant the clock jumps over it.
     */
    private fun localToUtc(localMs: Long, zone: LocalZone): Long {
        val before = localMs - zone.offsetMsAt(localMs - DAY_MS)
        val after = localMs - zone.offsetMsAt(localMs + DAY_MS)
        val valid = listOf(before, after).filter { it + zone.offsetMsAt(it) == localMs }
        return if (valid.isNotEmpty()) valid.min() else maxOf(before, after)
    }

    /**
     * Index into a pool of [poolSize] for this widget, period and shuffle count, or null for an empty pool. A SplitMix64
     * hash of (seed, period, shuffle) modulo the pool size, so picks spread evenly and are deterministic.
     */
    fun pick(poolSize: Int, seed: Long, periodIndex: Long, shuffleCount: Int): Int? {
        if (poolSize <= 0) return null
        var h = splitMix(seed)
        h = splitMix(h xor periodIndex)
        h = splitMix(h xor (shuffleCount.toLong() * -0x61c8864680b583ebL))
        return ((h ushr 1) % poolSize).toInt()
    }

    private fun splitMix(x: Long): Long {
        var z = x + -0x61c8864680b583ebL
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
        return z xor (z ushr 31)
    }
}

/** Floor division and modulo for Long in common code (negative local times before 1970 stay correct). */
private object Math2 {
    fun floorDiv(a: Long, b: Long): Long { val q = a / b; return if ((a % b != 0L) && ((a < 0) != (b < 0))) q - 1 else q }
    fun floorMod(a: Long, b: Long): Long = a - floorDiv(a, b) * b
}

package net.bible.sharedlogic.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class IsoUtcTimestampsTest {
    @Test fun formatEpoch() = assertEquals("1970-01-01T00:00:00Z", IsoUtcTimestamps.format(0))

    @Test fun formatKnownInstant() =
        assertEquals("2024-01-02T03:04:05Z", IsoUtcTimestamps.format(1_704_164_645_000))

    @Test fun parseKnownInstant() =
        assertEquals(1_704_164_645_000, IsoUtcTimestamps.parseLenient("2024-01-02T03:04:05Z"))

    @Test fun dayOverflowRollsOver() = assertEquals(
        IsoUtcTimestamps.parseLenient("2024-03-01T00:00:00Z"),
        IsoUtcTimestamps.parseLenient("2024-02-30T00:00:00Z"),
    )

    @Test fun monthOverflowRollsOver() = assertEquals(
        IsoUtcTimestamps.parseLenient("2025-01-01T00:00:00Z"),
        IsoUtcTimestamps.parseLenient("2024-13-01T00:00:00Z"),
    )

    @Test fun hourOverflowRollsOver() = assertEquals(
        IsoUtcTimestamps.parseLenient("2024-01-03T00:00:00Z"),
        IsoUtcTimestamps.parseLenient("2024-01-02T24:00:00Z"),
    )

    @Test fun trailingTextIgnored() = assertEquals(
        1_704_164_645_000,
        IsoUtcTimestamps.parseLenient("2024-01-02T03:04:05Zjunk"),
    )

    @Test fun unparseableThrows() {
        val e = assertFailsWith<IllegalArgumentException> { IsoUtcTimestamps.parseLenient("yesterday") }
        assertEquals("Unparseable date: \"yesterday\"", e.message)
    }

    @Test fun roundTripBeforeEpoch() {
        val ms = -86_400_000L * 400 + 7000
        assertEquals(ms, IsoUtcTimestamps.parseLenient(IsoUtcTimestamps.format(ms)))
    }
}

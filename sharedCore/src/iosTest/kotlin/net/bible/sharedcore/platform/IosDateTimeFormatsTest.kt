package net.bible.sharedcore.platform

import platform.Foundation.NSTimeZone
import platform.Foundation.timeZoneWithName
import platform.Foundation.setDefaultTimeZone
import platform.Foundation.defaultTimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IosDateTimeFormatsTest {
    private val formats = IosDateTimeFormats()

    @Test fun patternFormatsInTheGivenTimeZone() {
        val saved = NSTimeZone.defaultTimeZone
        try {
            NSTimeZone.setDefaultTimeZone(NSTimeZone.timeZoneWithName("UTC")!!)
            // 2026-02-01T10:30:00Z
            assertEquals("20260201", formats.pattern("yyyyMMdd", 1_769_941_800_000L))
            assertEquals("2026-02-01 10:30", formats.pattern("yyyy-MM-dd HH:mm", 1_769_941_800_000L))
        } finally {
            NSTimeZone.setDefaultTimeZone(saved)
        }
    }

    @Test fun shortFormatsAreNonEmpty() {
        assertTrue(formats.shortDate(1_769_941_800_000L).isNotEmpty())
        assertTrue(formats.shortTime(1_769_941_800_000L).isNotEmpty())
    }

    @Test fun relativeSpanIsNonEmpty() {
        val now = 1_769_941_800_000L
        assertTrue(formats.relativeTimeSpan(now - 3_600_000L, now).isNotEmpty())
    }
}

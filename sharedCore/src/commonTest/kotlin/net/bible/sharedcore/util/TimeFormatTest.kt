package net.bible.sharedcore.util

import kotlin.test.Test
import kotlin.test.assertEquals

class TimeFormatTest {
    @Test fun formatsTheSameAsThePreviousCommonUtilsHelper() {
        listOf(0L to "00:00", 59L to "00:59", 61L to "01:01", 3600L to "1:00:00", 3725L to "1:02:05")
            .forEach { (seconds, expected) -> assertEquals(expected, formatHoursMinsSecs(seconds)) }
    }
}

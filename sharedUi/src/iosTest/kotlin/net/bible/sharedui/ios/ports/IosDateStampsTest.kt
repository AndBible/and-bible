package net.bible.sharedui.ios.ports

import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarIdentifierGregorian
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.NSTimeZone
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.defaultTimeZone
import platform.Foundation.localeWithLocaleIdentifier
import platform.Foundation.setDefaultTimeZone
import platform.Foundation.timeZoneWithName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Pins the date stamps moved out of the hoisted ports to the NSDateFormatter output they had before. */
class IosDateStampsTest {
    private val instant = 1_774_682_232_345L

    private fun inHelsinki(block: () -> Unit) {
        val saved = NSTimeZone.defaultTimeZone
        try {
            NSTimeZone.setDefaultTimeZone(NSTimeZone.timeZoneWithName("Europe/Helsinki")!!)
            block()
        } finally { NSTimeZone.setDefaultTimeZone(saved) }
    }

    @Test fun aiLogTimestampMatchesTheOriginalFormatterForEveryLocale() = inHelsinki {
        for (tag in listOf("en", "fi", "ar", "th", "fa")) {
            val expected = NSDateFormatter().apply {
                locale = NSLocale.localeWithLocaleIdentifier(tag)
                calendar = NSCalendar(calendarIdentifier = NSCalendarIdentifierGregorian)
                timeZone = NSTimeZone.defaultTimeZone
                dateFormat = "yyyy-MM-dd HH:mm"
            }.stringFromDate(NSDate.dateWithTimeIntervalSince1970(instant / 1000.0))
            assertEquals(expected, iosAiLogTimestamp(tag, instant), tag)
        }
        assertEquals("2026-03-28 09:17", iosAiLogTimestamp("en", instant))
    }

    @Test fun bookmarkCsvTimestampHasTheFileNameShape() {
        val stamp = iosBookmarkCsvTimestamp()
        assertTrue(Regex("""\d{4}-\d{2}-\d{2}_\d{2}-\d{2}""").matches(stamp), stamp)
    }
}

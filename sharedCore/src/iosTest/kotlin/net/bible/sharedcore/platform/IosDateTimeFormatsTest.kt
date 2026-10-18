package net.bible.sharedcore.platform

import platform.Foundation.NSLocale
import platform.Foundation.NSTimeZone
import platform.Foundation.defaultTimeZone
import platform.Foundation.localeWithLocaleIdentifier
import platform.Foundation.setDefaultTimeZone
import platform.Foundation.timeZoneWithName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IosDateTimeFormatsTest {
    private val formats = IosDateTimeFormats()
    private val fixedInstant = 1_769_941_800_000L // 2026-02-01T10:30:00Z

    private fun inUtc(block: () -> Unit) {
        val saved = NSTimeZone.defaultTimeZone
        try {
            NSTimeZone.setDefaultTimeZone(NSTimeZone.timeZoneWithName("UTC")!!)
            block()
        } finally {
            NSTimeZone.setDefaultTimeZone(saved)
        }
    }

    @Test fun patternFormatsInTheDefaultTimeZone() = inUtc {
        assertEquals("20260201", formats.pattern("yyyyMMdd", fixedInstant))
        assertEquals("2026-02-01 10:30", formats.pattern("yyyy-MM-dd HH:mm", fixedInstant))
    }

    @Test fun patternIsGregorianWithLatinDigitsOnBuddhistAndArabicLocales() = inUtc {
        for (id in listOf("th_TH@calendar=buddhist;numbers=thai", "ar_EG", "fa_IR@calendar=persian")) {
            val f = IosDateTimeFormats { NSLocale.localeWithLocaleIdentifier(id) }
            assertEquals("20260201103000", f.pattern("yyyyMMddHHmmss", fixedInstant), id)
        }
    }

    @Test fun patternKeepsLocalisedNames() = inUtc {
        assertEquals("sunnuntai", IosDateTimeFormats { NSLocale.localeWithLocaleIdentifier("fi_FI") }.pattern("EEEE", fixedInstant))
        assertEquals("Sunday", IosDateTimeFormats { NSLocale.localeWithLocaleIdentifier("en_US") }.pattern("EEEE", fixedInstant))
    }

    @Test fun shortFormatsFollowTheLocale() = inUtc {
        val us = IosDateTimeFormats { NSLocale.localeWithLocaleIdentifier("en_US") }
        assertEquals("2/1/26", us.shortDate(fixedInstant))
        assertTrue(us.shortTime(fixedInstant).startsWith("10:30"))
    }

    @Test fun relativeSpanIsAbbreviatedEnglish() = inUtc {
        val us = IosDateTimeFormats { NSLocale.localeWithLocaleIdentifier("en_US") }
        val text = us.relativeTimeSpan(fixedInstant - 3_600_000L, fixedInstant)
        assertTrue(text.contains("1") && text.contains("hr"), text)
    }
}

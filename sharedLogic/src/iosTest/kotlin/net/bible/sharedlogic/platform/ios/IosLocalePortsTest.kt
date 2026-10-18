package net.bible.sharedlogic.platform.ios

import net.bible.sharedlogic.locale.Iso639
import org.crosswire.common.util.KLocale
import platform.Foundation.NSCalendar
import platform.Foundation.NSDate
import platform.Foundation.NSDateComponents
import platform.Foundation.dateWithTimeIntervalSince1970
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class IosLocalePortsTest {
    private val calendar = IosLocalCalendar()

    private fun comps(ms: Long) = NSCalendar.currentCalendar.components(
        platform.Foundation.NSCalendarUnitYear or platform.Foundation.NSCalendarUnitMonth or
            platform.Foundation.NSCalendarUnitDay or platform.Foundation.NSCalendarUnitHour or
            platform.Foundation.NSCalendarUnitMinute,
        fromDate = NSDate.dateWithTimeIntervalSince1970(ms / 1000.0),
    )

    @Test fun parseMonthDayYearIsLocalMidnight() {
        val c = comps(calendar.parseMonthDayYear("Feb-1/2026"))
        assertEquals(2026L, c.year); assertEquals(2L, c.month); assertEquals(1L, c.day)
        assertEquals(0L, c.hour); assertEquals(0L, c.minute)
    }

    @Test fun parseMonthDayYearGarbageThrows() {
        assertFailsWith<IllegalArgumentException> { calendar.parseMonthDayYear("garbage") }
    }

    @Test fun addDaysKeepsWallClockHour() {
        val start = calendar.parseMonthDayYear("Mar-1/2026") + 9 * 3_600_000L
        val later = calendar.addDays(start, 60)
        assertEquals(9L, comps(later).hour)
        assertEquals(9L, comps(calendar.plusLocalDays(start, 200)).hour)
        assertEquals(0L, comps(calendar.startOfLocalDay(later)).hour)
    }

    @Test fun monthAndShortName() {
        val ms = calendar.parseMonthDayYear("Feb-1/2026")
        assertEquals(1, calendar.localMonth(ms))
        assertTrue(calendar.shortMonthName(ms).isNotEmpty())
        assertEquals(2026, calendar.currentYear().coerceAtLeast(2026))
    }

    @Test fun startOfLocalWeekIsNotAfterDayAndWithinSevenDays() {
        val ms = calendar.parseMonthDayYear("Feb-11/2026")
        val week = calendar.startOfLocalWeek(ms)
        assertTrue(week <= ms && ms - week < 7 * 86_400_000L + 3_600_000L)
        assertEquals(week, calendar.startOfLocalDay(week))
    }

    @Test fun sentenceBreaker() {
        assertEquals(5, IosSentenceBreaker.following("A b. C d.", 0))
        assertEquals(5, IosSentenceBreaker.preceding("A b. C d.", 9))
        assertEquals(-1, IosSentenceBreaker.following("A b. C d.", 9))
        assertEquals(-1, IosSentenceBreaker.preceding("A b. C d.", 0))
        assertFailsWith<IllegalArgumentException> { IosSentenceBreaker.following("A b.", 10) }
    }

    @Test fun platformLocalesMapsModernCodesToLegacy() {
        assertEquals("iw", IosPlatformLocales { "he" }.defaultLanguage)
        assertEquals("in", IosPlatformLocales { "id" }.defaultLanguage)
        assertEquals("ji", IosPlatformLocales { "yi" }.defaultLanguage)
        assertEquals("fi", IosPlatformLocales { "fi" }.defaultLanguage)
        assertEquals(Iso639.iso3ByIso2, IosPlatformLocales { "fi" }.iso3ByIso2())
    }

    @Test fun localeProviderSerbianLatinException() {
        assertEquals(KLocale("sr", "LT"), IosLocaleProvider { "sr-Latn-RS" }.getUserLocale())
        assertEquals(KLocale("sr", "RS"), IosLocaleProvider { "sr-Cyrl-RS" }.getUserLocale())
        assertEquals(KLocale("pt", "BR"), IosLocaleProvider { "pt-BR" }.getUserLocale())
        assertEquals(KLocale("fi"), IosLocaleProvider { "fi" }.getUserLocale())
    }

    @Test fun localeProviderOverrideWins() {
        val p = IosLocaleProvider { "sr-Latn" }
        p.override = KLocale("de", "AT")
        assertEquals(KLocale("de", "AT"), p.getUserLocale())
    }

    @Test fun localeSupport() {
        val s = IosLocaleSupport
        assertEquals("en_GB", s.of("en", "GB").asString)
        assertEquals("fi", s.parse("fi").asString)
        assertEquals("en_GB", s.parse("en_GB").asString)
        assertEquals("suomi", s.displayLanguage("fi"))
        assertEquals("1", s.formatFixed(1.0, 0))
        assertEquals(4, s.formatFixed(1.5, 2).length)
        assertTrue(s.formatGrouped(1234567).length >= 9)
        assertTrue(s.default.language.isNotEmpty())
    }
}

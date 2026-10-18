package net.bible.sharedlogic.platform.ios

import net.bible.sharedlogic.locale.Iso639
import org.crosswire.common.util.KLocale
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarIdentifierGregorian
import platform.Foundation.NSCalendarUnitDay
import platform.Foundation.NSCalendarUnitHour
import platform.Foundation.NSCalendarUnitMinute
import platform.Foundation.NSCalendarUnitMonth
import platform.Foundation.NSCalendarUnitYear
import platform.Foundation.NSDate
import platform.Foundation.NSLocale
import platform.Foundation.NSTimeZone
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.defaultTimeZone
import platform.Foundation.localeWithLocaleIdentifier
import platform.Foundation.setDefaultTimeZone
import platform.Foundation.timeZoneWithName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class IosLocalePortsTest {
    private val calendar = IosLocalCalendar()

    private fun gregorian() = NSCalendar(calendarIdentifier = NSCalendarIdentifierGregorian).apply {
        timeZone = NSTimeZone.defaultTimeZone
    }

    private fun comps(ms: Long) = gregorian().components(
        NSCalendarUnitYear or NSCalendarUnitMonth or NSCalendarUnitDay or NSCalendarUnitHour or NSCalendarUnitMinute,
        fromDate = NSDate.dateWithTimeIntervalSince1970(ms / 1000.0),
    )

    private fun inZone(name: String, block: () -> Unit) {
        val saved = NSTimeZone.defaultTimeZone
        try {
            NSTimeZone.setDefaultTimeZone(NSTimeZone.timeZoneWithName(name)!!)
            block()
        } finally {
            NSTimeZone.setDefaultTimeZone(saved)
        }
    }

    private fun locale(id: String) = NSLocale.localeWithLocaleIdentifier(id)

    @Test fun parseMonthDayYearIsLocalMidnight() {
        val c = comps(calendar.parseMonthDayYear("Feb-1/2026"))
        assertEquals(2026L, c.year); assertEquals(2L, c.month); assertEquals(1L, c.day)
        assertEquals(0L, c.hour); assertEquals(0L, c.minute)
    }

    @Test fun parseMonthDayYearGarbageThrows() {
        assertFailsWith<IllegalArgumentException> { calendar.parseMonthDayYear("garbage") }
    }

    @Test fun parseMonthDayYearFallsBackToLocaleMonthNamesInGregorian() {
        // Finnish "helmik." is not a US month name; a Buddhist device calendar must not shift the year to 2569.
        val ms = IosLocalCalendar { locale("fi_FI@calendar=buddhist") }.parseMonthDayYear("helmik.-1/2026")
        val c = comps(ms)
        assertEquals(2026L, c.year); assertEquals(2L, c.month); assertEquals(1L, c.day)
        // Full names and a bare prefix reach the same month.
        assertEquals(2L, comps(IosLocalCalendar { locale("fi_FI") }.parseMonthDayYear("helmikuu-1/2026")).month)
    }

    @Test fun parseByMonthSymbolsAcceptsABarePrefix() {
        val c = comps(IosLocalCalendar { locale("fi_FI") }.parseMonthDayYear("hel-1/2026"))
        assertEquals(2026L, c.year); assertEquals(2L, c.month); assertEquals(1L, c.day)
    }

    @Test fun parseByMonthSymbolsRejectsAnAmbiguousPrefix() {
        // Czech "cerven" (June) and "cervenec" (July) share the prefix "cerve", so it names no single month.
        assertEquals(6L, comps(IosLocalCalendar { locale("cs_CZ") }.parseMonthDayYear("\u010derven-1/2026")).month)
        assertFailsWith<IllegalArgumentException> { IosLocalCalendar { locale("cs_CZ") }.parseMonthDayYear("\u010derve-1/2026") }
    }

    @Test fun parseByMonthSymbolsRejectsAnImpossibleDayInsteadOfRollingOver() {
        val fi = IosLocalCalendar { locale("fi_FI") }
        assertFailsWith<IllegalArgumentException> { fi.parseMonthDayYear("helmik.-31/2026") }
        assertFailsWith<IllegalArgumentException> { fi.parseMonthDayYear("helmik.-0/2026") }
        assertFailsWith<IllegalArgumentException> { fi.parseMonthDayYear("helmik.-32/2026") }
    }

    @Test fun currentYearIsGregorianEvenOnABuddhistCalendarLocale() {
        val expected = gregorian().component(NSCalendarUnitYear, fromDate = NSDate()).toInt()
        assertEquals(expected, calendar.currentYear())
        assertEquals(expected, IosLocalCalendar { locale("th_TH@calendar=buddhist") }.currentYear())
    }

    @Test fun addDaysKeepsWallClockHourAcrossSpringForward() = inZone("America/New_York") {
        // DST starts 2026-03-08 in New York; 2026-03-07 09:00 EST + 2 days is 2026-03-09 09:00 EDT (47 hours).
        val start = calendar.parseMonthDayYear("Mar-7/2026") + 9 * HOUR
        val later = calendar.addDays(start, 2)
        assertEquals(9L, comps(later).hour)
        assertEquals(9L, comps(later).day)
        assertEquals(47 * HOUR, later - start)
        assertNotEquals(48 * HOUR, later - start)
        assertEquals(47 * HOUR, calendar.plusLocalDays(start, 2) - start)
    }

    @Test fun addDaysKeepsWallClockHourAcrossFallBack() = inZone("Europe/Helsinki") {
        // DST ends 2026-10-25 in Helsinki; 2026-10-24 09:00 + 2 days is 2026-10-26 09:00 (49 hours).
        val start = calendar.parseMonthDayYear("Oct-24/2026") + 9 * HOUR
        val later = calendar.addDays(start, 2)
        assertEquals(9L, comps(later).hour)
        assertEquals(49 * HOUR, later - start)
        assertEquals(0L, comps(calendar.startOfLocalDay(later)).hour)
        assertEquals(26L, comps(calendar.startOfDay(later)).day)
    }

    @Test fun plusWeeksAcrossDstKeepsHour() = inZone("America/New_York") {
        val start = calendar.parseMonthDayYear("Mar-5/2026") + 9 * HOUR
        val later = calendar.plusWeeks(start, 1)
        assertEquals(9L, comps(later).hour)
        assertEquals(12L, comps(later).day)
        assertEquals(7 * 24 * HOUR - HOUR, later - start)
    }

    @Test fun monthIsZeroBasedAndNamed() {
        val ms = calendar.parseMonthDayYear("Feb-1/2026")
        assertEquals(1, calendar.localMonth(ms))
        assertEquals("Feb", IosLocalCalendar { locale("en_US") }.shortMonthName(ms))
        // "helmik." or "helmikuu" depending on the runtime's CLDR data; always Finnish, never English "Feb".
        IosLocalCalendar { locale("fi_FI") }.shortMonthName(ms).let { assertTrue(it.lowercase().startsWith("helmi"), it) }
    }

    @Test fun startOfLocalWeekFollowsLocaleFirstWeekday() {
        // 2026-02-11 is a Wednesday. US weeks start on Sunday (Feb 8), Finnish weeks on Monday (Feb 9).
        val ms = calendar.parseMonthDayYear("Feb-11/2026") + 15 * HOUR
        val us = IosLocalCalendar { locale("en_US") }
        val fi = IosLocalCalendar { locale("fi_FI") }
        assertEquals(8L, comps(us.startOfLocalWeek(ms)).day)
        assertEquals(9L, comps(fi.startOfLocalWeek(ms)).day)
        assertEquals(0L, comps(fi.startOfLocalWeek(ms)).hour)
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
        assertEquals(KLocale("pt", "BR"), IosLocaleProvider { "pt_BR@calendar=gregorian" }.getUserLocale())
    }

    @Test fun localeProviderOverrideWins() {
        val p = IosLocaleProvider { "sr-Latn" }
        p.override = KLocale("de", "AT")
        assertEquals(KLocale("de", "AT"), p.getUserLocale())
    }

    @Test fun localeSupportValues() {
        val s = IosLocaleSupportImpl { locale("en_US") }
        assertEquals("en_GB", s.of("en", "GB").asString)
        assertEquals("fi", s.parse("fi").asString)
        assertEquals("en_GB", s.parse("en_GB").asString)
        assertEquals("suomi", s.displayLanguage("fi"))
        assertEquals("en", s.default.language)
        assertEquals("US", s.default.country)
        assertEquals("en_US", s.default.asString)
        assertEquals("English", s.defaultDisplayLanguage())
    }

    @Test fun numberFormattingUsesLocaleSeparatorsAndHalfUp() {
        val us = IosLocaleSupportImpl { locale("en_US") }
        assertEquals("1", us.formatFixed(1.0, 0))
        assertEquals("1.50", us.formatFixed(1.5, 2))
        assertEquals("1234.5", us.formatFixed(1234.5, 1))
        assertEquals("1,234,567", us.formatGrouped(1234567))
        // Java's %.2f rounds half up (0.13), NSNumberFormatter's default is half even (0.12).
        assertEquals("0.13", us.formatFixed(0.125, 2))
        assertEquals("0.12", us.formatFixed(0.124, 2))
        val de = IosLocaleSupportImpl { locale("de_DE") }
        assertEquals("1,50", de.formatFixed(1.5, 2))
        assertEquals("1.234.567", de.formatGrouped(1234567))
    }

    private companion object {
        const val HOUR = 3_600_000L
    }
}

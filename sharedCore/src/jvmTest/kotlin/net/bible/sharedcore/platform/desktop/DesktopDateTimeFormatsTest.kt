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
package net.bible.sharedcore.platform.desktop

import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopDateTimeFormatsTest {
    private val zone = ZoneId.of("Europe/Helsinki")
    private val moment = ZonedDateTime.of(2026, 3, 8, 14, 5, 9, 0, zone).toInstant().toEpochMilli()
    private fun formats(tag: String) = DesktopDateTimeFormats({ Locale.forLanguageTag(tag) }, { zone })

    @Test fun fixedPatternIsGregorianWithLatinDigitsUnderArabic() {
        assertEquals("2026-03-08 14:05", formats("ar").pattern("yyyy-MM-dd HH:mm", moment))
        assertEquals("2026-03-08 14:05", formats("ar-SA").pattern("yyyy-MM-dd HH:mm", moment))
    }

    @Test fun fixedPatternIsGregorianWithLatinDigitsUnderThai() {
        assertEquals("2026-03-08", formats("th-TH").pattern("yyyy-MM-dd", moment))
        assertEquals("2026-03-08", formats("th-TH-u-ca-buddhist-nu-thai").pattern("yyyy-MM-dd", moment))
    }

    @Test fun patternNamesStayLocalised() {
        assertEquals("su", formats("fi").pattern("EEE", moment))
        assertEquals("Sun", formats("en").pattern("EEE", moment))
    }

    @Test fun patternUsesTheConfiguredZone() {
        val utc = DesktopDateTimeFormats({ Locale.ENGLISH }, { ZoneId.of("UTC") })
        assertEquals("12:05", utc.pattern("HH:mm", moment))
    }

    @Test fun shortDateAndTimeFollowLocale() {
        assertEquals("8.3.2026", formats("fi").shortDate(moment))
        assertTrue(formats("en-US").shortDate(moment).startsWith("3/8/"))
        assertEquals("14.05", formats("fi").shortTime(moment))
    }

    @Test fun relativeTimeSpan() {
        val f = formats("en")
        val now = moment
        assertEquals("now", f.relativeTimeSpan(now - 5_000, now))
        assertEquals("5 min. ago", f.relativeTimeSpan(now - 5 * 60_000, now))
        assertEquals("in 2 hr.", f.relativeTimeSpan(now + 2 * 3_600_000, now))
        assertEquals("1 day ago", f.relativeTimeSpan(now - 86_400_000, now))
        assertEquals("3 days ago", f.relativeTimeSpan(now - 3 * 86_400_000, now))
        assertEquals(f.shortDate(now - 30 * 86_400_000L), f.relativeTimeSpan(now - 30 * 86_400_000L, now))
    }
}

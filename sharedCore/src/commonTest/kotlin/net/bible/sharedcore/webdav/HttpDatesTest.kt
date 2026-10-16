package net.bible.sharedcore.webdav

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HttpDatesTest {
    @Test fun rfc1123_basic() =
        assertEquals(784111777000L, HttpDates.parseRfc1123("Sun, 06 Nov 1994 08:49:37 GMT"))

    @Test fun rfc1123_leapDay() =
        assertEquals(1709210096000L, HttpDates.parseRfc1123("Thu, 29 Feb 2024 12:34:56 GMT"))

    @Test fun rfc1123_singleDigitDayAndExtraSpaces() =
        assertEquals(1704067200000L, HttpDates.parseRfc1123("Mon,  1 Jan 2024 00:00:00 GMT"))

    @Test fun rfc1123_garbage_isNull() {
        assertNull(HttpDates.parseRfc1123(""))
        assertNull(HttpDates.parseRfc1123("yesterday"))
        assertNull(HttpDates.parseRfc1123("Sun, 06 Foo 1994 08:49:37 GMT"))
    }

    @Test fun iso8601_utc() =
        assertEquals(784111777000L, HttpDates.parseIso8601("1994-11-06T08:49:37Z"))

    @Test fun iso8601_fractionIsTruncatedToMillis() =
        assertEquals(784111777123L, HttpDates.parseIso8601("1994-11-06T08:49:37.123456Z"))

    @Test fun iso8601_offset() =
        assertEquals(784111777000L, HttpDates.parseIso8601("1994-11-06T10:49:37+02:00"))

    @Test fun iso8601_negativeOffset() =
        assertEquals(784111777000L, HttpDates.parseIso8601("1994-11-06T03:49:37-05:00"))

    @Test fun iso8601_garbage_isNull() {
        assertNull(HttpDates.parseIso8601("1994-11-06"))
        assertNull(HttpDates.parseIso8601("not a date"))
    }
}

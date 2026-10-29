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

import net.bible.sharedcore.platform.DateTimeFormats
import java.time.Instant
import java.time.ZoneId
import java.time.chrono.IsoChronology
import java.time.format.DateTimeFormatter
import java.time.format.DecimalStyle
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.abs

/**
 * [DateTimeFormats] on `java.time`: short styles in the user's locale and time zone, and fixed patterns that are
 * Gregorian with Latin digits whatever the locale (Thai Buddhist year 2569, Arabic-Indic digits), while names (`EEE`,
 * `MMM`) stay localised.
 *
 * [relativeTimeSpan] is abbreviated English ("5 min. ago", "in 2 hr.") for spans under a week and the short date beyond
 * that: the JDK has no localised relative-time formatter.
 */
class DesktopDateTimeFormats(
    private val localeProvider: () -> Locale = { Locale.getDefault() },
    private val zoneProvider: () -> ZoneId = { ZoneId.systemDefault() },
) : DateTimeFormats {
    private fun zoned(epochMs: Long) = Instant.ofEpochMilli(epochMs).atZone(zoneProvider())

    override fun shortDate(epochMs: Long): String =
        DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(localeProvider()).format(zoned(epochMs))

    override fun shortTime(epochMs: Long): String =
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(localeProvider()).format(zoned(epochMs))

    override fun relativeTimeSpan(epochMs: Long, nowMs: Long): String {
        val seconds = abs(nowMs - epochMs) / 1000
        val past = epochMs <= nowMs
        val text = when {
            seconds < 60 -> return "now"
            seconds < 3600 -> "${seconds / 60} min."
            seconds < 86_400 -> "${seconds / 3600} hr."
            seconds < 7 * 86_400 -> (seconds / 86_400).let { "$it day" + if (it == 1L) "" else "s" }
            else -> return shortDate(epochMs)
        }
        return if (past) "$text ago" else "in $text"
    }

    override fun pattern(pattern: String, epochMs: Long): String {
        val locale = Locale.Builder().setLocale(localeProvider())
            .setUnicodeLocaleKeyword("ca", "gregory")
            .setUnicodeLocaleKeyword("nu", "latn")
            .build()
        return DateTimeFormatter.ofPattern(pattern, locale)
            .withChronology(IsoChronology.INSTANCE)
            .withDecimalStyle(DecimalStyle.STANDARD)
            .format(zoned(epochMs))
    }
}

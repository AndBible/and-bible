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
package net.bible.sharedcore.platform

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterNoStyle
import platform.Foundation.NSDateFormatterShortStyle
import platform.Foundation.NSLocale
import platform.Foundation.NSRelativeDateTimeFormatter
import platform.Foundation.NSRelativeDateTimeFormatterUnitsStyleAbbreviated
import platform.Foundation.NSTimeZone
import platform.Foundation.currentLocale
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.defaultTimeZone

/** [DateTimeFormats] on `NSDateFormatter` (short styles, fixed patterns) and `NSRelativeDateTimeFormatter` (abbreviated). */
class IosDateTimeFormats : DateTimeFormats {
    private fun date(epochMs: Long) = NSDate.dateWithTimeIntervalSince1970(epochMs / 1000.0)

    private fun formatter() = NSDateFormatter().apply {
        locale = NSLocale.currentLocale
        timeZone = NSTimeZone.defaultTimeZone
    }

    override fun shortDate(epochMs: Long): String = formatter().apply {
        dateStyle = NSDateFormatterShortStyle
        timeStyle = NSDateFormatterNoStyle
    }.stringFromDate(date(epochMs))

    override fun shortTime(epochMs: Long): String = formatter().apply {
        dateStyle = NSDateFormatterNoStyle
        timeStyle = NSDateFormatterShortStyle
    }.stringFromDate(date(epochMs))

    override fun relativeTimeSpan(epochMs: Long, nowMs: Long): String = NSRelativeDateTimeFormatter().apply {
        locale = NSLocale.currentLocale
        unitsStyle = NSRelativeDateTimeFormatterUnitsStyleAbbreviated
    }.localizedStringForDate(date(epochMs), relativeToDate = date(nowMs))

    override fun pattern(pattern: String, epochMs: Long): String = formatter().apply {
        dateFormat = pattern
    }.stringFromDate(date(epochMs))
}

@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

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

/*
 * The iOS date stamps behind the host ports. They stay on NSDateFormatter exactly as before: the common
 * `DateTimeFormats.pattern` uses the device locale (not the UI-language tag, or en_US_POSIX) and forces Latin digits,
 * so swapping it in would change the output.
 */

/** AI log timestamp `yyyy-MM-dd HH:mm` in local time, Gregorian, in the locale of the UI language [tag]. */
@kotlin.native.HiddenFromObjC
fun iosAiLogTimestamp(tag: String, epochMs: Long): String = NSDateFormatter().apply {
    locale = NSLocale.localeWithLocaleIdentifier(tag)
    calendar = NSCalendar(calendarIdentifier = NSCalendarIdentifierGregorian)
    timeZone = NSTimeZone.defaultTimeZone
    dateFormat = "yyyy-MM-dd HH:mm"
}.stringFromDate(NSDate.dateWithTimeIntervalSince1970(epochMs / 1000.0))

/** Bookmark CSV file-name stamp `yyyy-MM-dd_HH-mm` for the current time (en_US_POSIX, device calendar and zone). */
@kotlin.native.HiddenFromObjC
fun iosBookmarkCsvTimestamp(): String = NSDateFormatter().apply {
    locale = NSLocale("en_US_POSIX")
    dateFormat = "yyyy-MM-dd_HH-mm"
}.stringFromDate(NSDate())

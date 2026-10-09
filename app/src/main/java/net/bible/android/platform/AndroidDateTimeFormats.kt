package net.bible.android.platform

import android.content.Context
import android.text.format.DateFormat
import android.text.format.DateUtils
import net.bible.sharedcore.platform.DateTimeFormats
import java.util.Date

class AndroidDateTimeFormats(private val context: Context) : DateTimeFormats {
    override fun shortDate(epochMs: Long): String = DateFormat.getDateFormat(context).format(Date(epochMs))
    override fun shortTime(epochMs: Long): String = DateFormat.getTimeFormat(context).format(Date(epochMs))
    override fun relativeTimeSpan(epochMs: Long, nowMs: Long): String =
        DateUtils.getRelativeTimeSpanString(epochMs, nowMs, DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE).toString()
}

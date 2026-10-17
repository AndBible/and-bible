package net.bible.android.platform

import net.bible.sharedlogic.platform.LocalCalendar
import java.util.Calendar
import java.util.Locale

object AndroidLocalCalendar : LocalCalendar {
    private fun at(epochMs: Long) = Calendar.getInstance().apply { timeInMillis = epochMs }
    private fun Calendar.zeroTime() {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    override fun startOfLocalDay(epochMs: Long) = at(epochMs).apply { zeroTime() }.timeInMillis
    override fun plusLocalDays(epochMs: Long, days: Int) = at(epochMs).apply { add(Calendar.DAY_OF_YEAR, days) }.timeInMillis
    override fun plusWeeks(epochMs: Long, weeks: Int) = at(epochMs).apply { add(Calendar.WEEK_OF_YEAR, weeks) }.timeInMillis
    override fun startOfLocalWeek(epochMs: Long) = at(epochMs).apply {
        set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
        zeroTime()
    }.timeInMillis
    override fun localMonth(epochMs: Long) = at(epochMs).get(Calendar.MONTH)
    override fun shortMonthName(epochMs: Long): String =
        at(epochMs).getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.getDefault()) ?: ""
}

package net.bible.android.control.readingplan

import java.util.Calendar
import java.util.Date

/** Today at 00:00:00.000 in the device time zone. */
internal val truncatedDate: Date
    get() = Calendar.getInstance().let { date ->
        date.set(Calendar.HOUR_OF_DAY, 0)
        date.set(Calendar.MINUTE, 0)
        date.set(Calendar.SECOND, 0)
        date.set(Calendar.MILLISECOND, 0)
        date.time
    }

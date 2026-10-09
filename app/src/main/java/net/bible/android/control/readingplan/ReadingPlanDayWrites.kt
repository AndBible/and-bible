package net.bible.android.control.readingplan

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.bible.service.readingplan.ReadingPlanInfoDto
import java.util.Calendar
import java.util.Date
import kotlin.coroutines.CoroutineContext

/**
 * The multi-step reading-plan writes of the daily-reading screen, each run to completion in
 * [writeContext] (the application scope) instead of the calling screen's scope: a screen that is
 * destroyed mid-sequence (Done then Back, recreation) must not leave a half-applied "set current
 * day" or skip the start-date write. The caller's own cancellation only stops it waiting for the
 * result; the UI reload afterwards stays in the caller's scope.
 *
 * The writes run one at a time ([lock]): they used to be serialised on the main thread, and
 * [ReadingPlanControl.getReadingStatus]'s cache is not synchronised, so two quick taps running
 * concurrently could each build their own status and the second's write drop the first's tick.
 */
class ReadingPlanDayWrites(
    private val control: ReadingPlanControl,
    private val writeContext: CoroutineContext,
) {
    private val lock = Mutex()

    private suspend fun <T> serialised(block: suspend () -> T): T =
        withContext(writeContext) { lock.withLock { block() } }

    /** [ReadingPlanControl.done] for the day on screen; returns the next day to show (or -1). */
    suspend fun done(info: ReadingPlanInfoDto, day: Int): Int =
        serialised { control.done(info, day, false) }

    /** Makes [day] the current day: start date moved so that today is [day], then days before it done. */
    suspend fun setCurrentDay(info: ReadingPlanInfoDto, day: Int) = serialised {
        val start = Calendar.getInstance().apply { add(Calendar.DATE, -(day - 1)) }
        control.setStartDate(info, start.time)
        control.done(info, day - 1, true)
        Unit
    }

    suspend fun setStartDate(info: ReadingPlanInfoDto, start: Date) =
        serialised { control.setStartDate(info, start) }

    /** Flips the read mark of one reading of [day]. */
    suspend fun toggleRead(day: Int, readingNo: Int) = serialised {
        val status = control.getReadingStatus(day)
        if (status.isRead(readingNo)) status.setUnread(readingNo) else status.setRead(readingNo)
    }
}

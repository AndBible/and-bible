package net.bible.android.view.activity.nav.ports

import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.service.common.CommonUtils
import net.bible.sharedui.app.readingplan.ReadingPlanHostPlatform
import java.util.Calendar

/**
 * [ReadingPlanHostPlatform] over the nav host: the `java.util.Calendar` arithmetic and the Activity-bound seams
 * of the reading plan cluster, moved here unchanged from `NavHostComposeActivity`.
 */
class AndroidReadingPlanHostPlatform(private val activity: NavHostComposeActivity) : ReadingPlanHostPlatform {
    override val isPortrait: Boolean get() = CommonUtils.isPortrait

    override fun localYmd(epochMs: Long): Triple<Int, Int, Int> {
        val calendar = Calendar.getInstance().apply { timeInMillis = epochMs }
        return Triple(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1, calendar.get(Calendar.DAY_OF_MONTH))
    }

    override fun withLocalYmd(epochMs: Long, year: Int, month1to12: Int, day: Int): Long {
        val calendar = Calendar.getInstance().apply { timeInMillis = epochMs }
        calendar.set(year, month1to12 - 1, day)
        return calendar.timeInMillis
    }
}

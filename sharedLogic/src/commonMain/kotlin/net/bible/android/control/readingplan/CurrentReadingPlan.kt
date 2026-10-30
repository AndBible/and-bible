package net.bible.android.control.readingplan

import net.bible.android.database.readingplan.ReadingPlanDao
import net.bible.sharedcore.platform.AppSettings

/** Device-local selection. A reset, local or synced, must not leave an unstarted plan selected. */
internal class CurrentReadingPlan(private val settings: AppSettings) {
    val code: String get() = settings.getString(KEY, "").orEmpty()

    fun select(planCode: String) = settings.setString(KEY, planCode)

    fun forgetReset(planCode: String) {
        if (code == planCode) settings.removeString(KEY)
    }

    /** Inspect the final rows, not DELETE logs: a reset and restart may arrive in the same patch. */
    suspend fun reconcile(dao: ReadingPlanDao) {
        val selected = code
        if (selected.isNotEmpty() && dao.getPlan(selected) == null) forgetReset(selected)
    }

    companion object { private const val KEY = "reading_plan" }
}

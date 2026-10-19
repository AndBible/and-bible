package net.bible.sharedui.app.readingplan

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import net.bible.sharedcore.readingplan.DailyReadingController
import net.bible.sharedcore.readingplan.DailyReadingUi
import net.bible.sharedcore.readingplan.PlanEntry
import net.bible.sharedcore.readingplan.ReadingItem
import net.bible.sharedcore.readingplan.ReadingPlanSelectorController
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ReadingPlanMetadataRefreshTest {
    private fun daily() = DailyReadingController(
        {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, { _, _, _ -> }, {},
    )

    @Test
    fun failedDailyProviderPreservesLoadedContentAndConfirmation() = runTest {
        val controller = daily()
        controller.setUi(DailyReadingUi("Plan", "Day 3", "Date", listOf(ReadingItem(1, "John 3", true)), false, true, false))
        controller.requestReset()
        val before = controller.ui.value
        val confirm = controller.confirm.value
        val errors = mutableListOf<Throwable>()
        refreshReadingPlanMetadata(log = { errors += it }) {
            val name: String = error("unreadable daily plan")
            controller.updateMetadata(name, "Day", "Date")
        }
        assertEquals(before, controller.ui.value)
        assertEquals(confirm, controller.confirm.value)
        assertEquals("unreadable daily plan", errors.single().message)
    }

    @Test
    fun failedSelectorProviderPreservesLoadedContentAndDismissedWarning() = runTest {
        var selected: String? = null
        val plans = listOf(PlanEntry("p1", "Plan", "Description"))
        val controller = ReadingPlanSelectorController(this, { plans }, { true }, { selected = it }, {})
        controller.dismissDuplicateWarning()
        val errors = mutableListOf<Throwable>()
        refreshReadingPlanMetadata(log = { errors += it }) {
            val name: String = error("unreadable selector plan")
            controller.updateMetadata { it.copy(name = name) }
        }
        assertEquals(plans, controller.plans.value)
        assertEquals(false, controller.duplicateWarning.value)
        controller.select("p1")
        assertEquals("p1", selected)
        assertEquals("unreadable selector plan", errors.single().message)
    }

    @Test
    fun dailyAndSelectorCancellationPropagates() = runTest {
        for (path in listOf("daily", "selector")) {
            val errors = mutableListOf<Throwable>()
            assertFailsWith<CancellationException> {
                refreshReadingPlanMetadata(log = { errors += it }) { throw CancellationException(path) }
            }
            assertEquals(emptyList(), errors)
        }
    }
}

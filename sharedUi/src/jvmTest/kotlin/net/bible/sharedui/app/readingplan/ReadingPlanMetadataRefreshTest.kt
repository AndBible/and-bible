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
import net.bible.service.readingplan.OneDaysReadingsDto
import net.bible.service.readingplan.ReadingPlanInfoDto
import net.bible.sharedlogic.platform.LocalCalendar
import net.bible.sharedui.strings.keyed.KeyedCoreStrings
import net.bible.sharedui.strings.keyed.StringTable

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

    private fun table(tag: String, dayTemplate: String) = StringTable.of(
        tag, { if (it == "rdg_plan_day") dayTemplate else it }, { n, _ -> n }, { emptyList() },
    )

    @Test
    fun loadedDayDescriptionIsReReadAfterALanguageSwitchKeepingReadStateAndConfirmation() = runTest {
        var current = table("en", "Day %s")
        val calendar = java.lang.reflect.Proxy.newProxyInstance(
            LocalCalendar::class.java.classLoader, arrayOf(LocalCalendar::class.java),
        ) { _, method, _ -> error("unexpected calendar call ${method.name}") } as LocalCalendar
        // One loaded DTO for the whole test: nothing re-creates it on the language switch.
        val dto = OneDaysReadingsDto(3, null, ReadingPlanInfoDto("p1").apply { planName = "Plan" }, KeyedCoreStrings { current }, calendar)
        val controller = daily()
        controller.setUi(DailyReadingUi("Plan", dto.dayDesc, "", listOf(ReadingItem(1, "John 3", true)), false, true, false))
        controller.requestReset()
        val confirm = controller.confirm.value
        assertEquals("Day 3", controller.ui.value.dayDesc)

        current = table("fi", "Päivä %s")
        applyLoadedDayMetadata(controller, dto, "Suunnitelma")

        assertEquals("Päivä 3", controller.ui.value.dayDesc)
        assertEquals("Suunnitelma", controller.ui.value.planName)
        assertEquals(listOf(ReadingItem(1, "John 3", true)), controller.ui.value.readings)
        assertEquals(true, controller.ui.value.allRead)
        assertEquals(confirm, controller.confirm.value)

        // A missing plan name keeps the loaded one; the description still follows the language.
        current = table("en", "Day %s")
        applyLoadedDayMetadata(controller, dto, null)
        assertEquals("Day 3", controller.ui.value.dayDesc)
        assertEquals("Suunnitelma", controller.ui.value.planName)
    }
}

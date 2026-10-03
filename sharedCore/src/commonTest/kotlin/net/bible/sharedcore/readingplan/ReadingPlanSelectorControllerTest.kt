package net.bible.sharedcore.readingplan

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class ReadingPlanSelectorControllerTest {
    private val sample = listOf(PlanEntry("y1", "Plan 1", "Desc 1"), PlanEntry("y2", "Plan 2", "Desc 2"))

    @Test fun load_populates_plans_and_duplicate_flag() {
        val c = ReadingPlanSelectorController({ sample }, { true }, {}, {})
        assertEquals(sample, c.plans.value)
        assertTrue(c.duplicateWarning.value)
        assertNull(c.error.value)
    }

    @Test fun select_forwards_planCode() {
        var selected: String? = null
        val c = ReadingPlanSelectorController({ sample }, { false }, { selected = it }, {})
        c.select("y2")
        assertEquals("y2", selected)
    }

    @Test fun reset_forwards_and_reloads() {
        var reset: String? = null
        var loads = 0
        val c = ReadingPlanSelectorController({ loads++; sample }, { false }, {}, { reset = it })
        c.reset("y1")
        assertEquals("y1", reset)
        assertEquals(2, loads) // init + reload after reset
    }

    @Test fun throwing_load_sets_error() {
        val c = ReadingPlanSelectorController({ throw RuntimeException("boom") }, { false }, {}, {})
        assertEquals(ReadingPlanError.FAILED, c.error.value)
        c.dismissError()
        assertNull(c.error.value)
    }

    @Test fun dismiss_duplicate_warning_clears_it() {
        val c = ReadingPlanSelectorController({ sample }, { true }, {}, {})
        c.dismissDuplicateWarning()
        assertFalse(c.duplicateWarning.value)
    }
}

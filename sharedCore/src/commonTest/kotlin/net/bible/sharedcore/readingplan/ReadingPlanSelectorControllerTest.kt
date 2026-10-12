package net.bible.sharedcore.readingplan

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

@OptIn(ExperimentalCoroutinesApi::class)
class ReadingPlanSelectorControllerTest {
    private val sample = listOf(PlanEntry("y1", "Plan 1", "Desc 1"), PlanEntry("y2", "Plan 2", "Desc 2"))

    @Test fun load_populates_plans_and_duplicate_flag() = runTest {
        val c = ReadingPlanSelectorController(this, { sample }, { true }, {}, {})
        assertEquals(sample, c.plans.value)
        assertTrue(c.duplicateWarning.value)
        assertNull(c.error.value)
    }

    @Test fun select_forwards_planCode() = runTest {
        var selected: String? = null
        val c = ReadingPlanSelectorController(this, { sample }, { false }, { selected = it }, {})
        c.select("y2")
        assertEquals("y2", selected)
    }

    @Test fun reset_forwards_and_reloads() = runTest {
        var reset: String? = null
        var loads = 0
        val c = ReadingPlanSelectorController(this, { loads++; sample }, { false }, {}, { reset = it })
        c.reset("y1")
        assertEquals("y1", reset)
        assertEquals(2, loads) // init + reload after reset
    }

    @Test fun throwing_load_sets_error() = runTest {
        val c = ReadingPlanSelectorController(this, { throw RuntimeException("boom") }, { false }, {}, {})
        assertEquals(ReadingPlanError.FAILED, c.error.value)
        c.dismissError()
        assertNull(c.error.value)
    }

    @Test fun dismiss_duplicate_warning_clears_it() = runTest {
        val c = ReadingPlanSelectorController(this, { sample }, { true }, {}, {})
        c.dismissDuplicateWarning()
        assertFalse(c.duplicateWarning.value)
    }

    @Test fun suspended_source_keeps_rows_empty_until_completion() = runTest {
        val gate = CompletableDeferred<List<PlanEntry>>()
        val c = ReadingPlanSelectorController(this, { gate.await() }, { false }, {}, {})
        assertEquals(emptyList(), c.plans.value)
        gate.complete(sample)
        runCurrent()
        assertEquals(sample, c.plans.value)
    }

    @Test fun cancelled_source_does_not_set_failed() = runTest {
        val c = ReadingPlanSelectorController(this, { throw CancellationException("screen closed") }, { false }, {}, {})
        assertNull(c.error.value)
    }

    @Test fun latest_reload_rejects_old_success_and_error() = runTest {
        for (fail in listOf(false, true)) {
            val held = CompletableDeferred<Unit>()
            var calls = 0
            val c = ReadingPlanSelectorController(this, {
                if (++calls == 1) {
                    withContext(NonCancellable) { held.await() }
                    if (fail) error("obsolete")
                    emptyList()
                } else sample
            }, { false }, {}, {})
            c.load()
            assertEquals(sample, c.plans.value)
            held.complete(Unit)
            runCurrent()
            assertEquals(sample, c.plans.value)
            assertNull(c.error.value)
        }
    }
}

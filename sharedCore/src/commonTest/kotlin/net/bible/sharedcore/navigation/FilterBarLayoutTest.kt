package net.bible.sharedcore.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FilterBarLayoutTest {
    private val evenWeights = listOf(0.5f, 0.5f)

    @Test fun n_ary_shrink_reproduces_the_pair_function_for_two_chips() {
        // Same inputs the tuned pair function gets from DocumentFilterBar.
        val pair = shrinkToFitPair(available = 400, gap = 24, languageNatural = 300, typeNatural = 200,
            languageShareOfShortfall = 2f / 3f, minWidth = 80)
        val chips = shrinkToFitChips(available = 400, gap = 24, naturals = listOf(300, 200),
            shortfallWeights = listOf(2f / 3f, 1f / 3f), minWidth = 80)
        assertEquals(listOf(pair.languageWidth, pair.typeWidth), chips.widths)
        assertEquals(pair.gap, chips.gap)
    }

    @Test fun everything_fits_means_nothing_shrinks() {
        val out = shrinkToFitChips(available = 500, gap = 10, naturals = listOf(100, 100, 100),
            shortfallWeights = listOf(1f / 3f, 1f / 3f, 1f / 3f), minWidth = 40)
        assertEquals(listOf(100, 100, 100), out.widths)
    }

    @Test fun the_total_never_exceeds_the_available_width_for_any_input() {
        for (available in listOf(0, 5, 37, 120, 400)) {
            for (gap in listOf(0, 8, 500)) {
                val out = shrinkToFitChips(available, gap, listOf(300, 200, 150),
                    listOf(0.5f, 0.3f, 0.2f), minWidth = 80)
                val total = out.widths.sum() + out.gap * (out.widths.size - 1)
                assertTrue(total <= maxOf(available, 0), "total=$total available=$available")
                assertTrue(out.widths.all { it >= 0 })
            }
        }
    }

    @Test fun count_stays_on_one_row_when_the_chips_fit_at_natural_width() {
        val out = filterBarLayout(available = 1000, gap = 8, chipNaturals = listOf(200, 150),
            shortfallWeights = evenWeights, countWidth = 120, tuneWidth = 48, minChipWidth = 80)
        assertEquals(false, out.countOnSecondRow)
        assertEquals(listOf(200, 150), out.chipWidths)
    }

    @Test fun count_drops_to_a_second_row_rather_than_letting_a_chip_truncate() {
        // chips need 350 + 8 gap; with the count (120) and tune (48) on the row they do not fit in
        // 500, but without the count they do — so the count moves and no chip is cut.
        val out = filterBarLayout(available = 500, gap = 8, chipNaturals = listOf(200, 150),
            shortfallWeights = evenWeights, countWidth = 120, tuneWidth = 48, minChipWidth = 80)
        assertEquals(true, out.countOnSecondRow)
        assertEquals(listOf(200, 150), out.chipWidths)
    }

    @Test fun chips_still_shrink_when_even_the_second_row_layout_cannot_fit_them() {
        val out = filterBarLayout(available = 260, gap = 8, chipNaturals = listOf(200, 150),
            shortfallWeights = evenWeights, countWidth = 120, tuneWidth = 48, minChipWidth = 60)
        assertEquals(true, out.countOnSecondRow)
        assertTrue(out.chipWidths.sum() < 350)
        assertTrue(out.chipWidths.all { it >= 0 })
    }
}

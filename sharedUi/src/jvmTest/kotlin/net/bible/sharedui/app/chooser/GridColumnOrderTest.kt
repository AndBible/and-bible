package net.bible.sharedui.app.chooser

import net.bible.android.view.activity.navigation.columnMajor
import net.bible.sharedcore.navigation.GridButton
import kotlin.test.Test
import kotlin.test.assertEquals

class GridColumnOrderTest {
    private fun rows(count: Int, columns: Int) =
        columnMajor((1..count).map { GridButton(it, it.toString()) }, columns)
            .map { it?.id }.chunked(columns)

    @Test fun incompleteLastColumnKeepsItsEmptySlots() {
        assertEquals(listOf(
            listOf(1, 6, 11, 16, 21),
            listOf(2, 7, 12, 17, null),
            listOf(3, 8, 13, 18, null),
            listOf(4, 9, 14, 19, null),
            listOf(5, 10, 15, 20, null),
        ), rows(21, 5))
    }

    @Test fun fullColumnsKeepTheirColumnOrder() {
        assertEquals(listOf(listOf(1, 3, 5, 7, 9), listOf(2, 4, 6, 8, 10)), rows(10, 5))
    }

    @Test fun fewerItemsThanColumnsLeaveTheRemainingCellsEmpty() {
        assertEquals(listOf(listOf(1, 2, 3, null, null)), rows(3, 5))
    }

    @Test fun multipleEmptyColumnsDoNotShiftTheSecondRow() {
        assertEquals(listOf(
            listOf(1, 3, 5, null, null),
            listOf(2, 4, 6, null, null),
        ), rows(6, 5))
    }

    @Test fun singleColumnKeepsItsOriginalOrder() {
        assertEquals(listOf(listOf(1), listOf(2), listOf(3)), rows(3, 1))
    }

    @Test fun emptyGridHasNoCells() {
        assertEquals(emptyList(), rows(0, 5))
    }
}

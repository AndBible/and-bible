package net.bible.sharedcore.search
import kotlin.test.Test
import kotlin.test.assertEquals
class SearchModelsTest {
    @Test fun styledText_concatenates_run_text() {
        val t = StyledText(listOf(StyledRun("Gen ", bold = true), StyledRun("1:1", highlight = true)))
        assertEquals("Gen 1:1", t.plainText())
    }
    @Test fun multiResults_total_counts_both_partitions() {
        val row = SwordResultRow("Gen 1:1", emptyList(), StyledText(emptyList()))
        val r = MultiSearchResults(main = listOf(row), other = listOf(row, row), total = 3)
        assertEquals(3, r.total)
    }
}

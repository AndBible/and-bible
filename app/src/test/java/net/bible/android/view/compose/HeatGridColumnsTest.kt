package net.bible.android.view.compose

import java.io.File
import net.bible.sharedui.progress.BOOK_GRID_COLUMNS
import net.bible.sharedui.progress.CHAPTER_GRID_COLUMNS
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The Compose heat grids must use the same column counts classic's GridLayouts declare, so the two
 * UIs lay books/chapters out identically. Reads the classic layout XML directly (the unit-test
 * working directory is the :app module dir, the same assumption the Roborazzi golden paths make),
 * so a change on EITHER side fails here instead of silently drifting.
 */
class HeatGridColumnsTest {

    private val layout = File("src/main/res/layout/reading_progress.xml").readText()

    /** columnCount of the GridLayout whose android:id is [id]. */
    private fun columnCountOf(id: String): Int {
        val idIndex = layout.indexOf("@+id/$id")
        require(idIndex > 0) { "no view with id $id in reading_progress.xml" }
        val match = Regex("""android:columnCount="(\d+)"""").find(layout, idIndex)
        require(match != null) { "no columnCount after id $id" }
        return match.groupValues[1].toInt()
    }

    @Test
    fun bookGridsUseClassicColumnCount() {
        listOf("otBooksGrid", "ntBooksGrid", "memOtBooksGrid", "memNtBooksGrid").forEach { id ->
            assertEquals("book grid $id", columnCountOf(id), BOOK_GRID_COLUMNS)
        }
    }

    @Test
    fun chapterGridsUseClassicColumnCount() {
        listOf("chaptersGrid", "memChaptersGrid").forEach { id ->
            assertEquals("chapter grid $id", columnCountOf(id), CHAPTER_GRID_COLUMNS)
        }
    }
}

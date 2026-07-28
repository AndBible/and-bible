package net.bible.android.view.compose

import java.io.File
import net.bible.sharedui.progress.BOOK_GRID_COLUMNS
import net.bible.sharedui.progress.CHAPTER_GRID_COLUMNS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Compose heat grids must use the same column counts classic's GridLayouts declare, so the two
 * UIs lay books/chapters out identically. Reads the classic layout XML directly (the unit-test
 * working directory is the :app module dir, the same assumption the Roborazzi golden paths make),
 * so a change on EITHER side fails here instead of silently drifting.
 */
class HeatGridColumnsTest {

    private val layout = File("src/main/res/layout/reading_progress.xml").readText()

    @Test
    fun bookGridsUseClassicColumnCount() {
        listOf("otBooksGrid", "ntBooksGrid", "memOtBooksGrid", "memNtBooksGrid").forEach { id ->
            assertEquals("book grid $id", columnCountOf(layout, id), BOOK_GRID_COLUMNS)
        }
    }

    @Test
    fun chapterGridsUseClassicColumnCount() {
        listOf("chaptersGrid", "memChaptersGrid").forEach { id ->
            assertEquals("chapter grid $id", columnCountOf(layout, id), CHAPTER_GRID_COLUMNS)
        }
    }

    /**
     * Regression test for the tag-bounding itself: if a `columnCount` is missing from the FIRST
     * GridLayout while a later sibling still declares one, [columnCountOf] must fail loudly rather
     * than silently attributing the sibling's value to the first element.
     */
    @Test
    fun columnCountOfDoesNotLeakALaterSiblingsValue() {
        val xml = """
            <GridLayout
                android:id="@+id/firstGrid"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"/>

            <GridLayout
                android:id="@+id/secondGrid"
                android:layout_width="match_parent"
                android:columnCount="6"/>
        """.trimIndent()

        val error = assertThrows(IllegalArgumentException::class.java) {
            columnCountOf(xml, "firstGrid")
        }
        assertTrue(
            "expected the failure message to name the offending id, was: ${error.message}",
            error.message.orEmpty().contains("firstGrid"),
        )
    }
}

/**
 * columnCount of the view whose `android:id` is [id], read out of [xml]. Bounded to that element's
 * own opening tag: XML attributes only ever appear between an element's `<TagName` and the `>` (or
 * `/>`) that closes ITS opening tag, so treating that `>` as the search boundary -- rather than
 * scanning unboundedly forward through the rest of the file -- means a `columnCount` declared on a
 * LATER sibling can never be mistaken for this element's own (missing) one. Every GridLayout this
 * test reads (`reading_progress.xml`'s `otBooksGrid`/`ntBooksGrid`/`chaptersGrid`/etc.) is written
 * as a single self-closing `<GridLayout .../>` tag, so this bound is also exactly that tag's end.
 */
private fun columnCountOf(xml: String, id: String): Int {
    val idIndex = xml.indexOf("@+id/$id")
    require(idIndex > 0) { "no view with id $id in the layout" }
    val tagEnd = xml.indexOf(">", idIndex)
    require(tagEnd > 0) { "no closing '>' found for the opening tag of $id" }
    val match = Regex("""android:columnCount="(\d+)"""").find(xml, idIndex)
    require(match != null && match.range.first < tagEnd) {
        "no columnCount within the opening tag of $id"
    }
    return match.groupValues[1].toInt()
}

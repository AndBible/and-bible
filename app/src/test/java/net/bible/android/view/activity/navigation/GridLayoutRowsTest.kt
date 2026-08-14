package net.bible.android.view.activity.navigation

import net.bible.android.TEST_SDK
import net.bible.sharedcore.navigation.GridButton
import net.bible.sharedcore.navigation.GridStep
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class GridLayoutRowsTest {

    @Test
    fun `a one-chapter book keeps the designer's row count, so cells stay small`() {
        val layout = GridChoosePassageComposeActivity.layoutGrid(count = 1, isPortrait = true, isBookGrid = false)
        assertEquals(5, layout.columns)
        assertEquals(10, layout.rows)   // classic LayoutDesigner: <=50 buttons, portrait -> 10 rows
    }

    @Test
    fun `a twelve-chapter book also keeps ten rows`() {
        val layout = GridChoosePassageComposeActivity.layoutGrid(count = 12, isPortrait = true, isBookGrid = false)
        assertEquals(10, layout.rows)
    }

    @Test
    fun `the 66-book portrait grid is unchanged - six columns, eleven rows`() {
        val layout = GridChoosePassageComposeActivity.layoutGrid(count = 66, isPortrait = true, isBookGrid = true)
        assertEquals(6, layout.columns)
        assertEquals(11, layout.rows)
    }

    @Test
    fun `landscape verse grids keep the designer's landscape row count`() {
        val layout = GridChoosePassageComposeActivity.layoutGrid(count = 176, isPortrait = false, isBookGrid = false)
        assertEquals(10, layout.rows)   // >100 buttons, landscape -> 10 rows
    }

    // Guards the wiring the three build*Step methods share: a dropped `minRows = layout.rows`
    // (or `columns = layout.columns`) would break here, without needing an Activity instance.
    @Test
    fun `assembleGridUi wires the layout's row and column counts into the GridUi`() {
        val layout = GridChoosePassageComposeActivity.layoutGrid(count = 1, isPortrait = true, isBookGrid = false)
        val ui = GridChoosePassageComposeActivity.assembleGridUi(
            step = GridStep.CHAPTER,
            title = "Jude",
            layout = layout,
            showLongNames = false,
            showProgress = true,
            showDeutToggle = false,
            buttons = listOf(GridButton(id = 1, label = "1")),
        )
        assertEquals(layout.rows, ui.minRows)
        assertEquals(layout.columns, ui.columns)
    }

    @Test
    fun `assembleGridUi threads sections through unchanged`() {
        val layout = GridChoosePassageComposeActivity.layoutGrid(count = 66, isPortrait = true, isBookGrid = true)
        val sections = listOf(listOf(GridButton(id = 0, label = "Gen")), listOf(GridButton(id = 1, label = "Exod")))
        val ui = GridChoosePassageComposeActivity.assembleGridUi(
            step = GridStep.BOOK,
            title = "Choose passage",
            layout = layout,
            showLongNames = false,
            showProgress = false,
            showDeutToggle = true,
            buttons = sections.flatten(),
            sections = sections,
        )
        assertEquals(layout.rows, ui.minRows)
        assertEquals(sections, ui.sections)
    }
}

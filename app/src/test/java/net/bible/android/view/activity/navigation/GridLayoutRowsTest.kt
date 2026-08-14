package net.bible.android.view.activity.navigation

import net.bible.android.TEST_SDK
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
}

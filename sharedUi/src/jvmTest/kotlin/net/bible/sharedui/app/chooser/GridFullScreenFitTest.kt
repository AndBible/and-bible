package net.bible.sharedui.app.chooser

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import net.bible.android.view.activity.navigation.columnMajor
import net.bible.android.view.activity.navigation.layoutGrid
import net.bible.sharedcore.navigation.GridButton
import net.bible.sharedcore.navigation.GridStep
import net.bible.sharedcore.navigation.GridUi
import net.bible.sharedui.navigation.GridChoosePassageContent
import net.bible.sharedui.theme.AbTheme
import kotlin.test.Test
import kotlin.test.assertFalse

/**
 * Issue #4143: the full-screen grid divides its height by the row count, but each cell is also
 * padded by 2dp on both sides, so the rows overflowed by rows x 4dp and the full-screen chooser
 * could always scroll a little. When every row fits at or above the cell floor, nothing scrolls,
 * also at a fractional density where per-cell pixel rounding could add up.
 */
@OptIn(ExperimentalTestApi::class)
class GridFullScreenFitTest {
    private fun grid(count: Int, isBookGrid: Boolean, sections: Boolean = false): GridUi {
        val layout = layoutGrid(count, isPortrait = true, isBookGrid = isBookGrid)
        val buttons = (1..count).map { GridButton(it, "$it", colorGroup = 1) }
        return GridUi(GridStep.CHAPTER, "Grid", layout.columns, showLongNames = false, showProgress = false,
            showDeutToggle = false, buttons = columnMajor(buttons, layout.columns), minRows = layout.rows,
            sections = if (sections) buttons.chunked(count / 3) else null)
    }

    // A window large enough for the 400 x 640dp grid at the density under test.
    private fun assertNoScroll(ui: GridUi, density: Float) = runSkikoComposeUiTest(
        size = Size(400f * density, 640f * density),
        density = Density(density),
    ) {
        lateinit var state: LazyGridState
        setContent {
            AbTheme(darkTheme = false, disableAnimations = true) {
                state = rememberLazyGridState()
                Box(Modifier.size(400.dp, 640.dp)) {
                    GridChoosePassageContent(ui, onPick = {}, state = state)
                }
            }
        }
        waitForIdle()
        assertFalse(state.canScrollForward, "full-screen grid scrolls although all rows fit (density $density)")
    }

    @Test fun bookGridFits() = assertNoScroll(grid(66, isBookGrid = true), 1f)

    @Test fun bookGridFitsAtFractionalDensity() = assertNoScroll(grid(66, isBookGrid = true), 2.75f)

    @Test fun chapterGridFitsAtFractionalDensity() = assertNoScroll(grid(50, isBookGrid = false), 2.625f)

    @Test fun groupedGridFitsAtFractionalDensity() = assertNoScroll(grid(66, isBookGrid = true, sections = true), 2.75f)
}

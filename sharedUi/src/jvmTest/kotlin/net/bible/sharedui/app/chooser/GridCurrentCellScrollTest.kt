package net.bible.sharedui.app.chooser

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import net.bible.android.view.activity.navigation.columnMajor
import net.bible.android.view.activity.navigation.layoutGrid
import net.bible.sharedcore.navigation.GridButton
import net.bible.sharedcore.navigation.GridStep
import net.bible.sharedcore.navigation.GridUi
import net.bible.sharedui.navigation.GridChoosePassageContent
import net.bible.sharedui.theme.AbTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Issue #4143: when the chooser opens at the CURRENT book, the current chapter (and on the verse
 * step the current verse) must be scrolled into view, so reading Psalm 119 does not open the
 * chapter grid at chapter 1. Any other book (no current cell) still opens at the top (#4117).
 */
@OptIn(ExperimentalTestApi::class)
class GridCurrentCellScrollTest {
    private fun grid(step: GridStep, title: String, count: Int, current: Int?, isBookGrid: Boolean = false): GridUi {
        val layout = layoutGrid(count, isPortrait = true, isBookGrid = isBookGrid)
        val first = if (isBookGrid) 2 else 1
        val buttons = (first until first + count).map {
            GridButton(it, if (isBookGrid) "B$it" else "$it", colorGroup = 1, isCurrent = it == current)
        }
        return GridUi(step, title, layout.columns, showLongNames = false, showProgress = false,
            showDeutToggle = false, buttons = columnMajor(buttons, layout.columns), minRows = layout.rows)
    }

    private val books = grid(GridStep.BOOK, "Books", 66, current = 20, isBookGrid = true)
    private val psalmsReading119 = grid(GridStep.CHAPTER, "Psalms", 150, current = 119)
    private val psalmsElsewhere = grid(GridStep.CHAPTER, "Psalms", 150, current = null)
    private val psalm119Verse150 = grid(GridStep.VERSE, "Psalms 119", 176, current = 150)

    private fun ComposeUiTest.mount(initial: GridUi, fit: Boolean): Pair<() -> LazyGridState, (GridUi) -> Unit> {
        var ui by mutableStateOf(initial)
        lateinit var state: LazyGridState
        setContent {
            AbTheme(darkTheme = false, disableAnimations = true) {
                state = rememberLazyGridState()
                Box(Modifier.size(400.dp, 300.dp).testTag(VIEWPORT)) {
                    GridChoosePassageContent(ui, onPick = {}, state = state, fitContentHeight = fit)
                }
            }
        }
        waitForIdle()
        return { state } to { next -> ui = next }
    }

    private fun ComposeUiTest.assertFullyVisible(label: String) {
        val viewport = onNodeWithTag(VIEWPORT).fetchSemanticsNode()
        val node = onNode(hasClickAction() and hasText(label)).fetchSemanticsNode()
        val top = node.positionInRoot.y
        val bottom = top + node.size.height
        val vTop = viewport.positionInRoot.y
        val vBottom = vTop + viewport.size.height
        assertTrue(top >= vTop && bottom <= vBottom, "cell $label not fully visible: $top..$bottom outside $vTop..$vBottom")
    }

    @Test
    fun fullScreenOpeningPsalmsShowsTheCurrentChapter() = runComposeUiTest {
        val (_, setUi) = mount(books, fit = false)
        setUi(psalmsReading119)
        waitForIdle()
        assertFullyVisible("119")
    }

    @Test
    fun sheetOpeningPsalmsShowsTheCurrentChapter() = runComposeUiTest {
        val (_, setUi) = mount(books, fit = true)
        setUi(psalmsReading119)
        waitForIdle()
        assertFullyVisible("119")
    }

    @Test
    fun verseStepShowsTheCurrentVerse() = runComposeUiTest {
        val (_, setUi) = mount(psalmsReading119, fit = true)
        setUi(psalm119Verse150)
        waitForIdle()
        assertFullyVisible("150")
    }

    @Test
    fun firstCompositionAtTheCurrentBookShowsTheCurrentChapter() = runComposeUiTest {
        mount(psalmsReading119, fit = false)
        assertFullyVisible("119")
    }

    @Test
    fun anotherBookStillOpensAtTheTop() = runComposeUiTest {
        val (state, setUi) = mount(psalmsReading119, fit = false)
        setUi(books)
        waitForIdle()
        setUi(psalmsElsewhere)
        waitForIdle()
        assertEquals(0 to 0, state().firstVisibleItemIndex to state().firstVisibleItemScrollOffset)
        assertFullyVisible("1")
    }

    @Test
    fun aCurrentCellAlreadyOnTheFirstPageKeepsTheTop() = runComposeUiTest {
        val (state, setUi) = mount(books, fit = false)
        setUi(grid(GridStep.CHAPTER, "Psalms", 150, current = 3))
        waitForIdle()
        assertEquals(0 to 0, state().firstVisibleItemIndex to state().firstVisibleItemScrollOffset)
        assertFullyVisible("3")
    }

    private companion object {
        const val VIEWPORT = "viewport"
    }
}

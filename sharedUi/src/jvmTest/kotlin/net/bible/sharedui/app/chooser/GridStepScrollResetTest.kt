package net.bible.sharedui.app.chooser

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import net.bible.android.view.activity.navigation.columnMajor
import net.bible.sharedcore.navigation.GridButton
import net.bible.sharedcore.navigation.GridStep
import net.bible.sharedcore.navigation.GridUi
import net.bible.sharedui.navigation.GridChoosePassageContent
import net.bible.sharedui.theme.AbTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Issue #4117: the chapter grid sometimes opened scrolled down one row (chapters 1, 11, 21, ...
 * hidden). The book and chapter steps are rendered by the same [GridChoosePassageContent] with the
 * same `LazyGridState` whenever the next step builds before the next frame (always in the quick
 * sheet, which hoists the state). The book cells are keyed by `BibleBook.ordinal` (Genesis = 2,
 * after the two intro entries) and the chapter cells by chapter number, so the lazy grid's
 * "keep the first visible item by key" rule followed key 2 to chapter 2 — in portrait column
 * order the second ROW.
 */
@OptIn(ExperimentalTestApi::class)
class GridStepScrollResetTest {
    /** 66 books as the real book step builds them: ids are BibleBook ordinals (GEN = 2). */
    private val bookUi = GridUi(
        step = GridStep.BOOK,
        title = "Choose Book",
        columns = 6,
        showLongNames = false,
        showProgress = false,
        showDeutToggle = false,
        buttons = columnMajor((2 until 2 + 66).map { GridButton(it, "B$it", colorGroup = 0) }, 6),
        minRows = 11,
    )

    /** Isaiah: 66 chapters, layoutGrid -> 10 rows x 7 columns in portrait column order. */
    private val isaiahUi = GridUi(
        step = GridStep.CHAPTER,
        title = "Isaiah",
        columns = 7,
        showLongNames = false,
        showProgress = false,
        showDeutToggle = false,
        buttons = columnMajor((1..66).map { GridButton(it, it.toString(), colorGroup = 3) }, 7),
        minRows = 10,
    )

    @Test
    fun hoistedStateChapterStepOpensAtTop() = runComposeUiTest {
        var ui by mutableStateOf(bookUi)
        lateinit var state: LazyGridState
        setContent {
            AbTheme(darkTheme = false, disableAnimations = true) {
                state = rememberLazyGridState()
                GridChoosePassageContent(ui, onPick = {}, modifier = Modifier.size(400.dp, 300.dp), state = state)
            }
        }
        waitForIdle()
        assertAtTop(state)

        ui = isaiahUi
        waitForIdle()
        assertAtTop(state)
        cell("1").assertIsDisplayed()
    }

    @Test
    fun internalStateChapterStepOpensAtTop() = runComposeUiTest {
        var ui by mutableStateOf(bookUi)
        setContent {
            AbTheme(darkTheme = false, disableAnimations = true) {
                GridChoosePassageContent(ui, onPick = {}, modifier = Modifier.size(400.dp, 300.dp))
            }
        }
        waitForIdle()
        ui = isaiahUi
        waitForIdle()
        // Row 0 of the column-ordered grid: 1, 11, 21, ... must be fully on screen.
        // positionInRoot is unclipped (boundsInRoot clips an off-screen cell to the viewport edge).
        val top = onNode(hasClickAction() and hasText("1")).fetchSemanticsNode().positionInRoot.y
        assertTrue(top >= 0f, "chapter 1 starts above the viewport (top=$top)")
        cell("11").assertIsDisplayed()
    }

    @Test
    fun backToBookStepAfterScrollingChaptersOpensAtTop() = runComposeUiTest {
        var ui by mutableStateOf(isaiahUi)
        lateinit var state: LazyGridState
        setContent {
            AbTheme(darkTheme = false, disableAnimations = true) {
                state = rememberLazyGridState()
                GridChoosePassageContent(ui, onPick = {}, modifier = Modifier.size(400.dp, 300.dp), state = state)
            }
        }
        waitForIdle()
        runOnIdle { kotlinx.coroutines.runBlocking { state.scrollToItem(35) } }
        waitForIdle()
        ui = bookUi
        waitForIdle()
        assertAtTop(state)
    }

    private fun assertAtTop(state: LazyGridState) {
        assertEquals(0 to 0, state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset)
    }

    private fun ComposeUiTest.cell(label: String) = onNode(hasClickAction() and hasText(label))
}

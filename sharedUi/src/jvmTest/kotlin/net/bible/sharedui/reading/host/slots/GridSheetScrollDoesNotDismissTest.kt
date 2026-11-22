package net.bible.sharedui.reading.host.slots

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import net.bible.android.view.activity.navigation.columnMajor
import net.bible.android.view.activity.navigation.layoutGrid
import net.bible.sharedcore.navigation.BookPick
import net.bible.sharedcore.navigation.ChapterPick
import net.bible.sharedcore.navigation.GridButton
import net.bible.sharedcore.navigation.GridChoosePassageController
import net.bible.sharedcore.navigation.GridOptions
import net.bible.sharedcore.navigation.GridStep
import net.bible.sharedcore.navigation.GridUi
import net.bible.sharedui.components.AbQuickSheetTag
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.iosStrings
import net.bible.sharedui.theme.AbTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Issue #4178: scrolling or flinging the passage grid up to its top must not hand the leftover downward
 * drag to the sheet (which then hides it). Dragging the header still dismisses.
 */
@OptIn(ExperimentalTestApi::class)
class GridSheetScrollDoesNotDismissTest {
    private val strings = iosStrings("en")

    private fun grid(step: GridStep, title: String, labels: List<String>, isBookGrid: Boolean): GridUi {
        val layout = layoutGrid(labels.size, isPortrait = true, isBookGrid = isBookGrid)
        val buttons = labels.mapIndexed { i, label -> GridButton(i + if (isBookGrid) 0 else 1, label) }
        return GridUi(step, title, layout.columns, showLongNames = false, showProgress = false,
            showDeutToggle = false, buttons = columnMajor(buttons, layout.columns), minRows = layout.rows)
    }

    /** 300 books/chapters/verses per step, so every step's grid overflows and scrolls. */
    private fun controller() = GridChoosePassageController(
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        initialOptions = GridOptions(true, false, true, false, false, false),
        buildStep = { step, _ ->
            val prefix = when (step) { GridStep.BOOK -> "Book"; GridStep.CHAPTER -> "Chap"; else -> "Verse" }
            grid(step, "T-$step", (1..300).map { "$prefix$it" }, isBookGrid = step == GridStep.BOOK)
        },
        onPersistOptions = {},
        onPickBook = { BookPick.GoChapter },
        onPickChapter = { ChapterPick.GoVerse },
        onPickVerse = { "x" },
        onFinish = {},
    )

    private fun runSheet(disableAnimations: Boolean, body: androidx.compose.ui.test.ComposeUiTest.(closed: () -> Int, c: GridChoosePassageController) -> Unit) =
        runComposeUiTest {
            val c = controller()
            var closed = 0
            setContent {
                CompositionLocalProvider(
                    LocalStrings provides strings,
                    net.bible.sharedui.components.LocalSheetDragHandleDescription provides strings.sheetDragHandle,
                ) {
                    AbTheme(darkTheme = false, disableAnimations = disableAnimations) {
                        GridKeyChooserSheetFor(c, "Fallback") { closed++ }
                    }
                }
            }
            waitForIdle()
            body({ closed }, c)
        }

    private fun androidx.compose.ui.test.ComposeUiTest.dragGridDownRepeatedly() {
        val grid = onNode(androidx.compose.ui.test.hasScrollAction())
        // Scroll down a bit, then fling/drag well past the top several times.
        grid.performTouchInput { swipeUp() }
        waitForIdle()
        repeat(4) {
            grid.performTouchInput { swipeDown(durationMillis = 80) }
            waitForIdle()
        }
        mainClock.advanceTimeBy(1000)
        waitForIdle()
    }

    private fun androidx.compose.ui.test.ComposeUiTest.assertSurvivesGridDrag(closed: () -> Int) {
        dragGridDownRepeatedly()
        assertEquals(0, closed(), "scrolling the grid to its top closed the sheet")
        onNodeWithTag(AbQuickSheetTag).assertExists()
    }

    @Test
    fun bookGridScrolledToTopKeepsSheetOpen() = runSheet(false) { closed, _ -> assertSurvivesGridDrag(closed) }

    @Test
    fun bookGridScrolledToTopKeepsSheetOpenWithoutAnimations() = runSheet(true) { closed, _ -> assertSurvivesGridDrag(closed) }

    @Test
    fun chapterGridScrolledToTopKeepsSheetOpen() = runSheet(false) { closed, c ->
        onNode(hasClickAction() and hasText("Book1")).performClick()
        waitForIdle()
        assertEquals(GridStep.CHAPTER, c.step.value)
        assertSurvivesGridDrag(closed)
    }

    @Test
    fun verseGridScrolledToTopKeepsSheetOpen() = runSheet(false) { closed, c ->
        onNode(hasClickAction() and hasText("Book1")).performClick()
        waitForIdle()
        onNode(hasClickAction() and hasText("Chap1")).performClick()
        waitForIdle()
        assertEquals(GridStep.VERSE, c.step.value)
        assertSurvivesGridDrag(closed)
    }

    @Test
    fun closeButtonStillCloses() = runSheet(false) { closed, _ ->
        onNodeWithContentDescription(strings.settingsEditorClose).performClick()
        waitForIdle()
        assertEquals(1, closed())
    }

    @Test
    fun draggingTheHeaderStillDismisses() = runSheet(true) { closed, _ ->
        // swipeDown starts at the node's top edge: the sheet's header, outside the grid.
        onNodeWithTag(AbQuickSheetTag).performTouchInput { swipeDown(durationMillis = 100) }
        mainClock.advanceTimeBy(1000)
        waitForIdle()
        assertTrue(closed() >= 1, "dragging the header down no longer dismisses the sheet")
    }
}

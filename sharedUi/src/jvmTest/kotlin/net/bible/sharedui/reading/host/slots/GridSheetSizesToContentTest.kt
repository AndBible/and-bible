package net.bible.sharedui.reading.host.slots

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlinx.coroutines.CompletableDeferred
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
import kotlin.test.assertTrue

/**
 * Issue #4133: the Choose Book quick sheet must size itself to its grid — the book list tall
 * enough for all its rows when the window allows, a chapter grid as tall as its own rows (a
 * one-chapter book is one row, not the earlier step's height), and never the whole window.
 */
@OptIn(ExperimentalTestApi::class)
class GridSheetSizesToContentTest {
    private val strings = iosStrings("en")

    /** Book ids 0..65 -> "Book<n>"; Jude (id 0) has 1 chapter, Psalms (id 1) 150, the rest 21. */
    private fun chapterCount(book: Int) = when (book) { 0 -> 1; 1 -> 150; else -> 21 }

    private fun grid(step: GridStep, title: String, labels: List<String>, isBookGrid: Boolean): GridUi {
        val layout = layoutGrid(labels.size, isPortrait = true, isBookGrid = isBookGrid)
        val buttons = labels.mapIndexed { i, label -> GridButton(i + if (isBookGrid) 0 else 1, label) }
        return GridUi(step, title, layout.columns, showLongNames = false, showProgress = false,
            showDeutToggle = false, buttons = columnMajor(buttons, layout.columns), minRows = layout.rows)
    }

    private fun controller(bookBuilt: CompletableDeferred<Unit>? = null): GridChoosePassageController {
        var book = 0
        return GridChoosePassageController(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            initialOptions = GridOptions(true, false, false, false, false, false),
            buildStep = { step, _ ->
                if (step == GridStep.BOOK) bookBuilt?.await()
                if (step == GridStep.BOOK) grid(step, "Books", (0 until 66).map { "Book$it" }, isBookGrid = true)
                else grid(step, "Book$book", (1..chapterCount(book)).map { "c$it" }, isBookGrid = false)
            },
            onPersistOptions = {},
            onPickBook = { book = it; BookPick.GoChapter },
            onPickChapter = { ChapterPick.Finish("x") },
            onPickVerse = { "x" },
            onFinish = {},
        )
    }

    @Test
    fun sheetHeightFollowsTheGridAndIsCapped() = runComposeUiTest {
        val controller = controller()
        setContent {
            CompositionLocalProvider(LocalStrings provides strings) {
                AbTheme(darkTheme = false, disableAnimations = true) {
                    GridKeyChooserSheetFor(controller, "Fallback") {}
                }
            }
        }
        waitForIdle()
        // The sheet is its own root (a popup layer); the window is the tallest root.
        val window = onAllNodes(isRoot()).fetchSemanticsNodes().maxOf { it.size.height }.toFloat()

        // Book list: every one of the 66 books lies fully inside the sheet (no scrolling needed).
        val bookSheet = sheetHeight()
        val sheetTop = window - bookSheet
        for (i in 0 until 66) {
            val node = onNode(hasClickAction() and hasText("Book$i")).fetchSemanticsNode()
            val top = node.positionInRoot.y
            assertTrue(top >= sheetTop && top + node.size.height <= window, "Book$i outside the sheet: top=$top")
        }
        assertTrue(bookSheet < window, "the book sheet covers the whole window")

        // A one-chapter book: the sheet shrinks to a single grid row.
        onNode(hasClickAction() and hasText("Book0")).performClick()
        waitForIdle()
        val judeSheet = sheetHeight()
        assertTrue(judeSheet < bookSheet / 2, "one-chapter sheet $judeSheet not much shorter than book sheet $bookSheet")

        // Back, then a 150-chapter book: the sheet grows but stays capped below the window top.
        onNodeWithContentDescription(strings.settingsEditorBack).performClick()
        waitForIdle()
        onNode(hasClickAction() and hasText("Book1")).performClick()
        waitForIdle()
        val psalmsSheet = sheetHeight()
        assertTrue(psalmsSheet > judeSheet, "150-chapter sheet $psalmsSheet not taller than one-chapter $judeSheet")
        assertTrue(psalmsSheet <= window * 0.9f, "150-chapter sheet $psalmsSheet covers (almost) the whole window $window")
    }

    /**
     * The book list builds after the sheet has opened (the empty gap is 0dp tall): with animations
     * off the sheet must still grow to the whole list instead of staying stranded at its first size.
     */
    @Test
    fun sheetGrowsToABookListThatArrivesAfterOpening() = runComposeUiTest {
        val bookBuilt = CompletableDeferred<Unit>()
        val controller = controller(bookBuilt)
        setContent {
            CompositionLocalProvider(LocalStrings provides strings) {
                AbTheme(darkTheme = false, disableAnimations = true) {
                    GridKeyChooserSheetFor(controller, "Fallback") {}
                }
            }
        }
        waitForIdle()
        val emptySheet = sheetHeight()
        bookBuilt.complete(Unit)
        waitForIdle()
        val window = onAllNodes(isRoot()).fetchSemanticsNodes().maxOf { it.size.height }.toFloat()
        assertTrue(sheetHeight() > emptySheet, "the sheet did not grow with the book list")
        val last = onNode(hasClickAction() and hasText("Book65")).fetchSemanticsNode()
        assertTrue(last.positionInRoot.y + last.size.height <= window, "the last book row is below the window")
    }

    private fun ComposeUiTest.sheetHeight(): Float =
        onNodeWithTag(AbQuickSheetTag).fetchSemanticsNode().size.height.toFloat()
}

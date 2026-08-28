package net.bible.android.view.compose.golden

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.Modifier
import net.bible.android.TEST_SDK
import net.bible.sharedcore.navigation.GridButton
import net.bible.sharedcore.navigation.GridOptions
import net.bible.sharedcore.navigation.GridStep
import net.bible.sharedcore.navigation.GridUi
import net.bible.sharedui.components.AbSheetContentMaxHeight
import net.bible.sharedui.navigation.GridChoosePassageContent
import net.bible.sharedui.navigation.GridChoosePassageScreen
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class GridChoosePassageGoldenTest {
    // A compact 12-book grid across categories, with the current book + progress.
    private val books = listOf(
        GridButton(0, "Gen", "Genesis", 0, isCurrent = false, readProgress = 1f),
        GridButton(1, "Exod", "Exodus", 0, readProgress = 0.5f),
        GridButton(2, "Josh", "Joshua", 1),
        GridButton(3, "Ps", "Psalms", 2, isCurrent = true, memProgress = 0.3f),
        GridButton(4, "Isa", "Isaiah", 3),
        GridButton(5, "Hos", "Hosea", 4),
        GridButton(6, "Matt", "Matthew", 5),
        GridButton(7, "Acts", "Acts", 6),
        GridButton(8, "Rom", "Romans", 7),
        GridButton(9, "Jas", "James", 8),
        GridButton(10, "Rev", "Revelation", 9),
        GridButton(11, "Ps151", "Psalm 151", -1),
    )
    private val opts = GridOptions(showScripture = true, alphabetical = false, ltr = false, groupByCategory = false, longNames = false, showProgress = true)
    private fun bookUi(long: Boolean = false, sections: List<List<GridButton>>? = null) =
        GridUi(GridStep.BOOK, "Choose passage (Workspace 1)", 6, showLongNames = long, showProgress = true, showDeutToggle = true, buttons = books, sections = sections, minRows = 11)

    @Test fun grid_book_flat() {
        captureMatrix("GridChoosePassage", "book_flat") { GridChoosePassageScreen(bookUi(), opts, {}, {}, {}) }
    }

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "ar")
    fun grid_book_flat_rtl() {
        captureRtl("GridChoosePassage", "book_flat") { GridChoosePassageScreen(bookUi(), opts, {}, {}, {}) }
    }

    @Test fun grid_book_grouped() {
        val sections = listOf(books.subList(0, 2), books.subList(2, 3), books.subList(3, 6), books.subList(6, 12))
        captureGolden("GridChoosePassage", "book_grouped", EDGE_MODE) {
            GridChoosePassageScreen(bookUi(sections = sections), opts.copy(groupByCategory = true, ltr = true), {}, {}, {})
        }
    }

    @Test fun grid_book_longnames() {
        captureGolden("GridChoosePassage", "book_long", EDGE_MODE) {
            GridChoosePassageScreen(bookUi(long = true), opts.copy(longNames = true), {}, {}, {})
        }
    }

    // Reproduces the reported defect: a long single-word book name ("Thessalonians") does not fit
    // the long-name line in showLongNames mode and must shrink to fit instead of wrapping/clipping.
    // Same 12-book/6-column shape as grid_book_longnames, with one short name swapped for a long one.
    @Test fun grid_book_longnames_overflow() {
        val longBooks = books.map { if (it.id == 8) it.copy(label = "1Thess", longLabel = "Thessalonians") else it }
        captureGolden("GridChoosePassage", "book_long_overflow", EDGE_MODE) {
            GridChoosePassageScreen(bookUi(long = true).copy(buttons = longBooks), opts.copy(longNames = true), {}, {}, {})
        }
    }

    // Real-world case: a Finnish long-form book name (~2.5x "Thessalonians") is where minFontSize
    // + ellipsis either degrades gracefully or looks bad — this app's users hit this, not just the
    // English edge case above.
    @Test fun grid_book_longnames_overflow_fi() {
        val longBooks = books.map {
            if (it.id == 8) it.copy(label = "1Tess", longLabel = "Ensimmäinen tessalonikalaiskirje") else it
        }
        captureGolden("GridChoosePassage", "book_long_overflow_fi", EDGE_MODE) {
            GridChoosePassageScreen(bookUi(long = true).copy(buttons = longBooks), opts.copy(longNames = true), {}, {}, {})
        }
    }

    @Test fun grid_chapter() {
        val chapters = (1..24).map { GridButton(it, it.toString(), colorGroup = 2, isCurrent = it == 3, readProgress = if (it < 3) 1f else 0f) }
        captureGolden("GridChoosePassage", "chapter", EDGE_MODE) {
            GridChoosePassageScreen(
                GridUi(GridStep.CHAPTER, "Psalms", 5, showLongNames = false, showProgress = true, showDeutToggle = false, buttons = chapters, minRows = 10),
                opts, {}, {}, {},
            )
        }
    }

    // Reproduces the reported defect: a book with very few chapters (Jude: one) must NOT get a
    // screen-filling button — the row-count floor keeps the cell sized like the other grids.
    @Test fun grid_chapter_one() {
        val chapters = listOf(GridButton(1, "1", colorGroup = 8, isCurrent = true, readProgress = 1f))
        captureGolden("GridChoosePassage", "chapter_one", EDGE_MODE) {
            GridChoosePassageScreen(
                GridUi(GridStep.CHAPTER, "Jude", 5, showLongNames = false, showProgress = true, showDeutToggle = false, buttons = chapters, minRows = 10),
                opts, {}, {}, {},
            )
        }
    }

    @Test fun grid_chapter_twelve() {
        val chapters = (1..12).map { GridButton(it, it.toString(), colorGroup = 3, isCurrent = it == 1, readProgress = if (it == 1) 1f else 0f) }
        captureGolden("GridChoosePassage", "chapter_twelve", EDGE_MODE) {
            GridChoosePassageScreen(
                GridUi(GridStep.CHAPTER, "Daniel", 5, showLongNames = false, showProgress = true, showDeutToggle = false, buttons = chapters, minRows = 10),
                opts, {}, {}, {},
            )
        }
    }

    @Test fun grid_verse() {
        val verses = (1..31).map { GridButton(it, it.toString(), colorGroup = 2, isCurrent = it == 6, memProgress = if (it == 6) 1f else 0f) }
        captureGolden("GridChoosePassage", "verse", EDGE_MODE) {
            GridChoosePassageScreen(
                GridUi(GridStep.VERSE, "Psalms 3", 5, showLongNames = false, showProgress = true, showDeutToggle = false, buttons = verses, minRows = 10),
                opts, {}, {}, {},
            )
        }
    }

    /**
     * Round 15b Task 8: the SAME book step as [grid_book_flat], rendered inside the quick sheet's
     * 400dp bound instead of the full-screen scaffold — the shape the reading view's grid quick
     * sheet actually produces, which had no golden at all despite shipping in the Speak sheet since
     * round 13a.
     *
     * The fixture is deliberately [bookUi], not a second one, so this capture and the full-screen
     * one differ ONLY by the height bound. Eleven rows at the 40dp cell floor is 440dp against a
     * 400dp bound, so the grid must SCROLL (clipped at the bottom) rather than squash: cells here
     * must be the same size as in `GridChoosePassage_book_flat_light.png`. Smaller cells would mean
     * the bound had been applied to the wrong node.
     */
    @Test fun grid_sheet_book() = captureGolden("GridChoosePassage", "sheetBook", GoldenMode.LIGHT) {
        Box(Modifier.heightIn(max = AbSheetContentMaxHeight)) {
            GridChoosePassageContent(ui = bookUi(), onPick = {}, modifier = Modifier.fillMaxSize())
        }
    }
}

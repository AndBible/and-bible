package net.bible.android.view.compose.golden

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.ui.Modifier
import net.bible.android.TEST_SDK
import net.bible.sharedcore.navigation.GridButton
import net.bible.sharedcore.navigation.GridOptions
import net.bible.sharedcore.navigation.GridStep
import net.bible.sharedcore.navigation.GridUi
import net.bible.sharedui.components.AbQuickSheetContent
import net.bible.sharedui.navigation.GridChoosePassageContent
import net.bible.sharedui.navigation.GridChoosePassageScreen
import net.bible.sharedui.navigation.GridOptionsOverflow
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

    @Test fun grid_monochromeCategoryBoundaries() {
        captureMatrix("GridChoosePassage", "categoryBoundaries") {
            GridChoosePassageScreen(bookUi().copy(showProgress = false), opts.copy(showProgress = false), {}, {}, {})
        }
        MONO_MODES.forEach { mode ->
            val image = javax.imageio.ImageIO.read(java.io.File("build/mono-audit/GridChoosePassage_categoryBoundaries_${mode.tag}.png"))
            val ink = if (mode.dark) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
            // An inactive cell must have an ink outline, not disappear into the paper background.
            org.junit.Assert.assertTrue("Inactive category outline in ${mode.tag}",
                (66..68).any { y -> (25..35).any { x -> image.getRGB(x, y) == ink } })
            val paper = if (mode.dark) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
            org.junit.Assert.assertEquals("Inactive category fill in ${mode.tag}", paper, image.getRGB(30, 80))
            org.junit.Assert.assertEquals("Current category fill in ${mode.tag}", ink, image.getRGB(185, 80))
        }
    }

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
     * A full 66-book grid — the fixture [grid_sheet_book] needs and [bookUi] deliberately is not.
     *
     * SECOND fixture on purpose: [bookUi] is what twelve existing goldens are recorded against and
     * must stay byte-identical, so it cannot grow. Sixty-six buttons over six columns is eleven laid
     * out rows, which is what makes the sheet capture OVERFLOW its bound instead of merely fitting
     * inside it (arithmetic in [grid_sheet_book]'s kdoc).
     *
     * `colorGroup` follows the same coarse OT/NT categories the 12-book fixture samples, so the
     * palette in the capture is representative rather than a rainbow: Pentateuch, History, Wisdom,
     * Major/Minor prophets, Gospels, Acts, Pauline, General epistles, Revelation.
     */
    private val allBooks: List<GridButton> = listOf(
        "Gen", "Exod", "Lev", "Num", "Deut",
        "Josh", "Judg", "Ruth", "1Sam", "2Sam", "1Kgs", "2Kgs", "1Chr", "2Chr", "Ezra", "Neh", "Esth",
        "Job", "Ps", "Prov", "Eccl", "Song",
        "Isa", "Jer", "Lam", "Ezek", "Dan",
        "Hos", "Joel", "Amos", "Obad", "Jonah", "Mic", "Nah", "Hab", "Zeph", "Hag", "Zech", "Mal",
        "Matt", "Mark", "Luke", "John",
        "Acts",
        "Rom", "1Cor", "2Cor", "Gal", "Eph", "Phil", "Col", "1Thess", "2Thess", "1Tim", "2Tim", "Titus", "Phlm",
        "Heb", "Jas", "1Pet", "2Pet", "1John", "2John", "3John", "Jude",
        "Rev",
    ).mapIndexed { i, abbr ->
        GridButton(
            id = i,
            label = abbr,
            colorGroup = when (i) {
                in 0..4 -> 0; in 5..16 -> 1; in 17..21 -> 2; in 22..26 -> 3; in 27..38 -> 4
                in 39..42 -> 5; 43 -> 6; in 44..56 -> 7; in 57..64 -> 8; else -> 9
            },
            isCurrent = i == 18, // Psalms, as in the 12-book fixture
            readProgress = if (i < 2) 1f else 0f,
            memProgress = if (i == 18) 0.3f else 0f,
        )
    }

    private fun allBooksUi() = GridUi(
        GridStep.BOOK, "Choose Book (Workspace 1)", 6, showLongNames = false, showProgress = true,
        showDeutToggle = true, buttons = allBooks, minRows = 11,
    )

    /**
     * Round 15b Task 8: the passage grid as it renders INSIDE the reading view's quick sheet.
     *
     * Captured through the REAL shell — `AbQuickSheetContent` (header + `AbSheetScrollBound`'s
     * 400dp bound + bottom fade) inside `SheetSurface` — not a hand-rolled `Box(heightIn(...))`
     * stand-in, so what is under test is the production chrome the host branch actually composes.
     * `AbQuickSheet` itself is never captured: an open `ModalBottomSheet` hangs the Roborazzi run
     * and takes the whole `:app` suite with it. The overflow menu is passed to the header's actions
     * slot COLLAPSED (a `DropdownMenu` only opens a `Popup` when expanded), which is what this task
     * added and what had no golden anywhere.
     *
     * The fixture is [allBooksUi], not [bookUi], because the bound has to be VISIBLE to be proven.
     * 66 buttons / 6 columns = 11 laid-out rows; `cellHeight` is `max(40dp, (400 - 8) / 11)` =
     * `max(40, 35.6)` = 40dp, i.e. pinned on the floor, plus 2dp of padding a side = a 44dp row
     * pitch, plus the grid's own 4dp contentPadding a side = 11 * 44 + 8 = 492dp of content against
     * a 400dp bound. Nine rows fit (the ninth ends at y=446dp, 2dp inside the bound's 448dp bottom
     * edge) and rows TEN and ELEVEN — 2Tim..1Pet and 2Pet..Rev — are clipped away entirely. The
     * bottom fade washes the ninth row out over its last ~24dp.
     *
     * `canScrollForward` reads the REAL `LazyGridState` handed to [GridChoosePassageContent] below
     * (whole-branch review fix wave, I2) — not a hardcoded `true`. An earlier version of this test
     * hardcoded it, which proved the fade paints when told to without proving anything ever tells
     * it to in production; the production host (`ComposeReadingViewHost`'s `KeyChooserKind.Grid`
     * branch) was passing no `canScrollForward` at all and silently getting `AbQuickSheet`'s
     * `{ false }` default, so the fade never rendered there. Both are fixed together: the host now
     * holds its own `rememberLazyGridState()` and wires it through, exactly as this golden does.
     *
     * With `bookUi`'s twelve buttons (two laid-out rows, ~96dp) nothing overflows: the capture would
     * then be identical for ANY bound between 0 and ~448dp, so it could not detect a misapplied one.
     * The `minRows = 11` floor feeds only the cell-size arithmetic, never the number of rows the
     * `LazyVerticalGrid` emits — and since the floor pins `cellHeight` at 40dp both here and in the
     * full-screen `book_flat` capture, cell SIZE is not a signal in this golden. Clipping is.
     *
     * Height: header 48dp + bound 400dp + `AbQuickSheetContent`'s 16dp bottom padding = 464dp,
     * inside the harness's 470dp default viewport, so no `heightDp` override is needed (Task 4
     * needed one; this stack is 6dp short of the default).
     */
    @Test fun grid_sheet_book() = captureGolden("GridChoosePassage", "sheetBook", GoldenMode.LIGHT) {
        val ui = allBooksUi()
        SheetSurface {
            val gridState = rememberLazyGridState()
            AbQuickSheetContent(
                title = ui.title,
                onClose = {},
                actions = { GridOptionsOverflow(ui, opts) {} },
                // I2 (whole-branch review fix wave): read the REAL grid scroll state, the same
                // `LazyGridState` handed to `GridChoosePassageContent` below, rather than a
                // hardcoded `true` that proved the fade paints without proving anything wires it.
                canScrollForward = { gridState.canScrollForward },
            ) {
                GridChoosePassageContent(ui = ui, onPick = {}, modifier = Modifier.fillMaxSize(), state = gridState)
            }
        }
    }
}

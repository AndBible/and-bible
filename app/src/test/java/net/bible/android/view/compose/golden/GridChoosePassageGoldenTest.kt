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
    private val opts = GridOptions(showScripture = true, alphabetical = false, ltr = false, groupByCategory = false, longNames = false, showProgress = true)

    /**
     * A full 66-book grid, the fixture [grid_sheet_book] needs. Sixty-six buttons over six columns is
     * eleven laid out rows, which is what makes the sheet capture OVERFLOW its bound instead of merely
     * fitting inside it (arithmetic in [grid_sheet_book]'s kdoc). The full-screen grid's goldens are
     * gone with the screen (#4169): the passage grid is only ever shown in this sheet.
     *
     * `colorGroup` follows coarse OT/NT categories, so the
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
            isCurrent = i == 18, // Psalms
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
     * The fixture is [allBooksUi] because the bound has to be VISIBLE to be proven.
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
     * With twelve buttons (two laid-out rows, ~96dp) nothing would overflow: the capture would then be
     * identical for ANY bound between 0 and ~448dp, so it could not detect a misapplied one. Clipping is
     * the signal here.
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

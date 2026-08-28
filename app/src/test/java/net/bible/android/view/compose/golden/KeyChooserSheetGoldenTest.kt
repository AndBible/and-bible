package net.bible.android.view.compose.golden

import androidx.compose.foundation.lazy.rememberLazyListState
import net.bible.android.TEST_SDK
import net.bible.sharedcore.navigation.KeyRow
import net.bible.sharedui.components.AbQuickSheetContent
import net.bible.sharedui.navigation.KeyListBody
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
// @GraphicsMode NATIVE is REQUIRED: without it Roborazzi captures Robolectric's legacy-graphics
// dump of an empty ComposeView instead of a render, and every variant comes out byte-identical.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// Every golden test in this directory carries this @Config. Without it Robolectric boots the
// real BibleApplication and dies on an excluded requery-sqlite class.
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class KeyChooserSheetGoldenTest {
    private fun rows(n: Int) = (1..n).map { KeyRow(it.toString(), "Entry $it") }

    /**
     * Round 15b Task 9: the map / general-book key list as it renders INSIDE the reading view's
     * quick sheet.
     *
     * Captured through the REAL shell — `AbQuickSheetContent` (header + `AbSheetScrollBound`'s
     * 400dp bound + bottom fade) inside `SheetSurface` — not a hand-rolled `Box(heightIn(...))`
     * stand-in, so what is under test is the production chrome the host branch actually composes.
     * `AbQuickSheet` itself is never captured: an open `ModalBottomSheet` hangs the Roborazzi run
     * and takes the whole `:app` suite with it.
     *
     * THE FIXTURE HAS TO BE THIS BIG. A `KeyListBody` row is a `bodyLarge` line box (~24dp) plus
     * 14dp of padding a side ≈ 52dp, so the 400dp bound holds about seven and a half of them.
     * Twenty-four rows (≈1248dp) therefore overflow it by a wide margin, and the current row is
     * placed at index 8 — deep enough that `KeyListBody`'s initial `scrollToItem` is VISIBLE in the
     * capture: the list opens on "Entry 9", not on "Entry 1", and the rows above it are scrolled
     * out of the bound. That makes three separate properties falsifiable in one image —
     * current-row highlight, initial scroll, and clipping at the bound rather than compression —
     * where a fixture short enough to fit would render identically whether any of them worked.
     *
     * `canScrollForward` is wired to the SAME `LazyListState` the body scrolls, which is the whole
     * reason `KeyListBody` gained a `listState` parameter in this task: the host has to hand the
     * shell the body's own state or the bottom fade can never appear.
     *
     * Height: header 48dp + bound 400dp + `AbQuickSheetContent`'s 16dp bottom padding = 464dp,
     * inside the harness's 470dp default viewport, so no `heightDp` override is needed.
     */
    @Test fun keyChooser_sheetBody() = captureGolden("KeyChooser", "sheetBody", GoldenMode.LIGHT) {
        val listState = rememberLazyListState()
        SheetSurface {
            AbQuickSheetContent(
                title = "General book",
                onClose = {},
                canScrollForward = { listState.canScrollForward },
            ) {
                KeyListBody(rows = rows(24), currentKeyId = "9", onSelect = {}, listState = listState)
            }
        }
    }

    /**
     * The same sheet with a list far SHORTER than the bound — a five-entry map document.
     *
     * Second fixture on purpose, and not decoration: `KeyListBody` used to `fillMaxSize()` its own
     * `LazyColumn`, which under `AbSheetScrollBound`'s `heightIn(max = 400.dp)` sets
     * minHeight = maxHeight and pins the sheet at 400dp for five rows exactly as for fifty (the
     * defect `WorkspaceQuickContent`'s I2 review fix names). Task 9 moved that `fillMaxSize` out to
     * the two full-screen callers, and THIS capture is what would catch it coming back: with the
     * bug the image is a five-row list floating above ~140dp of dead surface, without it the sheet
     * ends just under the last row.
     */
    @Test fun keyChooser_sheetBodyShort() = captureGolden("KeyChooser", "sheetBodyShort", GoldenMode.LIGHT) {
        val listState = rememberLazyListState()
        SheetSurface {
            AbQuickSheetContent(
                title = "Map",
                onClose = {},
                canScrollForward = { listState.canScrollForward },
            ) {
                KeyListBody(rows = rows(5), currentKeyId = "2", onSelect = {}, listState = listState)
            }
        }
    }
}

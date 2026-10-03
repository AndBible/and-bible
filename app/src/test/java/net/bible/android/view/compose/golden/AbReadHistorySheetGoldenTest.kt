package net.bible.android.view.compose.golden

import net.bible.android.TEST_SDK
import net.bible.sharedui.progress.AbReadHistorySheetContent
import net.bible.sharedui.progress.ReadHistoryRow
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * G2.5's body (spec §3/§4). The dialog it replaces had NO goldens at all, and could not easily have
 * had one: it owned its own staged-deletion set, so a capture could only ever show the untouched
 * list. Hoisting that set into the wrapper is what makes this capture worth having — the third row
 * is staged for deletion, so the dimmed row plus its undo glyph are proven, which is the whole
 * point of the screen.
 *
 * One mode, matching the zero the dialog had: this pins the shape, not the theme matrix.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class AbReadHistorySheetGoldenTest {

    private val rows = listOf(
        ReadHistoryRow("h1", "Genesis 1-3", "12 Aug 2026, 24 verses"),
        ReadHistoryRow("h2", "Psalms 23", "11 Aug 2026, 6 verses"),
        ReadHistoryRow("h3", "Matthew 5", "10 Aug 2026, 48 verses"),
    )

    @Test fun readHistory_rows_light() = captureGolden("AbReadHistorySheet", "rows", EDGE_MODE, heightDp = 380) {
        SheetSurface {
            AbReadHistorySheetContent(
                title = "Reading history",
                rows = rows,
                pendingDelete = setOf("h3"),
                onTogglePending = {},
                onClose = {},
            )
        }
    }
}

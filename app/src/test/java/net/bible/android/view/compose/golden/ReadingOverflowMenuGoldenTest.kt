package net.bible.android.view.compose.golden

import androidx.compose.foundation.layout.Column
import net.bible.android.TEST_SDK
import net.bible.sharedcore.reading.OptionsMenuItem
import net.bible.sharedui.reading.ReadingOverflowMenuRows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Covers Batch 12b-C Task 1's [net.bible.sharedui.reading.ReadingOverflowMenu] (the reading-view
 * toolbar's overflow options menu).
 *
 * NOTE on capture approach: `ReadingOverflowMenu` itself is a thin wrapper around a Material3
 * `DropdownMenu` (a `Popup`, i.e. a second window). This golden instead captures
 * [ReadingOverflowMenuRows] - the exact same row-building composable `ReadingOverflowMenu` calls
 * internally, wrapped here in a plain `Column` instead of the real popup. `ReadingOverflowMenu`
 * has NO logic of its own beyond `DropdownMenu(expanded, onDismissRequest) { ReadingOverflowMenuRows(...) }`,
 * so this covers everything the golden could meaningfully check (row text/icon/enabled rendering)
 * without force-opening a real popup, which this repo's Robolectric/Roborazzi version has
 * repeatedly, intermittently HUNG on for the near-identical top-bar `AbOverflowMenu` (see
 * `AiPromptsGoldenTest`'s removed `configured_overflowOpen_*` goldens, and the dropped
 * per-row-overflow attempt noted there - both confirmed via jstack: the "Main Thread" spins in
 * `ShadowPausedLooper.idle()` under `captureScreenIfMultipleWindows` and never converges). Re-attempt
 * a real-popup golden only after a Roborazzi/Robolectric upgrade (or a per-test JVM fork) resolves
 * that hang; until then, the popup wrapper itself is proven by [net.bible.sharedui.components.AbOverflowMenu]'s
 * long production use (SearchScreen, WorkspaceSelectorScreen, MyDocumentsScreen, ...).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class ReadingOverflowMenuGoldenTest {

    /** Checked toggle, unchecked toggle, disabled toggle, and a dialog-opening row. */
    private val items = listOf(
        OptionsMenuItem(id = "nightMode", label = "Night mode", checkable = true, checked = true, enabled = true, opensDialog = false),
        OptionsMenuItem(id = "showBookmarks", label = "Show bookmarks", checkable = true, checked = false, enabled = true, opensDialog = false),
        OptionsMenuItem(id = "compareTranslations", label = "Compare translations", checkable = true, checked = false, enabled = false, opensDialog = false),
        OptionsMenuItem(id = "textOptions", label = "Text options", checkable = false, checked = false, enabled = true, opensDialog = true),
    )

    @Test fun items_matrix() =
        captureMatrix("ReadingOverflowMenu", "items") {
            Column { ReadingOverflowMenuRows(items, onItemClick = {}) }
        }
}

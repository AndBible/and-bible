package net.bible.android.view.compose.golden

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import net.bible.android.TEST_SDK
import net.bible.sharedcore.window.WindowPaneMenuItem
import net.bible.sharedui.reading.WindowPaneMenuRows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Covers Batch 12b-followon-B Task 1's [net.bible.sharedui.reading.WindowPaneMenu] (the per-window
 * ☰ pane popup menu), including its in-place submenu navigation.
 *
 * NOTE on capture approach: same rationale as [ReadingOverflowMenuGoldenTest] (see its doc for the
 * force-opened-`DropdownMenu`-hangs-Roborazzi background) — this golden instead captures
 * [WindowPaneMenuRows], the exact row-building composable `WindowPaneMenu` calls internally for
 * whichever level is current, wrapped here in a plain `Column` instead of the real popup. Two
 * distinct levels are captured directly (by passing that level's item list straight to
 * [WindowPaneMenuRows]) rather than by driving the popup's internal navigation state: the root
 * level (`showBack = false`) and one submenu level (`showBack = true`, showing the "‹ Back" row).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class WindowPaneMenuGoldenTest {

    /** The "Synchronise" row's submenu: a small sync-group choice list, all checkable. */
    private val syncSubmenu = listOf(
        WindowPaneMenuItem(id = "syncGroupNone", label = "None", checkable = true, checked = false),
        WindowPaneMenuItem(id = "syncGroup1", label = "Group 1", checkable = true, checked = true),
        WindowPaneMenuItem(id = "syncGroup2", label = "Group 2", checkable = true, checked = false),
    )

    /** Checked toggle, unchecked toggle, a disabled row, a dialog row, and a submenu row. */
    private val rootItems = listOf(
        WindowPaneMenuItem(id = "pinMode", label = "Pinned", checkable = true, checked = true),
        WindowPaneMenuItem(id = "fullScreen", label = "Full screen", checkable = true, checked = false),
        WindowPaneMenuItem(id = "moveFirst", label = "Move to first", enabled = false),
        WindowPaneMenuItem(id = "windowSettings", label = "Window settings", opensDialog = true),
        WindowPaneMenuItem(id = "syncGroupSubMenu", label = "Synchronise", submenu = syncSubmenu),
    )

    private fun rootRows(): @Composable () -> Unit = {
        Column {
            WindowPaneMenuRows(
                items = rootItems, showBack = false,
                onBack = {}, onEnterSubmenu = {}, onItemClick = {},
            )
        }
    }

    @Test fun items_matrix() = captureMatrix("WindowPaneMenu", "items", content = rootRows())

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "ar")
    fun items_rtl() = captureRtl("WindowPaneMenu", "items", content = rootRows())

    @Test fun submenu() =
        captureGolden("WindowPaneMenu", "submenu", EDGE_MODE) {
            Column {
                WindowPaneMenuRows(
                    items = syncSubmenu, showBack = true,
                    onBack = {}, onEnterSubmenu = {}, onItemClick = {},
                )
            }
        }
}

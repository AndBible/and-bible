package net.bible.android.view.compose.golden

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import net.bible.android.TEST_SDK
import net.bible.android.activity.R
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
 *
 * `rootItems` mixes iconed and iconless rows (A/B batch 1 F5a) the way classic's own
 * `window_popup_menu.xml` does across levels: every real top-level row there carries an icon, so
 * `moveFirst` (disabled, no icon) is a synthetic stand-in to keep the iconless code path covered,
 * not a claim that a real top-level row lacks one. `syncSubmenu` mirrors the real
 * `syncGroupItem`/`disableSync` rows, which classic never gives an icon at all - `syncGroupNone`
 * stays iconless for that reason, with `syncGroup1`/`syncGroup2` given an icon purely for this
 * golden's coverage of "icon rendering inside a submenu level" too.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class WindowPaneMenuGoldenTest {

    /** The "Synchronise" row's submenu: a small sync-group choice list, all checkable. */
    private val syncSubmenu = listOf(
        WindowPaneMenuItem(id = "syncGroupNone", label = "None", checkable = true, checked = false),
        WindowPaneMenuItem(id = "syncGroup1", label = "Group 1", checkable = true, checked = true, iconKey = "sync"),
        WindowPaneMenuItem(id = "syncGroup2", label = "Group 2", checkable = true, checked = false, iconKey = "sync"),
    )

    /** Checked toggle, unchecked toggle, a disabled row, a dialog row, and a submenu row. */
    private val rootItems = listOf(
        WindowPaneMenuItem(id = "pinMode", label = "Pinned", checkable = true, checked = true, iconKey = "pin"),
        WindowPaneMenuItem(id = "fullScreen", label = "Full screen", checkable = true, checked = false, iconKey = "fullScreen"),
        WindowPaneMenuItem(id = "moveFirst", label = "Move to first", enabled = false),
        WindowPaneMenuItem(id = "windowSettings", label = "Window settings", opensDialog = true, iconKey = "textOptions"),
        WindowPaneMenuItem(id = "syncGroupSubMenu", label = "Synchronise", submenu = syncSubmenu, iconKey = "sync"),
    )

    /** Resolves the sample [iconKey]s above to real drawables, following [ReadingToolbarGoldenTest]'s style. */
    @Composable
    private fun icon(iconKey: String): Painter? = when (iconKey) {
        "pin" -> painterResource(R.drawable.ic_pin)
        "fullScreen" -> painterResource(R.drawable.ic_full_screen_24)
        "textOptions" -> painterResource(R.drawable.ic_text_options_24dp)
        "sync" -> painterResource(R.drawable.ic_window_sync_24dp)
        else -> null
    }

    private fun rootRows(): @Composable () -> Unit = {
        Column {
            WindowPaneMenuRows(
                items = rootItems, showBack = false,
                onBack = {}, onEnterSubmenu = {}, onItemClick = {},
                icon = { key -> icon(key) },
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
                    icon = { key -> icon(key) },
                )
            }
        }
}

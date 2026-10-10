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
 * `rootItems` mixes iconed and iconless rows (A/B batch 1 F5a): `moveFirst` (disabled, no icon)
 * keeps the iconless code path covered. `syncSubmenu` mirrors the real `syncGroupItem`/
 * `disableSync` rows, which classic never gives an icon at all - `syncGroupNone` stays iconless
 * for that reason, with `syncGroup1`/`syncGroup2` given an icon purely for this golden's coverage
 * of "icon rendering inside a submenu level" too. Both lists therefore already exercise the
 * per-level [net.bible.sharedui.reading.WindowPaneMenuRows] icon-slot reservation fix (F5a fix
 * round 1): since at least one row per level resolves an icon, EVERY row at that level - including
 * the iconless one - reserves the same leading slot, so labels stay aligned on one left edge
 * (classic's `MenuPopupHelper.setForceShowIcon(true)` + `ListMenuItemView`'s `INVISIBLE`, not
 * `GONE`, icon view for icon-less rows achieves the same alignment - see `WindowPaneMenuRows`'s
 * kdoc for verified call-site line numbers). This is not merely a synthetic golden-test
 * convenience: [net.bible.android.view.activity.page.OptionsMenuStateBuilder.build] appends
 * icon-less dynamic `textOptionItem` rows into the SAME flat list as the nine iconed static
 * entries, so the real reading-view overflow menu mixes icon/iconless rows at its one level
 * whenever the user has any display-setting history (see `ReadingOverflowMenuGoldenTest`'s doc).
 *
 * [iconAlignment] is a dedicated, maximally-legible case for that fix: an evenly alternating
 * icon/no-icon/icon/no-icon list makes the reserved column trivially easy to eyeball in the PNG,
 * which `rootItems`/`syncSubmenu` (only one iconless row each) demonstrate less clearly.
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
        captureGolden("WindowPaneMenu", "submenu", EDGE_MODE, content = submenuContent())

    /** Alternating icon/no-icon/icon/no-icon: the clearest possible proof the reserved column aligns. */
    private val alternatingIconItems = listOf(
        WindowPaneMenuItem(id = "hasIconA", label = "Has icon A", iconKey = "pin"),
        WindowPaneMenuItem(id = "noIconB", label = "No icon B"),
        WindowPaneMenuItem(id = "hasIconC", label = "Has icon C", iconKey = "sync"),
        WindowPaneMenuItem(id = "noIconD", label = "No icon D"),
    )

    @Test fun iconAlignment() =
        captureGolden("WindowPaneMenu", "iconAlignment", EDGE_MODE, content = iconAlignmentContent())

    private fun submenuContent(): @androidx.compose.runtime.Composable () -> Unit = {
            Column {
                WindowPaneMenuRows(
                    items = syncSubmenu, showBack = true,
                    onBack = {}, onEnterSubmenu = {}, onItemClick = {},
                    icon = { key -> icon(key) },
                )
            }
        }

    private fun iconAlignmentContent(): @androidx.compose.runtime.Composable () -> Unit = {
            Column {
                WindowPaneMenuRows(
                    items = alternatingIconItems, showBack = false,
                    onBack = {}, onEnterSubmenu = {}, onItemClick = {},
                    icon = { key -> icon(key) },
                )
            }
        }

    @Test
    fun items_mono() {
        MONO_MODES.forEach { mode -> captureGolden("WindowPaneMenu", "items", mode, content = rootRows()) }
    }

    @Test
    fun submenu_mono() {
        MONO_MODES.forEach { mode -> captureGolden("WindowPaneMenu", "submenu", mode, content = submenuContent()) }
    }

    @Test
    fun iconAlignment_mono() {
        MONO_MODES.forEach { mode -> captureGolden("WindowPaneMenu", "iconAlignment", mode, content = iconAlignmentContent()) }
    }
}

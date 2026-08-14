package net.bible.android.view.compose.golden

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import net.bible.android.TEST_SDK
import net.bible.android.activity.R
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
 *
 * `items` mixes iconed and iconless rows (A/B batch 1 F5a) — and this one is NOT just a synthetic
 * golden-test convenience: while classic's static `main_bible_options_menu.xml` gives all 9 of its
 * items an icon, [net.bible.android.view.activity.page.OptionsMenuStateBuilder.build] appends the
 * dynamic, icon-less `textOptionItem:<order>` rows into the SAME flat list the nine statics live
 * in, so the real overflow menu mixes icon and icon-less rows at its one level whenever the user
 * has any display-setting history (`CommonUtils.lastDisplaySettingsSorted` non-empty).
 * `compareTranslations` (disabled, no icon) stands in for that dynamic row here — the id/label are
 * synthetic, but the "icon-less row alongside iconed ones" shape it tests is real. This means
 * [net.bible.sharedui.reading.ReadingOverflowMenuRows]' per-level icon-slot reservation fix (F5a
 * fix round 1) is exercised here for real, not hypothetically: since some rows resolve an icon,
 * EVERY row - including `compareTranslations` - now reserves the same leading slot, keeping labels
 * aligned on one left edge, matching classic's `MenuPopupHelper.setForceShowIcon(true)` +
 * `ListMenuItemView`'s `INVISIBLE` (not `GONE`) icon view for icon-less rows (see
 * `ReadingOverflowMenuRows`'s kdoc for verified `setForceShowIcon` call-site line numbers).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class ReadingOverflowMenuGoldenTest {

    /** Checked toggle, unchecked toggle, disabled toggle, and a dialog-opening row. */
    private val items = listOf(
        OptionsMenuItem(id = "nightMode", label = "Night mode", checkable = true, checked = true, enabled = true, opensDialog = false, iconKey = "nightMode"),
        OptionsMenuItem(id = "showBookmarks", label = "Show bookmarks", checkable = true, checked = false, enabled = true, opensDialog = false, iconKey = "bookmarks"),
        OptionsMenuItem(id = "compareTranslations", label = "Compare translations", checkable = true, checked = false, enabled = false, opensDialog = false),
        OptionsMenuItem(id = "textOptions", label = "Text options", checkable = false, checked = false, enabled = true, opensDialog = true, iconKey = "textOptions"),
    )

    /**
     * Batch 5 F1: static rows, then a recent-settings section (divider above its first row), then
     * `allTextOptions` last (no divider of its own here, since the recent section already carries
     * one) — the real shape [net.bible.android.view.activity.page.OptionsMenuStateBuilder.build]
     * produces once the user has any display-setting history.
     */
    private val itemsWithRecentSection = listOf(
        OptionsMenuItem(id = "nightMode", label = "Night mode", checkable = true, checked = true, enabled = true, opensDialog = false, iconKey = "nightMode"),
        OptionsMenuItem(id = "showBookmarks", label = "Show bookmarks", checkable = true, checked = false, enabled = true, opensDialog = false, iconKey = "bookmarks"),
        OptionsMenuItem(id = "textOptionItem:0", label = "Font size", checkable = false, checked = false, enabled = true, opensDialog = false, startsNewSection = true),
        OptionsMenuItem(id = "textOptionItem:1", label = "Margin size", checkable = false, checked = false, enabled = true, opensDialog = false),
        OptionsMenuItem(id = "allTextOptions", label = "All text options", checkable = false, checked = false, enabled = true, opensDialog = true, iconKey = "textOptions"),
    )

    /** Resolves the sample [iconKey]s above to real drawables, following [ReadingToolbarGoldenTest]'s style. */
    @Composable
    private fun icon(iconKey: String): Painter? = when (iconKey) {
        "nightMode" -> painterResource(R.drawable.ic_night_mode_24)
        "bookmarks" -> painterResource(R.drawable.ic_baseline_bookmark_24)
        "textOptions" -> painterResource(R.drawable.ic_text_options_24dp)
        else -> null
    }

    @Test fun items_matrix() =
        captureMatrix("ReadingOverflowMenu", "items") {
            Column { ReadingOverflowMenuRows(items, onItemClick = {}, icon = { key -> icon(key) }) }
        }

    @Test fun recentSection_matrix() =
        captureMatrix("ReadingOverflowMenu", "recentSection") {
            Column { ReadingOverflowMenuRows(itemsWithRecentSection, onItemClick = {}, icon = { key -> icon(key) }) }
        }
}

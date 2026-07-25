package net.bible.sharedcore.reading

/**
 * A single row of the reading-view toolbar's overflow ("3-dot") options menu, ported from classic
 * `MainBibleActivity`'s `onCreateOptionsMenu`/`onPrepareOptionsMenu` (`R.menu.main_options_menu`).
 *
 * [id] identifies the menu action (mirrors the classic menu item's resource-id name, e.g.
 * "searchButton", "showBookmarksButton") and is what id-keyed click dispatch in the host switches
 * on - see Task 3. [checkable]/[checked] mirror an `android:checkable="true"` menu item (e.g.
 * night-mode / show-bookmarks toggles); [opensDialog] marks an item that opens a further
 * dialog/sub-screen rather than acting immediately (rendered with a trailing "...", matching the
 * classic menu's "..."-suffixed labels for e.g. "Text options...", "Speak...").
 */
data class OptionsMenuItem(
    val id: String,
    val label: String,
    val checkable: Boolean,
    val checked: Boolean,
    val enabled: Boolean,
    val opensDialog: Boolean,
    /**
     * Stable icon key for the leading glyph, resolved to a `Painter` by the host — the same seam
     * `DrawerItem.iconKey`/`ReadingDrawerContent`'s `icon` lambda already uses (the host maps each
     * key to an `R.drawable` id via a table like `ComposeReadingViewHost.drawerIconResIds`; the
     * per-id table for this menu is a later task). A string, not a resource id, so this module
     * stays iOS-clean. `null` = no icon, matching classic's iconless `main_bible_options_menu.xml`
     * items (there are none today - every item there carries an icon - but the field stays
     * optional for parity with [net.bible.sharedcore.window.WindowPaneMenuItem]).
     */
    val iconKey: String? = null,
)

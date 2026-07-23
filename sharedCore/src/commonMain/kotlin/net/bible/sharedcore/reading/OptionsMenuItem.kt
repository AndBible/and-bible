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
)

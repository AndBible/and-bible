/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */

package net.bible.android.view.activity.page

import net.bible.android.activity.R
import net.bible.service.common.CommonUtils
import net.bible.sharedcore.reading.OptionsMenuItem

/**
 * Builds the Compose reading-view toolbar's overflow ("3-dot") options menu item list, and
 * dispatches a click on one of those items — the Compose-side counterpart of classic
 * `MainBibleActivity.showOptionsMenu()`/`handlePrefItem()` (`R.menu.main_bible_options_menu`),
 * ported for Batch 12b-C (Task 3).
 *
 * [build] and [dispatch] both take the real `getItemOptions(itemId, order)` lookup as a
 * parameter rather than calling it directly: the actual implementation
 * ([net.bible.android.view.activity.page.MainBibleActivity]'s `getItemOptions`) is `private`
 * (it closes over the enclosing activity's `windowRepository`/`llmDialogHelper`/etc. to build
 * each [OptionsMenuItemInterface]) — `MainBibleActivity.buildOptionsMenuItems()`/
 * `handleOptionsMenuItem(id)` are the thin bridges that close over it (from inside the class body,
 * where the private access is legal) and forward here. This keeps the actual build/dispatch logic
 * — and the single id<->(resId, order) mapping ([idFor]/[parseId]) — in ONE place, reused by both
 * bridges, rather than duplicated.
 */
object OptionsMenuStateBuilder {

    /**
     * The static ids from `R.menu.main_bible_options_menu`, in menu-declaration order (the
     * group's `allTextOptions` last). Mirrors classic `showOptionsMenu`'s inflate of that XML.
     * The dynamic `textOptionItem` rows (one per [CommonUtils.lastDisplaySettingsSorted] index —
     * see [build]) are appended after all of these.
     */
    private data class StaticEntry(val resId: Int, val idName: String, val titleRes: Int)

    private val staticEntries: List<StaticEntry> = listOf(
        StaticEntry(R.id.fullscreen, "fullscreen", R.string.toggle_fullscreen),
        StaticEntry(R.id.nightMode, "nightMode", R.string.options_menu_night_mode),
        StaticEntry(R.id.switchToWorkspace, "switchToWorkspace", R.string.switch_to_workspace),
        StaticEntry(R.id.tiltToScroll, "tiltToScroll", R.string.prefs_tilt_to_scroll_title),
        StaticEntry(R.id.splitMode, "splitMode", R.string.reversed_split_mode),
        StaticEntry(R.id.autoPinMode, "autoPinMode", R.string.window_pinning_menutitle),
        StaticEntry(R.id.autoAssignLabels, "autoAssignLabels", R.string.auto_assign_labels_title),
        StaticEntry(R.id.llmActionsSubMenu, "llmActionsSubMenu", R.string.llm_actions),
        StaticEntry(R.id.allTextOptions, "allTextOptions", R.string.all_text_options_window_menutitle),
    )
    private val staticEntryByResId: Map<Int, StaticEntry> = staticEntries.associateBy { it.resId }
    private val staticEntryByName: Map<String, StaticEntry> = staticEntries.associateBy { it.idName }

    /** Prefix for a dynamic `textOptionItem` row's id — see [idFor]/[parseId]. */
    private const val TEXT_OPTION_PREFIX = "textOptionItem:"

    /** A parsed [OptionsMenuItem.id]: the `(itemId, order)` pair `getItemOptions` takes. */
    data class ParsedId(val resId: Int, val order: Int)

    /**
     * The [OptionsMenuItem.id] for a given `(resId, order)`: the static entry's XML id name (e.g.
     * `"fullscreen"`), or `"textOptionItem:<order>"` for a dynamic row. [parseId] reverses this —
     * keep both in sync (they share [staticEntries]/[TEXT_OPTION_PREFIX] as their single source of
     * truth).
     */
    fun idFor(resId: Int, order: Int): String =
        if (resId == R.id.textOptionItem) "$TEXT_OPTION_PREFIX$order"
        else staticEntryByResId.getValue(resId).idName

    /** Reverses [idFor]. Throws on an unknown id (e.g. a stale click after the item list changed underneath it). */
    fun parseId(id: String): ParsedId {
        if (id.startsWith(TEXT_OPTION_PREFIX)) {
            val order = id.removePrefix(TEXT_OPTION_PREFIX).toInt()
            return ParsedId(R.id.textOptionItem, order)
        }
        val entry = staticEntryByName[id] ?: throw IllegalArgumentException("Unknown options-menu item id: $id")
        return ParsedId(entry.resId, 0)
    }

    /**
     * Reproduces `MainBibleActivity.showOptionsMenu`'s build loop: every visible static entry (in
     * [staticEntries] order) followed by one row per [CommonUtils.lastDisplaySettingsSorted]
     * index — pre-filtered on `enabled && visible`, exactly like the classic loop that only
     * `menu.add`s a dynamic row passing that same check.
     *
     * A static entry's [OptionsMenuItemInterface.title] is always `null` (unlike the dynamic
     * `textOptionItem` rows, built from [Preference], which DO carry a type-specific title, none
     * of the static entries' model classes — [CommandPreference]/[GeneralPreference] subclasses —
     * override `title`; classic relies on the already-inflated `MenuItem`'s XML `android:title` in
     * that case, which doesn't exist on this Compose path) — so [staticEntries]' [StaticEntry.titleRes]
     * (the exact string resource each XML `<item>` declares) is used as the label instead.
     */
    fun build(getItemOptions: (resId: Int, order: Int) -> OptionsMenuItemInterface): List<OptionsMenuItem> {
        val items = mutableListOf<OptionsMenuItem>()
        for (entry in staticEntries) {
            val m = getItemOptions(entry.resId, 0)
            if (!m.visible) continue
            items += OptionsMenuItem(
                id = entry.idName,
                label = m.title ?: application.getString(entry.titleRes),
                checkable = m.isBoolean,
                checked = m.value == true,
                enabled = m.enabled,
                opensDialog = m.opensDialog,
            )
        }
        for ((order, _) in CommonUtils.lastDisplaySettingsSorted.withIndex()) {
            val m = getItemOptions(R.id.textOptionItem, order)
            if (!(m.enabled && m.visible)) continue
            items += OptionsMenuItem(
                id = idFor(R.id.textOptionItem, order),
                label = m.title ?: "",
                checkable = m.isBoolean,
                checked = m.value == true,
                enabled = m.enabled,
                opensDialog = m.opensDialog,
            )
        }
        return items
    }

    /**
     * Reproduces `MainBibleActivity.handlePrefItem`, minus the `MenuItem.isChecked` UI write (no
     * `MenuItem` exists on this path — the host rebuilds the whole item list instead, see
     * `MainBibleActivity.handleOptionsMenuItem`). [activity] is needed for `windowRepository`
     * (public) and as the [net.bible.android.view.activity.base.ActivityBase] receiver
     * `openDialog` requires — NOT for anything private, so this stays free of any access-widening
     * beyond the [getItemOptions] closure itself.
     *
     * Returns whether the menu should stay open: `true` for a boolean toggle (so the host can
     * rebuild the list and show the flipped check), `false` once a dialog/activity/action has been
     * launched, or for the (practically unreachable via [build]'s item set) [SubMenuPreference]
     * no-op case.
     */
    fun dispatch(
        activity: MainBibleActivity,
        getItemOptions: (resId: Int, order: Int) -> OptionsMenuItemInterface,
        id: String,
    ): Boolean {
        val (resId, order) = parseId(id)
        val itemOptions = getItemOptions(resId, order)
        if (itemOptions is SubMenuPreference) return false
        return if (itemOptions.isBoolean) {
            itemOptions.value = itemOptions.value != true
            itemOptions.handle()
            if (itemOptions is Preference) {
                activity.windowRepository.updateWindowTextDisplaySettingsValues(
                    setOf(itemOptions.type), activity.windowRepository.textDisplaySettings)
            }
            true
        } else {
            val onReady = {
                if (itemOptions is Preference) {
                    activity.windowRepository.updateWindowTextDisplaySettingsValues(
                        setOf(itemOptions.type), activity.windowRepository.textDisplaySettings)
                }
                activity.windowRepository.updateAllWindowsTextDisplaySettings()
            }
            itemOptions.openDialog(activity, { onReady() }, { onReady() })
            false
        }
    }
}

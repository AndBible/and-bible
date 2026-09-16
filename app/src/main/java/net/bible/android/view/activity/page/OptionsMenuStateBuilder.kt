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
import net.bible.sharedcore.settings.textSettingEditorPageFor

/**
 * Builds the Compose reading-view toolbar's overflow ("3-dot") options menu item list, and
 * dispatches a click on one of those items — the Compose-side counterpart of classic
 * `MainBibleActivity.showOptionsMenu()`/`handlePrefItem()` (`R.menu.main_bible_options_menu`),
 * ported for Batch 12b-C (Task 3).
 *
 * Those two classic functions were DELETED in nav-graph slice 7 Task 2 (their last live caller, the
 * `"AltKeyO"` shortcut, now opens `ComposeReadingViewHost.openOverflowMenu()`), so this object is
 * the only runtime implementation left. `R.menu.main_bible_options_menu` survives until the classic
 * layout goes, and nothing reads it any more — `ReadingOptionsMenuTest` compares [staticItemIds]
 * against it so the two cannot drift apart unnoticed.
 *
 * [build] and [dispatch] both take the real `getItemOptions(itemId, order)` lookup as a
 * parameter rather than calling it directly: the actual implementation is `private`
 * (it closes over the host's `windowRepository`/`llmDialogHelper`/etc. to build
 * each [OptionsMenuItemInterface]) — `buildOptionsMenuItems()`/`handleOptionsMenuItem(id)` are the
 * thin bridges that close over it (from inside the declaring class body, where the private access is
 * legal) and forward here. This keeps the actual build/dispatch logic — and the single
 * id<->(resId, order) mapping ([idFor]/[parseId]) — in ONE place, reused by both bridges, rather
 * than duplicated.
 *
 * Reading-host re-typing R3 (design spec §3.2) moved `getItemOptions` and both bridges off
 * `MainBibleActivity` into [net.bible.android.view.activity.page.ReadingCommands]; the Activity
 * keeps delegating stubs of the same names, which is what `OptionsMenuStateBuilderTest` and
 * `ReadingOptionsMenuTest` drive. [dispatch]'s [activity] parameter is deliberately UNCHANGED at
 * this task (controller ruling C-5): re-typing it belongs to R6, with the rest of the seam.
 */
object OptionsMenuStateBuilder {

    /**
     * The static ids from `R.menu.main_bible_options_menu`, in menu-declaration order — EXCEPT
     * `allTextOptions`, which [build] emits last of all (after the dynamic `textOptionItem` rows,
     * one per [CommonUtils.lastDisplaySettingsSorted] index), matching classic's rendered order
     * (`android:orderInCategory="1000"` inside `textOptionsGroup`) rather than the XML's
     * declaration order. See [build]'s kdoc for the full rendered ordering and the section-divider
     * rule.
     */
    private data class StaticEntry(val resId: Int, val idName: String, val titleRes: Int, val iconKey: String?)

    // iconKey values below are classic's own `android:icon` for the same item id in
    // `res/menu/main_bible_options_menu.xml` (classic force-shows them, MainBibleActivity.kt:1417).
    // Every static entry here happens to carry an icon in that XML today (see class kdoc).
    private val staticEntries: List<StaticEntry> = listOf(
        StaticEntry(R.id.fullscreen, "fullscreen", R.string.toggle_fullscreen, "ic_full_screen_24"),
        StaticEntry(R.id.nightMode, "nightMode", R.string.options_menu_night_mode, "ic_night_mode_24"),
        StaticEntry(R.id.switchToWorkspace, "switchToWorkspace", R.string.switch_to_workspace, "ic_baseline_workspace_24"),
        StaticEntry(R.id.tiltToScroll, "tiltToScroll", R.string.prefs_tilt_to_scroll_title, "ic_tilt_to_scroll_24dp"),
        StaticEntry(R.id.splitMode, "splitMode", R.string.reversed_split_mode, "ic_reverse_split_mode_24dp"),
        StaticEntry(R.id.autoPinMode, "autoPinMode", R.string.window_pinning_menutitle, "ic_window_pinning_24"),
        StaticEntry(R.id.autoAssignLabels, "autoAssignLabels", R.string.auto_assign_labels_title, "ic_label_settings_24"),
        StaticEntry(R.id.llmActionsSubMenu, "llmActionsSubMenu", R.string.llm_actions, "icon_robot"),
        StaticEntry(R.id.allTextOptions, "allTextOptions", R.string.all_text_options_window_menutitle, "ic_text_options_24dp"),
    )
    /**
     * Every static (XML-backed) item id this builder knows, in `R.menu.main_bible_options_menu`'s
     * *declaration* order. Exposed so `ReadingOptionsMenuTest` can compare it against that XML — the
     * menu resource is still the contract until it is deleted, but nothing reads it at runtime any
     * more, so nothing else would notice the two drifting apart. Note this is NOT [build]'s emit
     * order: [build] moves `allTextOptions` to the end (see its kdoc).
     */
    val staticItemIds: List<String> get() = staticEntries.map { it.idName }

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
     * Reproduces classic's *rendered* order (not `showOptionsMenu`'s inflate order): every visible
     * static entry except `allTextOptions` (in [staticEntries] order), then one row per
     * [CommonUtils.lastDisplaySettingsSorted] index — pre-filtered on `enabled && visible`, exactly
     * like the classic loop that only `menu.add`s a dynamic row passing that same check — then
     * `allTextOptions` last, with [OptionsMenuItem.startsNewSection] marking the first row of that
     * trailing group (the recent-settings rows if any exist, else `allTextOptions` itself) so the
     * renderer can draw a divider there.
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
            // Emitted last instead, below the recent rows — classic orders it there via
            // android:orderInCategory="1000" inside textOptionsGroup.
            if (entry.resId == R.id.allTextOptions) continue
            val m = getItemOptions(entry.resId, 0)
            if (!m.visible) continue
            items += OptionsMenuItem(
                id = entry.idName,
                label = m.title ?: application.getString(entry.titleRes),
                checkable = m.isBoolean,
                checked = m.value == true,
                enabled = m.enabled,
                opensDialog = m.opensDialog,
                iconKey = entry.iconKey,
            )
        }
        var isFirstOfSection = true
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
                // A/B batch 3 F4: classic draws these rows with the setting's own icon
                // (`ItemPreference.icon`); the port shipped them iconless.
                iconKey = m.icon?.let { application.resources.getResourceEntryName(it) },
                startsNewSection = isFirstOfSection,
            )
            isFirstOfSection = false
        }
        val allTextOptions = staticEntryByResId.getValue(R.id.allTextOptions)
        val m = getItemOptions(allTextOptions.resId, 0)
        if (m.visible) {
            items += OptionsMenuItem(
                id = allTextOptions.idName,
                label = m.title ?: application.getString(allTextOptions.titleRes),
                checkable = m.isBoolean,
                checked = m.value == true,
                enabled = m.enabled,
                opensDialog = m.opensDialog,
                iconKey = allTextOptions.iconKey,
                // Carries the divider itself when there are no recent rows to carry it.
                startsNewSection = isFirstOfSection,
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
     * Settings editor sheets T11: for a [Preference] whose type
     * [net.bible.sharedcore.settings.textSettingEditorPageFor] maps to a page — one of the eight
     * sheet-editable text display settings, plus colours — and only when [activity]'s
     * `composeReadingViewHost` is installed, this opens that page IN PLACE over the reading view
     * (`ComposeReadingViewHost.showTextSettingEditor`) instead of calling `openDialog`, reusing the
     * exact same `onReady` closure `openDialog` would otherwise have received. Everything else —
     * every boolean toggle (handled above, before this function ever reaches this point),
     * `CommandPreference`/`AutoAssignPreference`, HIDELABELS, and every type
     * [net.bible.sharedcore.settings.textSettingEditorPageFor] does not name — still calls
     * `openDialog` unchanged.
     *
     * Returns whether the menu should stay open: `true` for a boolean toggle (so the host can
     * rebuild the list and show the flipped check), `false` once a sheet/dialog/activity/action
     * has been launched, or for the (practically unreachable via [build]'s item set)
     * [SubMenuPreference] no-op case.
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
            // The eight sheet-editable text display settings are edited IN PLACE over the reading
            // view — no activity launch, so back cannot land anywhere but the reading view.
            // Everything else (CommandPreference, AutoAssignPreference, HIDELABELS, and every type
            // textSettingEditorPageFor does not name) falls through to openDialog unchanged: `page`
            // is genuinely null for those, so this fall-through stays live.
            val host = activity.composeReadingViewHost
            val page = (itemOptions as? Preference)?.let { textSettingEditorPageFor(it.type.name) }
            if (page != null && host != null) {
                host.showTextSettingEditor((itemOptions as Preference).settings.toScope(), page, onReady)
                return false
            }
            itemOptions.openDialog(activity, { onReady() }, { onReady() })
            false
        }
    }
}

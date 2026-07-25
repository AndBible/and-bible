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
import net.bible.android.control.page.MultiFragmentDocument
import net.bible.android.control.page.MyNotesDocument
import net.bible.android.control.page.StudyPadDocument
import net.bible.android.control.page.window.Window
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.page.window.WindowRepository
import net.bible.android.control.speak.SpeakControl
import net.bible.android.database.SettingsBundle
import net.bible.android.database.SettingsLevel
import net.bible.android.view.activity.page.screen.clipboardKey
import net.bible.android.view.activity.settings.getPrefItem
import net.bible.service.common.CommonUtils
import net.bible.service.common.shortName
import net.bible.service.download.isSpecial
import net.bible.sharedcore.window.WindowPaneMenuItem
import org.crosswire.jsword.book.BookCategory

/**
 * Builds the Compose per-window (☰) pane popup menu's item list — the Compose-side counterpart
 * of classic `SplitBibleArea.showPopupMenu()`/`getItemOptions()` (`R.menu.window_popup_menu`),
 * ported for Batch 12b follow-on Plan B (Task 4).
 *
 * Every row below reproduces the corresponding classic `SplitBibleArea.getItemOptions` branch's
 * `visible`/`enabled`/`isBoolean`(→checkable+checked) semantics inline (a `SplitBibleArea.kt:NNN`
 * comment marks each one) since that method is `private` and closes over a live `View`/`MenuItem`
 * — there is no clean seam to call it from here (same "reproduce, don't fork" discipline as
 * [OptionsMenuStateBuilder], where the classic method happened to be reachable as a closure
 * instead). Two rows genuinely REUSE the classic model rather than reproducing it: the dynamic
 * `textOptionItem` children call the real (public, side-effect-free) top-level
 * `net.bible.android.view.activity.settings.getPrefItem`, and `goToReference`'s visibility/title
 * reads the real shared `net.bible.android.view.activity.page.screen.clipboardKey`.
 *
 * **`opensDialog` is repurposed here** (design spec
 * `docs/superpowers/specs/2026-07-23-compose-batch12b-followon-window-management-design.md` §5):
 * unlike [OptionsMenuStateBuilder] (where it mirrors classic's own per-item flag verbatim), every
 * row in this builder's "bridge to existing native handlers" set (§5 of that spec) is marked
 * `opensDialog = true` regardless of classic's literal value — it doubles as the "this row is
 * still an interim bridge into native Android UI, not a native-in-Compose action" signal, which
 * is what the host dispatcher (Task 5) keys its own bridge-vs-atomic split on. Only the
 * "native-in-Compose now" rows (`windowNew`, `windowMaximise`, `windowMinimise`, `changeToNormal`,
 * `pinMode`, `moveItem`, `syncGroupItem`/`disableSync`, `windowClose`) are `opensDialog = false`.
 *
 * **`copySettingsToWindow`/`Workspace`/`Global` are flattened.** Classic nests them two levels
 * deep (`textOptionsSubMenu` > `copySettingsTo` > one `copySettingsToWindow` row PER other visible
 * window, `SplitBibleArea.kt:778-803`). This Compose menu exposes them as three flat top-level
 * rows instead (per the id scheme in the Task-4 brief, which lists them as static — not dynamic
 * per-window — ids); `copySettingsToWindow` is visible only when there is at least one other
 * visible window to copy to, and the actual target-window resolution is the host dispatcher's
 * job (Task 5), not this builder's.
 *
 * **`textOptionsSubMenu`'s collapse-to-`allTextOptions`** mirrors classic `showPopupMenu`
 * (`SplitBibleArea.kt:808-820`): when there is no last-used display setting, classic removes the
 * whole submenu and adds a flat top-level `allTextOptions` item instead. This builder expresses
 * the same swap as `textOptionsSubMenu`'s own [WindowPaneMenuItem.submenu] content — the row
 * itself always exists (gated only on `window.isVisible`, `SplitBibleArea.kt:902-905`), holding
 * either the dynamic `textOptionItem` children or a single nested `allTextOptions` child. `id`
 * addressing does not care about nesting depth, so `allTextOptions` is still reachable at the
 * same id either way.
 */
class WindowPaneMenuStateBuilder(
    private val windowControl: WindowControl,
    private val speakControl: SpeakControl,
) {
    private val windowRepository: WindowRepository get() = windowControl.windowRepository
    private val app get() = application

    /** The full ordered item set for `window_popup_menu`, mirroring classic `showPopupMenu`. */
    fun build(window: Window): List<WindowPaneMenuItem> {
        val isMaximised = windowRepository.isMaximized
        val items = mutableListOf<WindowPaneMenuItem>()

        // iconKey values below are classic's own `android:icon` for the same item id in
        // `res/menu/window_popup_menu.xml` (classic force-shows them, SplitBibleArea.kt:859).
        // Items that XML leaves iconless stay iconKey = null.

        // SplitBibleArea.kt:880-883
        if (!isMaximised && !window.isLinksWindow) {
            items += WindowPaneMenuItem(
                id = ID_WINDOW_NEW,
                label = app.getString(R.string.new_window),
                iconKey = "ic_window_add_outline_black_24dp",
            )
        }
        // SplitBibleArea.kt:974-977. `visible = !isMaximised` ONLY -- classic genuinely lets a
        // links window be maximised from its own per-window ☰ menu, no `!isLinksWindow` guard
        // here (unlike `windowNew`/`changeToNormal` just above/below). Confirmed classic parity;
        // do not add a links-window guard (see WindowPaneMenuStateBuilderTest).
        if (!isMaximised) {
            items += WindowPaneMenuItem(
                id = ID_WINDOW_MAXIMISE,
                label = app.getString(R.string.windowMaximise),
                iconKey = "ic_window_maximise_24dp",
            )
        }
        // SplitBibleArea.kt:970-973. Compose-specific choice: kept visible whenever not
        // maximised but DISABLED (rather than hidden) when not minimizable, so `enabled` directly
        // mirrors `WindowControl.isWindowMinimizable` (see WindowPaneMenuStateBuilderTest).
        if (!isMaximised) {
            items += WindowPaneMenuItem(
                id = ID_WINDOW_MINIMISE,
                label = app.getString(R.string.windowMinimise),
                enabled = windowControl.isWindowMinimizable(window),
                iconKey = "ic_baseline_minimise_24",
            )
        }
        // SplitBibleArea.kt:884-890 (compound action: add a new non-links window, close this one
        // — handled by the Task-5 dispatcher via the atomic addNewWindow+closeWindow seam calls)
        if (window.isLinksWindow) {
            items += WindowPaneMenuItem(
                id = ID_CHANGE_TO_NORMAL,
                label = app.getString(R.string.change_to_normal),
                iconKey = "ic_link_black_24dp",
            )
        }
        // SplitBibleArea.kt:896-898 (visibility), :748-749, :782-792 (submenu build)
        if (!window.isLinksWindow && !isMaximised && windowControl.hasMoveItems(window)) {
            items += WindowPaneMenuItem(
                id = ID_MOVE_WINDOW_SUBMENU,
                label = app.getString(R.string.move_window),
                submenu = buildMoveItems(window),
                iconKey = "ic_window_move_to_24dp",
            )
        }
        // SplitBibleArea.kt:891-895
        if (!window.isLinksWindow && !isMaximised && !windowRepository.workspaceSettings.autoPin) {
            items += WindowPaneMenuItem(
                id = ID_PIN_MODE,
                label = app.getString(R.string.window_pin_mode),
                checkable = true,
                checked = window.isPinMode,
                iconKey = "ic_pin",
            )
        }
        // SplitBibleArea.kt:899-901 (visibility), :751-752, :754-758, :997-999 (submenu build)
        if (window.isSyncable) {
            items += WindowPaneMenuItem(
                id = ID_SYNC_GROUP_SUBMENU,
                label = app.getString(R.string.windowSynchronise),
                submenu = buildSyncGroupItems(window),
                iconKey = "ic_window_sync_24dp",
            )
        }
        // SplitBibleArea.kt:1036-1048
        if (window.isVisible &&
            !window.pageManager.isBibleShown &&
            window.pageManager.currentPage.currentDocument?.isSpecial != true
        ) {
            items += WindowPaneMenuItem(
                id = ID_ADD_WHOLE_PAGE_BOOKMARK,
                label = app.getString(R.string.add_whole_page_bookmark),
                iconKey = "ic_baseline_bookmark_24",
            )
        }

        val firstDoc = window.bibleView?.firstDocument
        // SplitBibleArea.kt:1011-1019
        if (window.isVisible &&
            (firstDoc is StudyPadDocument || firstDoc is MultiFragmentDocument || firstDoc is MyNotesDocument)
        ) {
            items += WindowPaneMenuItem(
                id = ID_EXPORT_HTML,
                label = app.getString(R.string.export_fileformat, "HTML"),
                opensDialog = true,
                iconKey = "file_export",
            )
        }
        // SplitBibleArea.kt:1020-1035
        if (window.isVisible && firstDoc is StudyPadDocument) {
            items += WindowPaneMenuItem(
                id = ID_EXPORT_STUDYPAD,
                label = app.getString(R.string.export_something, app.getString(R.string.studypad)),
                opensDialog = true,
                iconKey = "file_export",
            )
            items += WindowPaneMenuItem(
                id = ID_EXPORT_STUDYPAD_CSV,
                label = app.getString(R.string.export_bookmarks_csv, "CSV"),
                opensDialog = true,
                iconKey = "file_export",
            )
        }

        // SplitBibleArea.kt:902-905, :808-820, :978-986 -- see the class kdoc for the collapse note.
        if (window.isVisible) {
            items += WindowPaneMenuItem(
                id = ID_TEXT_OPTIONS_SUBMENU,
                label = app.getString(R.string.text_options_window_menutitle),
                submenu = buildTextOptionItems(window),
                iconKey = "ic_text_options_24dp",
            )
        }

        // SplitBibleArea.kt:1002-1010 (WindowControl.copySettingsTo*), flattened -- see class kdoc.
        //
        // INTENTIONAL WIDENING (controller-adjudicated, not a bug): classic nests these three rows
        // two levels deep inside `textOptionsSubMenu` (`window_popup_menu.xml`), and when
        // `CommonUtils.lastDisplaySettingsSorted` is empty, classic's `showPopupMenu` REMOVES THE
        // WHOLE `textOptionsSubMenu` (SplitBibleArea.kt:816-820) -- which incidentally hides
        // copySettingsTo* too, on a fresh install with no display-setting history. This builder
        // gates copySettingsTo* independently (only on `window.isVisible` + "another visible
        // window exists" for the Window variant), so it stays visible even with no last display
        // settings. This is a deliberate divergence: classic's coupling is an XML-nesting artifact
        // of where the menu items happen to live, not a considered product decision -- copying
        // settings to another window/workspace/globally is useful regardless of whether the user
        // has ever touched a per-item display setting. Do not "fix" this to match classic's
        // coupling.
        //
        // Carry-note for the Task-5 host dispatcher: `copySettingsToWindow` must resolve the
        // ACTUAL target window (classic's copy-settings dialog lets the user pick one of the other
        // visible windows, SplitBibleArea.kt:1005-1007's `order` param) -- do not default silently
        // to "first other window".
        if (window.isVisible) {
            // DIVERGENCE: classic nests these three under a `copySettingsTo` PARENT that alone
            // carries `ic_content_copy_black_24dp` (the children are iconless) -- since this
            // builder promotes them to top-level rows (see "INTENTIONAL WIDENING" above), each
            // takes the parent's icon so the promoted rows still read as "copy settings".
            if (windowRepository.visibleWindows.any { it.id != window.id }) {
                items += WindowPaneMenuItem(
                    id = ID_COPY_SETTINGS_TO_WINDOW,
                    label = app.getString(R.string.copy_settings_to_other_window),
                    opensDialog = true,
                    iconKey = "ic_content_copy_black_24dp",
                )
            }
            items += WindowPaneMenuItem(
                id = ID_COPY_SETTINGS_TO_WORKSPACE,
                label = "${app.getString(R.string.copy_settings)} ${app.getString(R.string.copy_settings_to_workspace)}",
                opensDialog = true,
                iconKey = "ic_content_copy_black_24dp",
            )
            items += WindowPaneMenuItem(
                id = ID_COPY_SETTINGS_TO_GLOBAL,
                label = "${app.getString(R.string.copy_settings)} ${app.getString(R.string.copy_settings_to_global)}",
                opensDialog = true,
                iconKey = "ic_content_copy_black_24dp",
            )
        }

        // SplitBibleArea.kt:1049-1063
        if (CommonUtils.settings.llmConfigured && window.isVisible) {
            items += WindowPaneMenuItem(
                id = ID_LLM_ACTIONS_SUBMENU,
                label = app.getString(R.string.llm_actions),
                opensDialog = true,
                iconKey = "icon_robot",
            )
        }

        // SplitBibleArea.kt:950-969
        if (window.pageManager.currentPage.currentDocument?.isSpecial != true) {
            items += WindowPaneMenuItem(
                id = ID_COPY_REFERENCE,
                label = app.getString(R.string.copyReference),
                opensDialog = true,
                iconKey = "ic_content_copy_black_24dp",
            )
        }

        // SplitBibleArea.kt:929-949. Genuine reuse (not reproduction): reads the real shared
        // `net.bible.android.view.activity.page.screen.clipboardKey`, the same state classic reads.
        run {
            val clipboard = clipboardKey
            if (clipboard != null && clipboard.document?.isSpecial != true) {
                val label = app.getString(
                    R.string.go_to_ref,
                    if (clipboard.document?.bookCategory == BookCategory.BIBLE && window.pageManager.isVersePageShown) {
                        clipboard.key.shortName
                    } else {
                        clipboard.shortName
                    },
                )
                items += WindowPaneMenuItem(id = ID_GO_TO_REFERENCE, label = label, opensDialog = true, iconKey = "baseline_content_paste_24")
            }
        }

        // SplitBibleArea.kt:910-928
        if (!speakControl.isStopped) {
            val bookAndKey = speakControl.speakPageManager.currentPage.bookAndKey
            val label = app.getString(
                R.string.go_to_ref,
                bookAndKey?.let {
                    if (it.document?.bookCategory == BookCategory.BIBLE && window.pageManager.isVersePageShown) {
                        it.key.shortName
                    } else {
                        it.shortName
                    }
                },
            )
            items += WindowPaneMenuItem(id = ID_GO_TO_SPEAK, label = label, opensDialog = true, iconKey = "ic_baseline_headphones_24")
        }

        // SplitBibleArea.kt:906-909
        if (windowControl.isWindowRemovable(window) && !isMaximised) {
            items += WindowPaneMenuItem(id = ID_WINDOW_CLOSE, label = app.getString(R.string.close), iconKey = "ic_close_white_24dp")
        }

        return items
    }

    /** SplitBibleArea.kt:782-792: one row per OTHER window sharing [window]'s pin mode. */
    private fun buildMoveItems(window: Window): List<WindowPaneMenuItem> =
        windowRepository.windowList
            .filter { it.isPinMode == window.isPinMode }
            .mapIndexedNotNull { order, other ->
                if (other.id == window.id) return@mapIndexedNotNull null
                val page = other.pageManager.currentPage
                WindowPaneMenuItem(
                    id = idForMoveItem(order),
                    label = app.getString(
                        R.string.move_window_to_position2, order + 1, page.currentDocument?.abbreviation, page.key?.name,
                    ),
                )
            }

    /** SplitBibleArea.kt:754-758 (groups), :997-999 (disableSync). */
    private fun buildSyncGroupItems(window: Window): List<WindowPaneMenuItem> {
        val items = mutableListOf<WindowPaneMenuItem>()
        if (window.isSynchronised) {
            items += WindowPaneMenuItem(id = ID_DISABLE_SYNC, label = app.getString(R.string.disable_sync))
        }
        for (i in 0..5) {
            if (window.isSynchronised && i == window.syncGroup) continue
            items += WindowPaneMenuItem(id = idForSyncGroupItem(i), label = app.getString(R.string.sync_group_n, i + 1))
        }
        return items
    }

    /**
     * SplitBibleArea.kt:808-820 (collapse), :1000 (`getItemOptions(R.id.textOptionItem, order)`
     * delegating to `getPrefItem`). Genuinely REUSES `getPrefItem` (public, side-effect-free) for
     * each dynamic row's real title/checkable/checked/enabled/opensDialog, unlike the rest of this
     * builder which reproduces `getItemOptions`'s branches inline.
     */
    private fun buildTextOptionItems(window: Window): List<WindowPaneMenuItem> {
        val lastSettings = CommonUtils.lastDisplaySettingsSorted
        if (lastSettings.isEmpty()) {
            // SplitBibleArea.kt:816-820
            return listOf(
                WindowPaneMenuItem(
                    id = ID_ALL_TEXT_OPTIONS,
                    label = app.getString(R.string.all_text_options_window_menutitle),
                    opensDialog = true,
                    iconKey = "ic_text_options_24dp",
                ),
            )
        }
        // SplitBibleArea.kt:866-874
        val settingsBundle = SettingsBundle(
            level = SettingsLevel.WINDOW,
            windowId = window.id,
            pageManagerSettings = window.pageManager.textDisplaySettings,
            workspaceId = windowRepository.id,
            workspaceName = windowRepository.name,
            workspaceSettings = windowRepository.textDisplaySettings,
            globalSettings = CommonUtils.globalTextDisplaySettings,
        )
        // SplitBibleArea.kt:809-815 (only rows passing enabled&&visible are added)
        return lastSettings.mapIndexedNotNull { order, type ->
            val itm = getPrefItem(settingsBundle, type)
            if (itm.enabled && itm.visible) {
                WindowPaneMenuItem(
                    id = idForTextOptionItem(order),
                    label = itm.title ?: type.name,
                    checkable = itm.isBoolean,
                    checked = itm.value == true,
                    enabled = itm.enabled,
                    opensDialog = itm.opensDialog,
                )
            } else null
        }
    }

    companion object {
        const val ID_WINDOW_NEW = "windowNew"
        const val ID_WINDOW_MAXIMISE = "windowMaximise"
        const val ID_WINDOW_MINIMISE = "windowMinimise"
        const val ID_CHANGE_TO_NORMAL = "changeToNormal"
        const val ID_PIN_MODE = "pinMode"
        const val ID_DISABLE_SYNC = "disableSync"
        const val ID_ALL_TEXT_OPTIONS = "allTextOptions"
        const val ID_WINDOW_CLOSE = "windowClose"
        const val ID_COPY_REFERENCE = "copyReference"
        const val ID_GO_TO_REFERENCE = "goToReference"
        const val ID_GO_TO_SPEAK = "goToSpeak"
        const val ID_ADD_WHOLE_PAGE_BOOKMARK = "addWholePageBookmark"
        const val ID_EXPORT_HTML = "exportHtml"
        const val ID_EXPORT_STUDYPAD = "exportStudypad"
        const val ID_EXPORT_STUDYPAD_CSV = "exportStudypadCsv"
        const val ID_COPY_SETTINGS_TO_WINDOW = "copySettingsToWindow"
        const val ID_COPY_SETTINGS_TO_WORKSPACE = "copySettingsToWorkspace"
        const val ID_COPY_SETTINGS_TO_GLOBAL = "copySettingsToGlobal"
        const val ID_LLM_ACTIONS_SUBMENU = "llmActionsSubMenu"
        const val ID_MOVE_WINDOW_SUBMENU = "moveWindowSubMenu"
        const val ID_SYNC_GROUP_SUBMENU = "syncGroupSubMenu"
        const val ID_TEXT_OPTIONS_SUBMENU = "textOptionsSubMenu"

        private const val MOVE_ITEM_PREFIX = "moveItem:"
        private const val SYNC_GROUP_ITEM_PREFIX = "syncGroupItem:"
        private const val TEXT_OPTION_ITEM_PREFIX = "textOptionItem:"

        private val staticIds: Set<String> = setOf(
            ID_WINDOW_NEW, ID_WINDOW_MAXIMISE, ID_WINDOW_MINIMISE, ID_CHANGE_TO_NORMAL, ID_PIN_MODE,
            ID_DISABLE_SYNC, ID_ALL_TEXT_OPTIONS, ID_WINDOW_CLOSE, ID_COPY_REFERENCE, ID_GO_TO_REFERENCE,
            ID_GO_TO_SPEAK, ID_ADD_WHOLE_PAGE_BOOKMARK, ID_EXPORT_HTML, ID_EXPORT_STUDYPAD, ID_EXPORT_STUDYPAD_CSV,
            ID_COPY_SETTINGS_TO_WINDOW, ID_COPY_SETTINGS_TO_WORKSPACE, ID_COPY_SETTINGS_TO_GLOBAL,
            ID_LLM_ACTIONS_SUBMENU, ID_MOVE_WINDOW_SUBMENU, ID_SYNC_GROUP_SUBMENU, ID_TEXT_OPTIONS_SUBMENU,
        )

        fun idForMoveItem(order: Int): String = "$MOVE_ITEM_PREFIX$order"
        fun idForSyncGroupItem(order: Int): String = "$SYNC_GROUP_ITEM_PREFIX$order"
        fun idForTextOptionItem(order: Int): String = "$TEXT_OPTION_ITEM_PREFIX$order"

        /**
         * Reverses [idForMoveItem]/[idForSyncGroupItem]/[idForTextOptionItem] and the static id
         * constants above into a [ParsedId] the host dispatcher (Task 5) can `when`-switch on.
         * Throws on an unknown id (e.g. a stale click after the item list changed underneath it).
         */
        fun parseId(id: String): ParsedId = when {
            id.startsWith(MOVE_ITEM_PREFIX) -> ParsedId.MoveItem(id.removePrefix(MOVE_ITEM_PREFIX).toInt())
            id.startsWith(SYNC_GROUP_ITEM_PREFIX) -> ParsedId.SyncGroupItem(id.removePrefix(SYNC_GROUP_ITEM_PREFIX).toInt())
            id.startsWith(TEXT_OPTION_ITEM_PREFIX) -> ParsedId.TextOptionItem(id.removePrefix(TEXT_OPTION_ITEM_PREFIX).toInt())
            id in staticIds -> ParsedId.StaticItem(id)
            else -> throw IllegalArgumentException("Unknown window-pane-menu item id: $id")
        }
    }

    /**
     * A parsed [WindowPaneMenuItem.id] — see [parseId]. A plain (non-`inner`) nested type of the
     * class itself, NOT of [Companion] — Kotlin does not promote a companion object's own nested
     * classifiers onto the enclosing class's namespace, so `WindowPaneMenuStateBuilder.ParsedId`
     * only resolves from here.
     */
    sealed interface ParsedId {
        data class StaticItem(val id: String) : ParsedId
        data class MoveItem(val order: Int) : ParsedId
        data class SyncGroupItem(val order: Int) : ParsedId
        data class TextOptionItem(val order: Int) : ParsedId
    }
}

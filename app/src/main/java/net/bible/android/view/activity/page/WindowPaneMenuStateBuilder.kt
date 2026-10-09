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
import org.crosswire.jsword.versification.BookName

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
 * The one deliberate further exception is `copySettingsToWindow`/`Workspace`/`Global` (see
 * `buildCopySettingsItems`, A/B batch 4a whole-batch review M1): all three DO bridge into a native
 * dialog (`WindowControl.chooseSettingsToCopy`'s `AlertDialog`), but are `opensDialog = false` like
 * classic itself, which never sets that flag for any of the three (`SplitBibleArea.kt:1002-1010`).
 *

 * **`copySettingsToWindow`/`Workspace`/`Global` follow classic's nesting.** They sit under
 * `textOptionsSubMenu` > `copySettingsTo` — one dynamic `copySettingsToWindow` row PER other
 * visible window, plus the `Workspace`/`Global` rows (`SplitBibleArea.kt:778-803`,
 * `:1002-1010`). Batch 12b follow-on had flattened these into three top-level rows with a picker
 * dialog resolving the target window; the maintainer overruled that in A/B feedback batch 4a (F4),
 * so the classic shape is back — do not re-flatten it, that has already been tried and rejected on
 * device. The one surviving divergence: the `textOptionsSubMenu` (and so `copySettingsTo` with it)
 * stays present even with an empty display-setting history, where classic removes the whole
 * submenu (see `buildCopySettingsItems`'s kdoc for why that's kept).
 *
 * **`textOptionsSubMenu` always ends with `allTextOptions`** — classic's
 * `window_popup_menu.xml` declares it as a static child at orderInCategory=1000, present
 * alongside whatever dynamic last-used rows exist. This builder mirrors that: the submenu
 * [WindowPaneMenuItem.submenu] content is `dynamic + [allTextOptions row]`, so `allTextOptions`
 * is always last. The empty case (no last-used display settings) produces no dynamic rows but
 * still includes the `allTextOptions` row — matching classic's promotion of that row to a top-level
 * item only in that edge case (classic's `showPopupMenu` `SplitBibleArea.kt:808-820` removes the
 * whole submenu when empty, but this builder just omits the dynamic children). `id` addressing does
 * not care about nesting depth, so `allTextOptions` is always reachable at the same id.
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
                        R.string.move_window_to_position2, order + 1, page.currentDocument?.abbreviation, page.key?.getName(),
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
                WindowPaneMenuItem(
                    id = ID_COPY_SETTINGS_TO_SUBMENU,
                    label = app.getString(R.string.copy_settings),
                    submenu = buildCopySettingsItems(window),
                    iconKey = "ic_content_copy_black_24dp",
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
        val dynamic = lastSettings.mapIndexedNotNull { order, type ->
            val itm = getPrefItem(settingsBundle, type)
            if (itm.enabled && itm.visible) {
                WindowPaneMenuItem(
                    id = idForTextOptionItem(order),
                    label = itm.title ?: type.name,
                    checkable = itm.isBoolean,
                    checked = itm.value == true,
                    enabled = itm.enabled,
                    opensDialog = itm.opensDialog,
                    // A/B batch 3 F4: as above — `itm` is already the classic ItemPreference.
                    iconKey = itm.icon?.let { app.resources.getResourceEntryName(it) },
                )
            } else null
        }
        // A/B batch 4a F3: classic's window_popup_menu.xml declares allTextOptions as a static child
        // of this submenu at orderInCategory=1000 — i.e. ALWAYS present, sorted last, next to
        // whatever dynamic rows exist. The port previously produced it only in the empty branch
        // above, so a user with any display-setting history lost the row entirely.
        return dynamic + WindowPaneMenuItem(
            id = ID_ALL_TEXT_OPTIONS,
            label = app.getString(R.string.all_text_options_window_menutitle),
            opensDialog = true,
            iconKey = "ic_text_options_24dp",
        ) + WindowPaneMenuItem(
            id = ID_COPY_SETTINGS_TO_SUBMENU,
            label = app.getString(R.string.copy_settings),
            submenu = buildCopySettingsItems(window),
            iconKey = "ic_content_copy_black_24dp",
        )
    }

    /**
     * SplitBibleArea.kt:778-803 (per-window rows), :1002-1010 (the three actions).
     *
     * Classic nests these under `textOptionsSubMenu` > `copySettingsTo`. Batch 12b follow-on had
     * flattened them into three top-level rows with a picker dialog resolving the target window;
     * the maintainer overruled that in A/B feedback batch 4a (F4), so the classic shape is back.
     * Keep it: the flattening has already been tried and rejected on device.
     *
     * RETAINED DIVERGENCE: classic removes the entire `textOptionsSubMenu` when there is no
     * display-setting history (SplitBibleArea.kt:816-820), which incidentally hides copy-settings
     * too. The submenu stays here, so copy-settings is reachable on a fresh install. That was the
     * useful half of the reverted widening and is deliberately kept.
     */
    private fun buildCopySettingsItems(window: Window): List<WindowPaneMenuItem> {
        val items = mutableListOf<WindowPaneMenuItem>()
        // SplitBibleArea.kt:793-802: `order` counts over visibleWindows INCLUDING this window, and
        // is what WindowControl.copySettingsToWindow(window, order) expects -- so index with
        // forEachIndexed over the whole list and skip self, never over a pre-filtered list.
        synchronized(BookName::class.java) {
            val oldValue = BookName.isFullBookName()
            BookName.setFullBookName(false)
            try {
                windowRepository.visibleWindows.forEachIndexed { order, other ->
                    if (other.id == window.id) return@forEachIndexed
                    val page = other.pageManager.currentPage
                    items += WindowPaneMenuItem(
                        id = idForCopySettingsToWindow(order),
                        label = app.getString(
                            R.string.copy_settings_to_window, order + 1, page.currentDocument?.abbreviation, page.key?.getName(),
                        ),
                    )
                }
            } finally {
                BookName.setFullBookName(oldValue)
            }
        }
        // A/B batch 4a whole-batch review M1: opensDialog left at its false default here, matching
        // the dynamic copySettingsToWindow rows above -- all three call windowControl.copySettingsTo*,
        // which share the SAME chooseSettingsToCopy(window) AlertDialog (WindowControl.kt:297-364),
        // so there was no reason for these two alone to render the " …" suffix while the per-window
        // rows didn't. Verified against classic itself, not just internally: SplitBibleArea.kt's
        // getItemOptions constructs all three as a bare `CommandPreference({...})` (:1002,:1005,:1008)
        // with no `opensDialog = true` -- CommandPreference's own default (OptionsMenuItems.kt:312) --
        // so classic genuinely marks none of the three, unlike e.g. R.id.allTextOptions
        // (SplitBibleArea.kt:985), which explicitly sets `opensDialog = true` and IS mirrored that way
        // by this builder's ID_ALL_TEXT_OPTIONS row.
        items += WindowPaneMenuItem(
            id = ID_COPY_SETTINGS_TO_WORKSPACE,
            label = app.getString(R.string.copy_settings_to_workspace),
        )
        items += WindowPaneMenuItem(
            id = ID_COPY_SETTINGS_TO_GLOBAL,
            label = app.getString(R.string.copy_settings_to_global),
        )
        return items
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
        const val ID_COPY_SETTINGS_TO_WORKSPACE = "copySettingsToWorkspace"
        const val ID_COPY_SETTINGS_TO_GLOBAL = "copySettingsToGlobal"
        const val ID_COPY_SETTINGS_TO_SUBMENU = "copySettingsTo"
        const val ID_LLM_ACTIONS_SUBMENU = "llmActionsSubMenu"
        const val ID_MOVE_WINDOW_SUBMENU = "moveWindowSubMenu"
        const val ID_SYNC_GROUP_SUBMENU = "syncGroupSubMenu"
        const val ID_TEXT_OPTIONS_SUBMENU = "textOptionsSubMenu"

        private const val MOVE_ITEM_PREFIX = "moveItem:"
        private const val SYNC_GROUP_ITEM_PREFIX = "syncGroupItem:"
        private const val TEXT_OPTION_ITEM_PREFIX = "textOptionItem:"
        private const val COPY_SETTINGS_TO_WINDOW_PREFIX = "copySettingsToWindow:"

        private val staticIds: Set<String> = setOf(
            ID_WINDOW_NEW, ID_WINDOW_MAXIMISE, ID_WINDOW_MINIMISE, ID_CHANGE_TO_NORMAL, ID_PIN_MODE,
            ID_DISABLE_SYNC, ID_ALL_TEXT_OPTIONS, ID_WINDOW_CLOSE, ID_COPY_REFERENCE, ID_GO_TO_REFERENCE,
            ID_GO_TO_SPEAK, ID_ADD_WHOLE_PAGE_BOOKMARK, ID_EXPORT_HTML, ID_EXPORT_STUDYPAD, ID_EXPORT_STUDYPAD_CSV,
            ID_COPY_SETTINGS_TO_WORKSPACE, ID_COPY_SETTINGS_TO_GLOBAL, ID_COPY_SETTINGS_TO_SUBMENU,
            ID_LLM_ACTIONS_SUBMENU, ID_MOVE_WINDOW_SUBMENU, ID_SYNC_GROUP_SUBMENU, ID_TEXT_OPTIONS_SUBMENU,
        )

        fun idForMoveItem(order: Int): String = "$MOVE_ITEM_PREFIX$order"
        fun idForSyncGroupItem(order: Int): String = "$SYNC_GROUP_ITEM_PREFIX$order"
        fun idForTextOptionItem(order: Int): String = "$TEXT_OPTION_ITEM_PREFIX$order"
        fun idForCopySettingsToWindow(order: Int): String = "$COPY_SETTINGS_TO_WINDOW_PREFIX$order"

        /**
         * Reverses [idForMoveItem]/[idForSyncGroupItem]/[idForTextOptionItem]/
         * [idForCopySettingsToWindow] and the static id constants above into a [ParsedId] the host
         * dispatcher (Task 5) can `when`-switch on. Throws on an unknown id (e.g. a stale click
         * after the item list changed underneath it).
         */
        fun parseId(id: String): ParsedId = when {
            id.startsWith(MOVE_ITEM_PREFIX) -> ParsedId.MoveItem(id.removePrefix(MOVE_ITEM_PREFIX).toInt())
            id.startsWith(SYNC_GROUP_ITEM_PREFIX) -> ParsedId.SyncGroupItem(id.removePrefix(SYNC_GROUP_ITEM_PREFIX).toInt())
            id.startsWith(TEXT_OPTION_ITEM_PREFIX) -> ParsedId.TextOptionItem(id.removePrefix(TEXT_OPTION_ITEM_PREFIX).toInt())
            id.startsWith(COPY_SETTINGS_TO_WINDOW_PREFIX) ->
                ParsedId.CopySettingsToWindow(id.removePrefix(COPY_SETTINGS_TO_WINDOW_PREFIX).toInt())
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
        data class CopySettingsToWindow(val order: Int) : ParsedId
    }
}

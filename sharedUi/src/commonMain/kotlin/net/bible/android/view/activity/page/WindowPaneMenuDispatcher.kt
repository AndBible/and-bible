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

@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.android.view.activity.page

import net.bible.android.control.page.window.Window
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.page.window.WindowRepository
import net.bible.android.control.speak.SpeakControl
import net.bible.android.database.IdType
import net.bible.android.database.SettingsBundle
import net.bible.android.database.SettingsLevel
import net.bible.android.database.WorkspaceEntities.TextDisplaySettings
import net.bible.android.view.activity.page.screen.clipboardKey
import net.bible.service.common.andBibleUrl
import net.bible.service.sword.BookAndKey
import net.bible.sharedcore.window.ReadingViewController
import net.bible.sharedlogic.settings.PrefItemHandle
import net.bible.sharedlogic.settings.TextDisplayPrefItems
import org.crosswire.jsword.book.BookCategory

/**
 * What a pane menu row does that only the platform can: the rows that open a platform editor or leave the app's
 * own window model. Every member defaults to "not available", which is also what the platform's builder omits
 * (see [WindowPaneMenuStateBuilder]'s `omit`), so a platform lists exactly the rows it implements.
 */
@kotlin.native.HiddenFromObjC
interface WindowPaneMenuPlatform {
    /** The window-level "All text options" destination. */
    fun openAllTextOptions(window: Window) {}

    /**
     * A non-toggle "last used" text option row ([item] opens an editor). [bundle] is the window-level bundle the item
     * was read from. Returns whether the menu stays open.
     */
    fun openTextOption(window: Window, type: TextDisplaySettings.Types, item: PrefItemHandle, bundle: SettingsBundle): Boolean = false

    fun exportHtml(window: Window) {}
    fun exportStudyPad(window: Window) {}
    fun exportStudyPadCsv(window: Window) {}
    fun llmActions(window: Window) {}

    /** Bookmarks the whole current page of [window] (the page's document and OSIS key). */
    fun addWholePageBookmark(window: Window, bookInitials: String, osisRef: String) {}

    /** Puts [url] on the clipboard ([label] names it) and tells the user. */
    fun copyReference(label: String, url: String) {}
}

/**
 * Runs a chosen window pane menu row: the common half of Android's `ReadingCommands.handleWindowPaneMenuItem`, shared with
 * iOS. Window operations go through [controller] (the reading view's command seam) when there is one, else straight
 * through [windowControl]; the rows only a platform can do go to [platform].
 *
 * [handle] returns whether the menu should stay open: true for the pin toggle and a boolean text option row (the host
 * then rebuilds the list and shows the flipped check), false for every other row.
 */
@kotlin.native.HiddenFromObjC
class WindowPaneMenuDispatcher(
    private val windowControl: WindowControl,
    private val speakControl: SpeakControl,
    private val windowRepository: () -> WindowRepository,
    private val controller: () -> ReadingViewController?,
    private val prefItems: TextDisplayPrefItems,
    private val recentDisplaySettings: () -> List<TextDisplaySettings.Types>,
    private val globalTextDisplaySettings: () -> TextDisplaySettings,
    private val platform: WindowPaneMenuPlatform,
) {
    companion object {
        /**
         * The static row ids this dispatcher hands to its [WindowPaneMenuPlatform] (the rest it runs itself). A platform
         * that cannot run one omits it from the builder; the tests tie this set to the behaviour and to the iOS lists.
         */
        val PLATFORM_ROWS: Set<String> = setOf(
            WindowPaneMenuStateBuilder.ID_ALL_TEXT_OPTIONS,
            WindowPaneMenuStateBuilder.ID_EXPORT_HTML,
            WindowPaneMenuStateBuilder.ID_EXPORT_STUDYPAD,
            WindowPaneMenuStateBuilder.ID_EXPORT_STUDYPAD_CSV,
            WindowPaneMenuStateBuilder.ID_LLM_ACTIONS_SUBMENU,
            WindowPaneMenuStateBuilder.ID_ADD_WHOLE_PAGE_BOOKMARK,
            WindowPaneMenuStateBuilder.ID_COPY_REFERENCE,
        )
    }

    fun handle(windowId: String, id: String): Boolean {
        val window = windowRepository().getWindow(IdType(windowId)) ?: return false
        val controller = controller()
        return when (val parsed = WindowPaneMenuStateBuilder.parseId(id)) {
            // SplitBibleArea.kt:896-898, :987-992
            is WindowPaneMenuStateBuilder.ParsedId.MoveItem -> {
                controller?.onMove(windowId, parsed.order) ?: windowControl.moveWindow(window, parsed.order)
                false
            }
            // SplitBibleArea.kt:899-901, :993-996
            is WindowPaneMenuStateBuilder.ParsedId.SyncGroupItem -> {
                controller?.onChangeSyncGroup(windowId, parsed.order) ?: windowControl.changeSyncGroup(window, parsed.order)
                false
            }
            is WindowPaneMenuStateBuilder.ParsedId.TextOptionItem -> handleTextOption(window, parsed.order)
            // SplitBibleArea.kt:1005-1007: the target window comes from the row's own order.
            is WindowPaneMenuStateBuilder.ParsedId.CopySettingsToWindow -> {
                windowControl.copySettingsToWindow(window, parsed.order)
                false
            }
            is WindowPaneMenuStateBuilder.ParsedId.StaticItem -> handleStatic(window, parsed.id, controller)
        }
    }

    private fun handleStatic(window: Window, id: String, controller: ReadingViewController?): Boolean {
        val windowId = window.id.toString()
        return when (id) {
            WindowPaneMenuStateBuilder.ID_WINDOW_NEW -> {
                controller?.onAddWindow(windowId) ?: windowControl.addNewWindow(window)
                false
            }
            WindowPaneMenuStateBuilder.ID_WINDOW_MAXIMISE -> {
                controller?.onMaximise(windowId) ?: windowControl.maximiseWindow(window)
                false
            }
            WindowPaneMenuStateBuilder.ID_WINDOW_MINIMISE -> {
                controller?.onMinimise(windowId) ?: windowControl.minimiseWindow(window)
                false
            }
            // SplitBibleArea.kt:884-890: compound (add a new non-links window, close this one); the seam has no such command.
            WindowPaneMenuStateBuilder.ID_CHANGE_TO_NORMAL -> {
                windowControl.addNewWindow(window).also { it.isLinksWindow = false }
                windowControl.closeWindow(window)
                false
            }
            // A checkable toggle: stays open so the host rebuilds the list and shows the flipped check.
            WindowPaneMenuStateBuilder.ID_PIN_MODE -> {
                val newValue = !window.isPinMode
                controller?.onSetPin(windowId, newValue) ?: windowControl.setPinMode(window, newValue)
                true
            }
            WindowPaneMenuStateBuilder.ID_DISABLE_SYNC -> {
                controller?.onSetSynchronised(windowId, false) ?: windowControl.setSynchronised(window, false)
                false
            }
            WindowPaneMenuStateBuilder.ID_WINDOW_CLOSE -> {
                controller?.onClose(windowId) ?: windowControl.closeWindow(window)
                false
            }
            WindowPaneMenuStateBuilder.ID_ALL_TEXT_OPTIONS -> { platform.openAllTextOptions(window); false }
            WindowPaneMenuStateBuilder.ID_COPY_SETTINGS_TO_WORKSPACE -> { windowControl.copySettingsToWorkspace(window); false }
            WindowPaneMenuStateBuilder.ID_COPY_SETTINGS_TO_GLOBAL -> { windowControl.copySettingsToGlobal(window); false }
            WindowPaneMenuStateBuilder.ID_EXPORT_HTML -> { platform.exportHtml(window); false }
            WindowPaneMenuStateBuilder.ID_EXPORT_STUDYPAD -> { platform.exportStudyPad(window); false }
            WindowPaneMenuStateBuilder.ID_EXPORT_STUDYPAD_CSV -> { platform.exportStudyPadCsv(window); false }
            WindowPaneMenuStateBuilder.ID_LLM_ACTIONS_SUBMENU -> { platform.llmActions(window); false }
            // SplitBibleArea.kt:1036-1048
            WindowPaneMenuStateBuilder.ID_ADD_WHOLE_PAGE_BOOKMARK -> {
                val currentPage = window.pageManager.currentPage
                val book = currentPage.currentDocument
                val key = currentPage.key
                if (book != null && key != null) platform.addWholePageBookmark(window, book.initials, key.getOsisRef())
                false
            }
            // SplitBibleArea.kt:950-967
            WindowPaneMenuStateBuilder.ID_COPY_REFERENCE -> {
                val doc = window.pageManager.currentPage.currentDocument
                val key = window.pageManager.currentPage.singleKey
                if (doc != null && key != null) {
                    val ordinalRange = window.pageManager.currentPage.anchorOrdinal
                    clipboardKey = BookAndKey(key, doc, ordinalRange)
                    platform.copyReference(
                        key.getName(),
                        andBibleUrl(keyStr = key.getOsisRef(), docInitials = doc.initials, ordinal = ordinalRange?.start),
                    )
                }
                false
            }
            // SplitBibleArea.kt:929-949
            WindowPaneMenuStateBuilder.ID_GO_TO_REFERENCE -> {
                clipboardKey?.let {
                    if (it.document?.bookCategory == BookCategory.BIBLE && window.pageManager.isVersePageShown) {
                        window.pageManager.setCurrentDocumentAndKey(null, it.key)
                    } else if (it.document == null) {
                        window.pageManager.setCurrentDocumentAndKey(null, it.key)
                    } else {
                        window.pageManager.setCurrentDocumentAndKey(it.document, it)
                    }
                }
                false
            }
            // SplitBibleArea.kt:910-928
            WindowPaneMenuStateBuilder.ID_GO_TO_SPEAK -> {
                speakControl.speakBookAndKey?.let {
                    if (it.document?.bookCategory == BookCategory.BIBLE && window.pageManager.isVersePageShown) {
                        window.pageManager.setCurrentDocumentAndKey(null, it.key)
                    } else {
                        window.pageManager.setCurrentDocumentAndKey(it.document, it)
                    }
                }
                false
            }
            // The submenu container ids never reach a leaf click.
            else -> false
        }
    }

    /** SplitBibleArea.kt:1000: a "last used" text option row, at WINDOW level. */
    private fun handleTextOption(window: Window, order: Int): Boolean {
        val repository = windowRepository()
        val bundle = SettingsBundle(
            level = SettingsLevel.WINDOW,
            windowId = window.id,
            pageManagerSettings = window.pageManager.textDisplaySettings,
            workspaceId = repository.id,
            workspaceName = repository.name,
            workspaceSettings = repository.textDisplaySettings,
            globalSettings = globalTextDisplaySettings(),
        )
        val type = recentDisplaySettings().getOrNull(order) ?: return false
        val item = prefItems.item(bundle, type)
        return if (item.isBoolean) {
            item.value = !(item.value == true)
            item.handle()
            true
        } else {
            platform.openTextOption(window, type, item, bundle)
        }
    }
}

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
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class, kotlinx.cinterop.ExperimentalForeignApi::class)

package net.bible.sharedui.ios.reading

import net.bible.android.control.link.LinkControl
import net.bible.android.view.activity.page.Selection
import net.bible.android.view.activity.page.SelectionMenuItem
import net.bible.service.sword.SwordContentFacade
import net.bible.service.sword.SwordDocumentFacade
import net.bible.sharedcore.bibleview.BibleViewEvents
import net.bible.sharedcore.log.nsLog
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedui.app.ports.ExternalLinks
import net.bible.sharedui.reading.bridge.ReadingBridge
import net.bible.sharedui.reading.bridge.webSearchUrl
import net.bible.sharedui.reading.session.BibleWindowSession
import org.crosswire.jsword.versification.BookName
import platform.UIKit.UIAction
import platform.UIKit.UIMenu
import platform.UIKit.UIMenuBuilderProtocol
import platform.UIKit.UIMenuOptionsDisplayInline
import platform.UIKit.UIMenuRoot
import platform.UIKit.UIMenuStandardEdit
import platform.UIKit.UIMenuSystem
import platform.UIKit.UIPasteboard
import platform.WebKit.WKScriptMessage
import platform.WebKit.WKScriptMessageHandlerProtocol
import platform.WebKit.WKUserContentController
import platform.darwin.NSObject

/** [SelectionMenuWindow] over a window's session and bridge: the same calls Android's `BibleView` makes. */
internal class IosSelectionMenuWindow(
    private val session: BibleWindowSession,
    private val bridge: ReadingBridge,
    private val facade: () -> SwordContentFacade,
) : SelectionMenuWindow {
    override val isBible: Boolean get() = session.firstDocument is net.bible.android.control.page.BibleDocument
    override fun makeBookmark(selection: Selection?, wholeVerse: Boolean) = bridge.bookmarkCreation.makeBookmark(selection, wholeVerse, false)
    override fun addParagraphBreak(selection: Selection?) = bridge.bookmarkCreation.addParagraphBreakBookmark(selection)
    override fun compare(selection: Selection?) = bridge.compareSelection(selection)
    override fun memorize(selection: Selection?) = bridge.memorizeSelection(selection)
    override fun share(selection: Selection) {
        session.hostSheets?.showShare(facade().buildShareVersesInput(selection))
    }
    override fun clearSelection() {
        session.emit(BibleViewEvents.removeRanges())
    }
}

/**
 * The production [SelectionMenuEnvironment]: settings from [settings], links from [linkControl], search through
 * [openSearch] (the reading host), web search through [links] and the pasteboard. [llmConfiguredSource] answers
 * whether the AI features are configured (false hides the AI row).
 */
internal class IosSelectionMenuEnvironment(
    private val settings: () -> AppSettings,
    private val linkControl: () -> LinkControl,
    private val links: () -> ExternalLinks,
    private val openSearch: (String) -> Unit,
    private val llmConfiguredSource: () -> Boolean = { false },
) : SelectionMenuEnvironment {
    override val twoStepDisabled: Boolean get() = settings().getBoolean("disable_two_step_bookmarking", false)
    override val paragraphBreakEnabled: Boolean get() = "add_paragraph_break" in settings().getStringSet("experimental_features", emptySet())
    override val hasLookupDictionaries: Boolean get() = SwordDocumentFacade.wordLookupDictionaries(settings()).isNotEmpty()
    override val llmConfigured: Boolean get() = llmConfiguredSource()

    override fun resolveRef(text: String): String? {
        val key = linkControl().resolveRef(text) ?: return null
        // The short book name, as Android's menu title uses (the setting is process-wide; Android takes a lock, this runs on the main thread only).
        val wasFull = BookName.isFullBookName()
        BookName.setFullBookName(false)
        return try { key.getName() } finally { BookName.setFullBookName(wasFull) }
    }

    override fun openRef(text: String) {
        val control = linkControl()
        control.resolveRef(text)?.let { control.showLink(null, it) }
    }

    override fun lookupInDictionaries(text: String) {
        linkControl().lookupInDictionaries(text)
    }

    override fun search(text: String) = openSearch(text)
    override fun webSearch(text: String) = links().open(webSearchUrl(text))
    override fun copy(text: String) {
        UIPasteboard.generalPasteboard.string = text
    }
}

/**
 * Receives the page's debounced selection ([SELECTION_USER_SCRIPT]) and caches it in [menu]. When the cached selection
 * changed, UIKit is asked to rebuild the edit menu, so a menu that was built a moment before the page reported (a
 * long-press shows it at once) is rebuilt from the fresh cache.
 */
internal class IosSelectionMessageHandler(
    private val windowId: String,
    private val menu: IosSelectionMenu,
    private val rebuild: () -> Unit = { UIMenuSystem.contextSystem.setNeedsRebuild() },
) : NSObject(), WKScriptMessageHandlerProtocol {
    override fun userContentController(userContentController: WKUserContentController, didReceiveScriptMessage: WKScriptMessage) {
        try {
            val body = didReceiveScriptMessage.body as? String ?: return
            if (menu.cacheSelection(windowId, body)) rebuild()
        } catch (t: Throwable) {
            nsLog("selection[$windowId]: handler failure $t")
        }
    }
}

/**
 * Adds the AndBible rows to the system edit menu being built for [windowId]'s WebView (the web view's
 * `buildMenuWithBuilder`). Nothing is added without a cached selection. System Copy / Look Up / Share stay, so the policy's
 * non-Bible "Copy" row is left out (the system one copies the same text). On a Bible with two-step bookmarking the
 * "Bookmark" row is a submenu holding the step-two choices; UIKit shows it natively, no menu has to be re-presented.
 */
internal fun insertSelectionMenu(builder: UIMenuBuilderProtocol, windowId: String, menu: IosSelectionMenu) {
    val snapshot = menu.snapshot(windowId) ?: return
    val rows = menu.rows(windowId, snapshot).filter { it.item != SelectionMenuItem.Copy }
    if (rows.isEmpty()) return
    val children = rows.map { row ->
        if (menu.opensStepTwo(windowId, row.item)) {
            val choices = menu.stepTwoRows(windowId, snapshot).map { choice -> action(choice, menu, windowId, snapshot) }
            UIMenu.menuWithTitle(row.title, null, null, 0uL, choices)
        } else {
            action(row, menu, windowId, snapshot)
        }
    }
    val group = UIMenu.menuWithTitle("", null, null, UIMenuOptionsDisplayInline, children)
    if (builder.menuForIdentifier(UIMenuStandardEdit) != null) {
        builder.insertSiblingMenu(group, beforeMenuForIdentifier = UIMenuStandardEdit)
    } else {
        builder.insertChildMenu(group, atStartOfMenuForIdentifier = UIMenuRoot)
    }
}

private fun action(row: SelectionMenuRow, menu: IosSelectionMenu, windowId: String, snapshot: CachedSelection): UIAction =
    UIAction.actionWithTitle(row.title, null, null) { _ -> menu.run(windowId, row.item, snapshot) }

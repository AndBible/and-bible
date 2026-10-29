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

package net.bible.sharedui.ios.reading

import net.bible.android.control.link.LinkControl
import net.bible.android.view.activity.page.Selection
import net.bible.android.view.activity.page.SelectionMenuItem
import net.bible.service.sword.SwordContentFacade
import net.bible.service.sword.SwordDocumentFacade
import net.bible.sharedcore.bibleview.BibleViewEvents
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedui.app.ports.ExternalLinks
import net.bible.sharedui.reading.bridge.ReadingBridge
import net.bible.sharedui.reading.bridge.webSearchUrl
import net.bible.sharedui.reading.session.BibleWindowSession
import net.bible.sharedlogic.sync.bookNameMonitor
import net.bible.sharedlogic.sync.withMonitor
import org.crosswire.jsword.versification.BookName

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
 * [openSearch] (the reading host), web search through [links] and the clipboard through [copyText]. [llmConfiguredSource] answers
 * whether the AI features are configured (false hides the AI row).
 */
internal class IosSelectionMenuEnvironment(
    private val settings: () -> AppSettings,
    private val linkControl: () -> LinkControl,
    private val links: () -> ExternalLinks,
    private val openSearch: (String) -> Unit,
    /** Puts text on the system clipboard (the iOS host passes the general pasteboard). */
    private val copyText: (String) -> Unit,
    private val llmConfiguredSource: () -> Boolean = { false },
) : SelectionMenuEnvironment {
    override val twoStepDisabled: Boolean get() = settings().getBoolean("disable_two_step_bookmarking", false)
    override val paragraphBreakEnabled: Boolean get() = "add_paragraph_break" in settings().getStringSet("experimental_features", emptySet())
    override val hasLookupDictionaries: Boolean get() = SwordDocumentFacade.wordLookupDictionaries(settings()).isNotEmpty()
    override val llmConfigured: Boolean get() = llmConfiguredSource()

    override fun resolveRef(text: String): String? {
        val key = linkControl().resolveRef(text) ?: return null
        // The short book name, as Android's menu title uses. The setting is process-wide: the same monitor as `withShortBookNames`.
        return withMonitor(bookNameMonitor) {
            val wasFull = BookName.isFullBookName()
            BookName.setFullBookName(false)
            try { key.getName() } finally { BookName.setFullBookName(wasFull) }
        }
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
        copyText(text)
    }
}

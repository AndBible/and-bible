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

package net.bible.sharedui.host.reading

import net.bible.android.view.activity.page.Selection
import net.bible.android.view.activity.page.SelectionMenuInput
import net.bible.android.view.activity.page.SelectionMenuItem
import net.bible.android.view.activity.page.selectionMenuItems
import net.bible.sharedcore.log.nsLog
import net.bible.sharedcore.platform.AppJson
import net.bible.sharedui.strings.keyed.KeyedSelectionMenuStrings

/** What the selection menu needs from one reading window: the window's bridge actions (Android: the `BibleView` methods). */
@kotlin.native.HiddenFromObjC
interface SelectionMenuWindow {
    /** The window's first document is a Bible. */
    val isBible: Boolean
    fun makeBookmark(selection: Selection?, wholeVerse: Boolean)
    fun addParagraphBreak(selection: Selection?)
    fun compare(selection: Selection?)
    fun memorize(selection: Selection?)
    fun share(selection: Selection)

    /** Removes the page's selection ranges (Android: `stopSelection(removeRanges = true)`). */
    fun clearSelection()
}

/** What the selection menu needs from the app: settings, link handling and the actions that leave the window. */
@kotlin.native.HiddenFromObjC
interface SelectionMenuEnvironment {
    val twoStepDisabled: Boolean
    val paragraphBreakEnabled: Boolean
    val hasLookupDictionaries: Boolean
    val llmConfigured: Boolean

    /** The short display name of the scripture reference [text] resolves to, or null when it is not a reference. */
    fun resolveRef(text: String): String?
    fun openRef(text: String)
    fun lookupInDictionaries(text: String)

    /** Opens the reading view's search seeded with [text] as a phrase search. */
    fun search(text: String)
    fun webSearch(text: String)
    fun copy(text: String)
}

/** A selection as the page reported it, with what the menu derives from it once (Android's `currentSelection*`). */
@kotlin.native.HiddenFromObjC
class CachedSelection(val selection: Selection?, val text: String, val refName: String?)

/** One row of the menu: the policy item and its label. */
@kotlin.native.HiddenFromObjC
class SelectionMenuRow(val item: SelectionMenuItem, val title: String)

/**
 * The text-selection menu's logic on iOS (Android: `BibleView.onPrepareActionMenu` and `onActionMenuItemClicked`).
 *
 * UIKit builds the edit menu synchronously, while the page can only be asked for its selection asynchronously. So the
 * selection is cached per window ahead of time: the page reports it (debounced) on every `selectionchange`, see
 * [SELECTION_USER_SCRIPT], and [cacheSelection] stores it. [rows] and [run] only read the cache and never wait.
 *
 * Which rows show is the common [selectionMenuItems] policy, the very function Android's menu uses. Two-step bookmarking
 * on a Bible: [rows] lists the single "Bookmark" row and [stepTwoRows] the choices behind it (the UI shows them as that
 * row's submenu, so no second menu has to be re-presented); on a non-Bible [run] of [SelectionMenuItem.AddBookmark]
 * bookmarks straight away, as Android does.
 *
 * @param window the window's actions, null for an unknown or released window
 * @param onLlmAction the AI action for a selection (Android: `showLlmPromptSelector`); the reading host opens the
 *   prompt selector. The row is hidden while [SelectionMenuEnvironment.llmConfigured] is false.
 */
@kotlin.native.HiddenFromObjC
class IosSelectionMenu(
    private val windows: (windowId: String) -> SelectionMenuWindow?,
    private val environment: SelectionMenuEnvironment,
    private val strings: () -> KeyedSelectionMenuStrings,
    private val onLlmAction: (windowId: String, selection: Selection) -> Unit = { _, _ -> },
) {
    private val cache = mutableMapOf<String, CachedSelection>()

    /**
     * Stores the selection the page reported for [windowId]: [raw] is the JSON of `bibleView.querySelection()`
     * (an object for a verse selection, a string for plain text, `null` for none). Returns whether the cached
     * selection changed, so the caller can ask UIKit to rebuild a menu it already built from the old one.
     */
    fun cacheSelection(windowId: String, raw: String): Boolean {
        val parsed = parse(raw)
        val old = cache[windowId]
        if (parsed == null) return cache.remove(windowId) != null
        cache[windowId] = parsed
        return old == null || old.text != parsed.text || old.selection?.startOrdinal != parsed.selection?.startOrdinal ||
            old.selection?.endOrdinal != parsed.selection?.endOrdinal
    }

    /** Forgets the window's selection (the window was released, or an action consumed it). */
    fun clear(windowId: String) {
        cache.remove(windowId)
    }

    /** The window's cached selection; the UI takes it when it builds the menu and hands it back to [run]. */
    fun snapshot(windowId: String): CachedSelection? = cache[windowId]

    private fun parse(raw: String): CachedSelection? {
        if (raw == "null" || raw.isBlank()) return null
        val selection = try { AppJson.decodeFromString(kotlinx.serialization.serializer<Selection?>(), raw) } catch (e: Exception) { null }
        val text = selection?.text
            ?: try {
                AppJson.decodeFromString(kotlinx.serialization.serializer<String>(), raw)
            } catch (e: Exception) {
                // Neither a Selection object nor a JSON string: keep the raw text (as Android does) but say so.
                nsLog("selection menu: unreadable selection JSON (${raw.length} characters): $e")
                raw
            }
        return CachedSelection(selection, text, runCatching { environment.resolveRef(text) }.getOrNull())
    }

    private fun input(windowId: String, sel: CachedSelection) = SelectionMenuInput(
        isBible = windows(windowId)?.isBible ?: false,
        twoStepDisabled = environment.twoStepDisabled,
        hasVerseSelection = sel.selection != null,
        selectionText = sel.text,
        resolvedRef = sel.refName != null,
        paragraphBreakEnabled = environment.paragraphBreakEnabled,
        hasLookupDictionaries = environment.hasLookupDictionaries,
        llmConfigured = environment.llmConfigured,
    )

    /** The policy's items for the window's [selection] (cached one by default); empty without a selection. */
    fun items(windowId: String, step2: Boolean = false, selection: CachedSelection? = cache[windowId]): List<SelectionMenuItem> {
        selection ?: return emptyList()
        if (windows(windowId) == null) return emptyList() // a released or unknown window shows nothing
        return selectionMenuItems(input(windowId, selection), step2)
    }

    /** The first-step rows with labels, empty without a cached selection. */
    fun rows(windowId: String, selection: CachedSelection? = cache[windowId]): List<SelectionMenuRow> =
        items(windowId, false, selection).map { SelectionMenuRow(it, label(it, selection)) }

    /** The rows behind the single "Bookmark" row of a two-step Bible. */
    fun stepTwoRows(windowId: String, selection: CachedSelection? = cache[windowId]): List<SelectionMenuRow> =
        items(windowId, true, selection).map { SelectionMenuRow(it, label(it, selection)) }

    /** True for the "Bookmark" row of a two-step Bible: it opens [stepTwoRows] instead of bookmarking. */
    fun opensStepTwo(windowId: String, item: SelectionMenuItem): Boolean =
        item == SelectionMenuItem.AddBookmark && windows(windowId)?.isBible == true

    private fun label(item: SelectionMenuItem, selection: CachedSelection?): String = strings().let { s ->
        when (item) {
            SelectionMenuItem.AddBookmark -> s.addBookmark
            SelectionMenuItem.AddBookmarkSelection -> s.addBookmarkSelection
            SelectionMenuItem.AddBookmarkWholeVerse -> s.addBookmarkWholeVerse
            SelectionMenuItem.AddParagraphBreak -> s.addParagraphBreak
            SelectionMenuItem.Compare -> s.compare
            SelectionMenuItem.Memorize -> s.memorize
            SelectionMenuItem.ShareVerses -> s.shareVerses
            SelectionMenuItem.OpenRef -> selection?.refName?.let(s::goToRef) ?: s.search
            SelectionMenuItem.Search -> selection?.text?.let(s::searchLabel) ?: s.search
            SelectionMenuItem.WebSearch -> s.webSearch
            SelectionMenuItem.LookupDictionary -> s.lookupDictionary
            SelectionMenuItem.Copy -> s.copy
            SelectionMenuItem.LlmAction -> s.llmAction
        }
    }

    /**
     * Carries out [item] on [windowId] with [selection] (the one the menu was built from, so a selection that collapses
     * while the menu closes still counts). The selection is consumed afterwards, as Android's `stopSelection(true)`.
     */
    fun run(windowId: String, item: SelectionMenuItem, selection: CachedSelection? = cache[windowId]) {
        selection ?: return
        val window = windows(windowId) ?: return
        val verse = selection.selection
        val text = selection.text
        when (item) {
            // On a Bible the row only opens the step-two choices (a submenu); a non-Bible bookmarks at once.
            SelectionMenuItem.AddBookmark -> if (!window.isBible) window.makeBookmark(verse, false) else return
            SelectionMenuItem.AddBookmarkSelection -> window.makeBookmark(verse, false)
            SelectionMenuItem.AddBookmarkWholeVerse -> window.makeBookmark(verse, true)
            SelectionMenuItem.AddParagraphBreak -> window.addParagraphBreak(verse)
            SelectionMenuItem.Compare -> window.compare(verse)
            SelectionMenuItem.Memorize -> window.memorize(verse)
            SelectionMenuItem.ShareVerses -> verse?.let(window::share)
            SelectionMenuItem.OpenRef -> environment.openRef(text)
            SelectionMenuItem.Search -> environment.search(text)
            SelectionMenuItem.WebSearch -> environment.webSearch(text)
            SelectionMenuItem.LookupDictionary -> environment.lookupInDictionaries(text)
            SelectionMenuItem.Copy -> environment.copy(text)
            SelectionMenuItem.LlmAction -> verse?.let { onLlmAction(windowId, it) }
        }
        nsLog("selection menu: $item on $windowId")
        cache.remove(windowId)
        window.clearSelection()
    }
}

/**
 * Posts the page's selection to the native `"selection"` handler, debounced: UIKit builds the edit menu at once, so the
 * cache must be fresh a moment after the selection settles (80 ms is below the time a finger needs to lift). The posted
 * text is the JSON of `bibleView.querySelection()` (`null` when the selection is gone).
 */
internal const val SELECTION_USER_SCRIPT: String =
    "(function(){var t=null;document.addEventListener('selectionchange',function(){clearTimeout(t);t=setTimeout(function(){" +
        "try{var s=(typeof bibleView!=='undefined'&&bibleView.querySelection)?bibleView.querySelection():null;" +
        "var j=JSON.stringify(s===undefined?null:s);window.webkit.messageHandlers.selection.postMessage(j===undefined?'null':j);}catch(e){}" +
        "},80);});})();"

/** The name of the script-message handler the selection script posts to. */
internal const val SELECTION_HANDLER_NAME: String = "selection"

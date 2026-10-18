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

package net.bible.sharedui.reading.host.slots

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import net.bible.android.control.search.SearchControl
import net.bible.sharedcore.search.IndexPollDecision
import net.bible.sharedcore.search.PollOutcome
import net.bible.sharedcore.search.ReadingSearchPhase
import net.bible.sharedui.search.BibleResultsActions
import net.bible.sharedui.search.BibleSearchSettings
import net.bible.sharedui.search.EpubSearchSettings
import net.bible.sharedui.search.SearchIndexPanel
import net.bible.sharedui.search.SearchSettingsSheet
import net.bible.sharedui.search.SearchSheetContent
import net.bible.sharedui.search.bibleResultRows
import net.bible.sharedui.search.epubResultRows
import net.bible.sharedui.strings.Strings

/**
 * The modal search-settings sheet (the toolbar's Tune affordance). [forEpub] and [currentBookName]
 * are read at composition time, not collected: the sheet is modal and composes its content only
 * while open, so the active window cannot change under it. [onClosed] re-runs a search already in
 * flight (`ReadingSearchController.settingsClosed`).
 */
@kotlin.native.HiddenFromObjC
@Composable
internal fun SearchSettingsSlotContent(
    state: ReadingSearchState,
    forEpub: () -> Boolean,
    currentBookName: () -> String,
    onClosed: () -> Unit,
) {
    SearchSettingsSheet(
        open = state.searchSettingsOpen.value,
        onDismiss = {
            state.searchSettingsOpen.value = false
            onClosed()
        },
    ) {
        if (forEpub()) {
            val mode by state.epubSearchMode.collectAsState()
            EpubSearchSettings(mode = mode, onMode = state::persistEpubSearchMode)
        } else {
            BibleSearchSettings(
                searchType = state.searchType.value,
                bibleSection = state.searchSection.value,
                availableTranslations = state.searchAvailableTranslations.value,
                selectedTranslationIds = state.searchTranslations.value,
                currentBookName = currentBookName(),
                onSearchType = { state.searchType.value = it },
                onBibleSection = { state.searchSection.value = it },
                onTranslations = { ids -> state.setSearchTranslations(ids) },
            )
        }
    }
}

/**
 * What the search sheet holds, per phase: the index prompt/progress panel while the document has no
 * usable index, the results otherwise. `Form` and `Closed` render nothing: the form lives in the
 * toolbar and in the modal settings sheet, not in this sheet.
 */
@kotlin.native.HiddenFromObjC
@Composable
internal fun SearchSheetSlotContent(state: ReadingSearchState, strings: Strings) {
    val platform = state.platform
    val controller = state.controller
    val phase by controller.phase.collectAsState()
    val indexDocId = when (val p = phase) {
        is ReadingSearchPhase.NeedsIndex -> p.docId
        is ReadingSearchPhase.Indexing -> p.docId
        else -> null
    }
    if (indexDocId != null) {
        val jobs by state.searchIndexProgress.jobs.collectAsState()
        val indexError by state.searchIndexProgress.error.collectAsState()
        // The name is invariant per document, so it is resolved once rather than on every
        // recomposition of a progressing index.
        val documentName = remember(indexDocId) {
            platform.documentByInitials(indexDocId)?.name ?: indexDocId
        }
        // Deliberately NOT remembered: `createIndex` deletes the existing index up front, so a
        // failed rebuild leaves the document with NO index even though it had one when this
        // composable was entered, and the fail->NeedsIndex retry carries the same docId. A plain
        // read is a single cheap existence check.
        val isRebuild = state.searchIndexService.hasIndex(indexDocId)
        SearchIndexPanel(
            documentName = documentName,
            isRebuild = isRebuild,
            indexing = phase is ReadingSearchPhase.Indexing,
            jobs = jobs,
            error = indexError,
            onCreate = { controller.acceptIndexing() },
            // Declining the index is declining the search: there is nothing else this session
            // could do with a document Lucene cannot read.
            onCancel = { state.leaveSearch() },
            onDismissError = state.searchIndexProgress::dismissError,
            engineChanged = state.searchIndexService.hasLegacyIndex(indexDocId),
        )
        return
    }
    val resultsPhase = phase as? ReadingSearchPhase.Results ?: return
    if (resultsPhase.forEpub) {
        val loading by state.epubSearchResults.loading.collectAsState()
        val rows by state.epubSearchResults.results.collectAsState()
        val error by state.epubSearchResults.error.collectAsState()
        val docAbbrev = remember(resultsPhase.docId) {
            platform.documentByInitials(resultsPhase.docId)?.abbreviation ?: resultsPhase.docId
        }
        // Classic's "+" overflow affordance: the service caps at MAX+1, so size > MAX is detectable.
        val amount =
            if (rows.size > SearchControl.MAX_SEARCH_RESULTS) "${SearchControl.MAX_SEARCH_RESULTS}+"
            else rows.size.toString()
        SearchSheetContent(
            countLabel = strings.searchWithResults2(amount, docAbbrev),
            loading = loading,
            // `SearchSheetContent.error` is a `String?`; the EPUB controller carries only a Boolean,
            // so the message is supplied here (the string the standalone EPUB results screen shows).
            error = if (error) strings.errorExecutingSearch else null,
            empty = !loading && rows.isEmpty(),
            listState = state.searchResultsListState.value,
            onDismissError = state.epubSearchResults::dismissError,
            actions = {},
        ) {
            epubResultRows(rows = rows, onSelect = state.epubSearchResults::select)
        }
        return
    }
    val searchResults = state.searchResults
    val loading by searchResults.loading.collectAsState()
    val results by searchResults.results.collectAsState()
    val rows by searchResults.displayed.collectAsState()
    val scriptureShown by searchResults.scriptureShown.collectAsState()
    val scriptureToggleVisible by searchResults.scriptureToggleVisible.collectAsState()
    val error by searchResults.error.collectAsState()
    val selected by searchResults.selectedTranslations.collectAsState()
    val candidates by searchResults.candidates.collectAsState()
    SearchSheetContent(
        countLabel = strings.multiSearchResults(results.total, selected.size),
        loading = loading,
        error = error,
        // "Nothing found" only once a search has actually finished: an empty list while loading is
        // not an empty result.
        empty = !loading && rows.isEmpty(),
        listState = state.searchResultsListState.value,
        onDismissError = searchResults::dismissError,
        actions = {
            BibleResultsActions(
                candidates = candidates,
                selectedIds = selected,
                selectedAbbreviations = selected
                    .mapNotNull { id -> candidates.firstOrNull { it.id == id }?.abbreviation }
                    .joinToString(", "),
                scriptureToggleVisible = scriptureToggleVisible,
                scriptureShown = scriptureShown,
                onToggleScripture = searchResults::toggleScripture,
                onOpenInWindow = state::openSearchResultsInWindow,
                onSelectTranslations = { ids -> state.onSearchTranslationsChosen(ids) },
            )
        },
    ) {
        bibleResultRows(
            rows = rows,
            expanded = state.searchResultsExpanded,
            // F27: labelling a single-match card with its translation only says anything when more
            // than one translation was searched.
            labelSingleMatchTranslation = selected.size > 1,
            onSelect = state::onSearchResultSelected,
        )
    }
}

/**
 * Waits out JSword's "the job says finished before `indexStatus` says DONE" gap and reports whether
 * the index actually appeared. The bookkeeping (and the ~12s cap) is [poll]'s; the waiting is
 * [pause]'s, so this is testable with no clock at all.
 */
@kotlin.native.HiddenFromObjC
suspend fun awaitIndexDone(
    poll: IndexPollDecision,
    indexDone: () -> Boolean,
    pause: suspend () -> Unit,
): Boolean {
    while (true) {
        when (poll.onPoll(indexDone())) {
            PollOutcome.Done -> return true
            PollOutcome.GaveUp -> return false
            PollOutcome.KeepPolling -> pause()
        }
    }
}

/**
 * Whether a job-feed event may resolve the index build the sheet is waiting on. The feed is global
 * (an event fires for EVERY job, a module download included), so the gate is "this job finished AND
 * no job is still running anywhere", not the build's own identity (which no caller ever sees). It
 * sits BEFORE the poll because the ~12 s budget lives in one `IndexPollDecision` per build.
 */
@kotlin.native.HiddenFromObjC
fun shouldResolveIndexBuild(jobFinished: Boolean, everyJobFinished: Boolean): Boolean =
    jobFinished && everyJobFinished

/**
 * The chaining decision once a build resolves: the next unindexed translation id to prompt for from
 * the selector's chosen set, or `null` when there is nothing to chain (the build failed, [pendingIds]
 * is `null` because this is not a selector-driven run, or everything is indexed now).
 */
@kotlin.native.HiddenFromObjC
fun nextSelectorIndexPrompt(
    pendingIds: List<String>?,
    indexDone: Boolean,
    unindexedAmong: (List<String>) -> List<String>,
): String? {
    if (pendingIds == null || !indexDone) return null
    return unindexedAmong(pendingIds).firstOrNull()
}

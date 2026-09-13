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
package net.bible.android.view.activity.page.screen

import net.bible.android.activity.R
import net.bible.service.sword.epub.epubBackend
import net.bible.service.sword.epub.isEpub
import net.bible.sharedcore.search.IndexPollDecision
import net.bible.sharedcore.search.PollOutcome
import net.bible.sharedcore.search.SearchDocumentCategory
import net.bible.sharedcore.search.SearchDocumentInfo
import net.bible.sharedui.strings.Strings
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.index.IndexStatus

/**
 * The pure, host-state-free half of [ComposeReadingViewHost]'s companion object: the JSword/search
 * helpers and the two drawable-id tables. Nav-graph slice 7 Task 17 moved them here verbatim to get
 * ~230 lines out of a 4800-line file; nothing in the block reads `activity`, host state or `this`,
 * which is what made it the one part of the split that is a file move rather than a refactor.
 *
 * **Why a superclass of the companion, and not top-level functions.** Every caller — production and
 * the four test classes that pin these (`ReadingSearchHostTest`, `ComposeReadingViewHostTest`,
 * `OptionsMenuStateBuilderTest`, `WindowPaneMenuStateBuilderTest`) — names them
 * `ComposeReadingViewHost.x(...)`. Members inherited by a companion object stay reachable through the
 * class name, so the move touches no call site at all; top-level declarations (or extensions on the
 * companion, which need an import of their own) would have meant editing the very tests whose
 * unchanged pass/fail counts are this move's proof of purity.
 *
 * **Stays in `:app`.** [drawerIconResIds]/[menuIconResIds] resolve `R.drawable`, and the rest speaks
 * JSword `Book`, so `commonMain` is out of the question. The six functions alone would fit a JVM
 * source set (`:sharedCore`'s `jvmMain`), but moving across a module boundary is not this task.
 */
abstract class ComposeReadingViewHostHelpers {
    /**
     * The JSword `Book` → portable [SearchDocumentInfo] mapping that feeds
     * [net.bible.sharedcore.search.searchKindFor] (F6 Task 8a Step 1) — the one place the
     * reading view turns a document into "what kind of search does this support".
     *
     * Non-private (`internal`, on the companion) for the same reason as [ComposeReadingViewHost.buildTabBarModel]:
     * `ReadingSearchHostTest` asserts the mapping — and with it both defects the spec's §7
     * names — against real `SwordBook`s, without booting a [MainBibleActivity].
     *
     * Two mapping details carry the fixes:
     * - every category outside Bible/Commentary/Dictionary becomes [SearchDocumentCategory.GENERAL_BOOK],
     *   which `searchKindFor` reports as `Unavailable` unless it is an EPUB;
     * - `isEpub` is carried separately (rather than folded into the category, which JSword
     *   reports as GENERAL_BOOK for an EPUB), because `searchKindFor` must test it FIRST.
     */
    internal fun searchDocumentInfo(book: Book?): SearchDocumentInfo? {
        if (book == null) return null
        val category = when (book.bookCategory) {
            BookCategory.BIBLE -> SearchDocumentCategory.BIBLE
            BookCategory.COMMENTARY -> SearchDocumentCategory.COMMENTARY
            BookCategory.DICTIONARY -> SearchDocumentCategory.DICTIONARY
            else -> SearchDocumentCategory.GENERAL_BOOK
        }
        return SearchDocumentInfo(
            docId = book.initials,
            category = category,
            isEpub = book.isEpub,
            indexDone = documentIndexDone(book),
        )
    }

    /**
     * Whether [book]'s search index actually exists. For everything but an EPUB that is
     * `indexStatus`; for an EPUB it is the FTS5 table's existence, read from the book's own
     * backend, because `indexStatus` is not reliable for EPUBs — `EpubBook.addEpubBook` re-derives
     * it from the LUCENE index manager (which is never true for an EPUB) and
     * `EpubBackendState.buildSearchIndex` returns early without setting DONE when the index is
     * already there. Both are fixed at the source too, but this read is what makes the reading
     * view's search independent of them ever regressing.
     *
     * Deliberately NOT `SwordDocumentFacade.hasIndex`, which re-resolves the book through
     * `Books.installed()` — an indirection that buys nothing here and that a test-constructed
     * book is not in.
     */
    internal fun documentIndexDone(book: Book): Boolean =
        documentIndexDone(book) { it.epubBackend?.state?.isIndexed == true }

    /**
     * The two-argument overload exists so a unit test can drive both sides of the EPUB
     * branch: no unit test can construct a real `EpubBackend` (that needs an EPUB directory
     * plus its Room database, and `EpubSearch`'s SQLite driver is deliberately null under unit
     * tests), so [epubIndexed] is the seam a test substitutes to prove the branch, while the
     * single-argument entry point above wires the real backend read for production.
     */
    internal fun documentIndexDone(book: Book, epubIndexed: (Book) -> Boolean): Boolean =
        if (book.isEpub) epubIndexed(book) else book.indexStatus == IndexStatus.DONE

    /**
     * Waits out JSword's "the job says finished before `indexStatus` says DONE" gap and reports
     * whether the index actually appeared. The bookkeeping (and the ~12s cap) is [poll]'s; the
     * waiting is [pause]'s, so this is testable with no clock at all.
     *
     * `internal` so `ReadingSearchHostTest` drives the REAL loop rather than a replica of it —
     * the `GaveUp` outcome (an index that finishes but never reaches DONE) is exactly what the
     * cap exists for, and what must land back in `NeedsIndex` instead of empty `Results`.
     */
    internal suspend fun awaitIndexDone(
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
     * The "cannot be searched" snackbar's text (review item 2).
     *
     * [ComposeReadingViewHost.searchUnavailableDocName] is composed from
     * `currentDocument?.name.orEmpty()`, and "there is no current document" — the reading view
     * showing an error page — is itself one of `searchKindFor`'s `Unavailable` cases, so a blank
     * name is reachable and the parameterised string then read " cannot be searched". Pure and
     * `internal` so `ReadingSearchHostTest` can assert both branches against real resources
     * without a `ComposeTestRule` to render the snackbar.
     */
    internal fun searchUnavailableMessage(docName: String, strings: Strings): String =
        if (docName.isBlank()) strings.searchNotAvailable
        else strings.searchNotAvailableForDocument(docName)

    /**
     * Whether a `JobManager` work event may resolve the index build the search sheet is waiting
     * on — i.e. poll `indexStatus`, possibly report failure, tear the feed down and hand the
     * session back to [ReadingSearchController.onIndexingFinished].
     *
     * Review I1 (a regression against classic, not a port of one). `JobManager`'s listener is
     * global: a `WorkEvent` fires for EVERY JSword job, so [jobFinished] says nothing about the
     * index build. Resolving on it meant an unrelated finished job (a module download, an
     * install) tore the feed down mid-build — after which the real completion had nobody
     * listening, the phase had already fallen back to `NeedsIndex`, and that prompt's Create ran
     * `deleteDocumentIndex` under the build that was about to succeed. Classic's
     * `SearchIndexProgressComposeActivity` has the same "any finished job" shape but keeps its
     * listener registered until `onPause` (`:92-96`), so the real completion is still handled
     * there; the early teardown is the port's own doing.
     *
     * So the gate is [everyJobFinished] — no JSword job is still running anywhere — and NOT the
     * identity of the build's own `Progress`, for two reasons: the host never sees that object
     * (JSword creates it inside `IndexManager.scheduleIndexCreation`, layers below
     * `SearchIndexServiceImpl.createIndex`, and nothing on that path returns it), and matching by
     * `jobName` would key behaviour on a localised progress label. The cost is deferral, not
     * correctness: while a foreign job is still running nothing resolves, and that job's own
     * finish event then opens this gate, whereupon the poll finds the index `DONE`.
     *
     * It also has to sit BEFORE the poll rather than after it: the ≤12 s budget lives in one
     * [IndexPollDecision] per build (its own kdoc: "the counter is not reset"), so polling on a
     * foreign job would spend the real completion's budget and report a false failure.
     */
    internal fun shouldResolveIndexBuild(jobFinished: Boolean, everyJobFinished: Boolean): Boolean =
        jobFinished && everyJobFinished

    /**
     * F6 Task 11's chaining decision for [ComposeReadingViewHost.onSearchIndexWorkEvent]: once a build resolves, decides
     * whether to prompt again for a DIFFERENT translation from the results selector's chosen set,
     * or let [ReadingSearchController.onIndexingFinished] behave exactly as it did before this
     * task. Returns the next unindexed translation id to prompt for, or `null` when there is
     * nothing left to chain — the build failed, [pendingIds] is `null` (this is not a
     * selector-driven run at all — the plain "document being read has no index" flow), or
     * [unindexedAmong] reports every id in [pendingIds] as indexed now.
     *
     * `internal` on the companion for the same reason as [awaitIndexDone]/[searchDocumentInfo]
     * just above: `ReadingSearchHostTest` drives the decision directly, with no real JSword
     * `Progress`/`WorkEvent`/`JobManager` round trip (the only production caller,
     * [ComposeReadingViewHost.onSearchIndexWorkEvent], is itself untested end-to-end for the same reason `awaitIndexDone`
     * already is not).
     */
    internal fun nextSelectorIndexPrompt(
        pendingIds: List<String>?,
        indexDone: Boolean,
        unindexedAmong: (List<String>) -> List<String>,
    ): String? {
        if (pendingIds == null || !indexDone) return null
        return unindexedAmong(pendingIds).firstOrNull()
    }

    /**
     * Drawable-name -> `R.drawable.*` for every icon the Compose drawer can ask for: the 22
     * `iconKey`s of [DrawerMenuStateBuilder]'s static table plus `ic_logo` (the header, which
     * `ReadingDrawerContent` requests directly).
     *
     * Batch Z-early A7 fix F — this replaces a per-row, per-recomposition
     * `resources.getIdentifier(key, "drawable", packageName)`. Beyond the (minor) cost, name
     * lookup is invisible to R8: a resource referenced only by string silently resolves to `0`
     * once resource shrinking runs in a release build, so the icons would vanish from the
     * release drawer only. Direct `R.drawable` references mark them used and resolve at compile
     * time. Kept in step with the builder's table by
     * `ComposeReadingViewHostTest.drawerIconResIdsCoverEveryBuilderIconKey`.
     */
    internal val drawerIconResIds: Map<String, Int> = mapOf(
        "ic_logo" to R.drawable.ic_logo,
        "ic_library_books_white_24dp" to R.drawable.ic_library_books_white_24dp,
        "ic_search_24dp" to R.drawable.ic_search_24dp,
        "ic_baseline_headphones_24" to R.drawable.ic_baseline_headphones_24,
        "ic_baseline_bookmark_24" to R.drawable.ic_baseline_bookmark_24,
        "ic_baseline_studypads_24" to R.drawable.ic_baseline_studypads_24,
        "ic_baseline_description_24" to R.drawable.ic_baseline_description_24,
        "ic_reading_plan_24dp" to R.drawable.ic_reading_plan_24dp,
        "ic_bar_chart_24dp" to R.drawable.ic_bar_chart_24dp,
        "ic_history_clock_24dp" to R.drawable.ic_history_clock_24dp,
        "ic_file_download_24dp" to R.drawable.ic_file_download_24dp,
        "ic_settings_backup_restore_db_24dp" to R.drawable.ic_settings_backup_restore_db_24dp,
        "ic_syncdb_24dp" to R.drawable.ic_syncdb_24dp,
        "icon_robot" to R.drawable.icon_robot,
        "ic_settings_white_24dp" to R.drawable.ic_settings_white_24dp,
        "ic_help_white_24dp" to R.drawable.ic_help_white_24dp,
        "baseline_attach_money_24" to R.drawable.baseline_attach_money_24,
        "ic_need_help_24dp" to R.drawable.ic_need_help_24dp,
        "ic_baseline_emoji_people_24" to R.drawable.ic_baseline_emoji_people_24,
        "ic_baseline_copyright_24" to R.drawable.ic_baseline_copyright_24,
        "ic_baseline_people_24" to R.drawable.ic_baseline_people_24,
        "ic_rate_review_white_24dp" to R.drawable.ic_rate_review_white_24dp,
        "ic_bug_report_white_24dp" to R.drawable.ic_bug_report_white_24dp,
    )

    /**
     * Drawable-name -> `R.drawable.*` for every icon the per-window (☰) pane popup menu
     * ([WindowPaneMenuStateBuilder]) or the toolbar's overflow ("3-dot") menu
     * ([OptionsMenuStateBuilder]) can ask for — 22 entries total (14 shared with, or unique to,
     * the pane menu's `window_popup_menu.xml` table, plus 8 more from the overflow menu's
     * `main_bible_options_menu.xml` table; `ic_baseline_headphones_24`/
     * `ic_baseline_bookmark_24`/`icon_robot` also appear in [drawerIconResIds] above — kept as
     * separate entries here rather than merged, mirroring how each menu owns its own
     * self-contained table).
     *
     * A/B batch 1 F5b — same rationale as [drawerIconResIds]: a name-only
     * `resources.getIdentifier` lookup is invisible to R8 (silently resolves to `0` once
     * release resource shrinking runs), so direct `R.drawable` references are used instead.
     * Kept in step with both builders' tables by
     * `WindowPaneMenuStateBuilderTest.everyPaneMenuIconKeyIsResolvableByTheHost` and
     * `OptionsMenuStateBuilderTest.everyOverflowIconKeyIsResolvableByTheHost`.
     */
    internal val menuIconResIds: Map<String, Int> = mapOf(
        "ic_window_add_outline_black_24dp" to R.drawable.ic_window_add_outline_black_24dp,
        "ic_window_maximise_24dp" to R.drawable.ic_window_maximise_24dp,
        "ic_baseline_minimise_24" to R.drawable.ic_baseline_minimise_24,
        "ic_link_black_24dp" to R.drawable.ic_link_black_24dp,
        "ic_window_move_to_24dp" to R.drawable.ic_window_move_to_24dp,
        "ic_pin" to R.drawable.ic_pin,
        "ic_window_sync_24dp" to R.drawable.ic_window_sync_24dp,
        "ic_baseline_bookmark_24" to R.drawable.ic_baseline_bookmark_24,
        "file_export" to R.drawable.file_export,
        "ic_text_options_24dp" to R.drawable.ic_text_options_24dp,
        "ic_content_copy_black_24dp" to R.drawable.ic_content_copy_black_24dp,
        "baseline_content_paste_24" to R.drawable.baseline_content_paste_24,
        "ic_baseline_headphones_24" to R.drawable.ic_baseline_headphones_24,
        "ic_close_white_24dp" to R.drawable.ic_close_white_24dp,
        "ic_full_screen_24" to R.drawable.ic_full_screen_24,
        "ic_night_mode_24" to R.drawable.ic_night_mode_24,
        "ic_baseline_workspace_24" to R.drawable.ic_baseline_workspace_24,
        "ic_tilt_to_scroll_24dp" to R.drawable.ic_tilt_to_scroll_24dp,
        "ic_reverse_split_mode_24dp" to R.drawable.ic_reverse_split_mode_24dp,
        "ic_window_pinning_24" to R.drawable.ic_window_pinning_24,
        "ic_label_settings_24" to R.drawable.ic_label_settings_24,
        "icon_robot" to R.drawable.icon_robot,
    )
}

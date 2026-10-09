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

package net.bible.android.view.activity.nav

import android.app.Activity
import android.content.Intent
import androidx.activity.result.ActivityResult
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.control.progress.ReadingProgressServiceImpl
import net.bible.android.view.activity.page.ActivityResultKind
import net.bible.sharedcore.nav.BookmarkResult
import net.bible.sharedcore.nav.DocumentResult
import net.bible.sharedcore.nav.KeyChooserResult
import net.bible.sharedcore.nav.LabelEditResult
import net.bible.sharedcore.nav.ManageLabelsResult
import net.bible.sharedcore.nav.MyDocumentPagesResult
import net.bible.sharedcore.nav.MyDocumentsResult
import net.bible.sharedcore.nav.PassageResult
import net.bible.sharedcore.nav.ReadingProgressResult
import net.bible.sharedcore.nav.WorkspaceResult

/**
 * The one place a `commonMain` nav RESULT is packed into the `Intent` (and result code) the
 * classic `Activity` contract expects. Each function is byte-identical to the host code that
 * built the same `Intent` by hand before `NavResultChannel` existed, and each is covered by
 * `NavResultIntentsTest` — which is the point of this object. An `exitWithResult` lambda inside
 * `NavHostComposeActivity` can only be exercised by launching the host, so in practice it is not
 * tested at all; a pure function over a result type is.
 *
 * An earlier revision of this kdoc scoped the object to [ReadingProgressResult]. That described
 * the one function it then had, not a boundary: any result type a destination delivers belongs
 * here. A result type earns a function the moment a destination actually delivers on its
 * channel — a channel whose destination has not landed yet keeps a placeholder lambda at its
 * declaration instead, so that the packing and the test that pins it arrive together, in the task
 * that builds the destination. As of slice 2, Task 6 there is no such placeholder left: all four
 * result types below have a live destination.
 *
 * It uses `ReadingProgressServiceImpl.osisIdForChapter` (the companion form): that reads no instance
 * state — it re-resolves the KJVA versification on every call — so this object needs no DI wiring
 * to reuse the exact same conversion the host used.
 */
object NavResultIntents {
    /**
     * `MainBibleActivity.kt:2930-2957` reads exactly these extras — same keys, same order, same
     * [ActivityResultKind.EXTRA] tag — and is not touched by this move.
     */
    fun forReadingProgress(result: ReadingProgressResult): Intent = when (result) {
        is ReadingProgressResult.Chapter ->
            Intent()
                .putExtra("verse", ReadingProgressServiceImpl.osisIdForChapter(result.bookId, result.chapter))
                .putExtra(ActivityResultKind.EXTRA, ActivityResultKind.ReadingProgress.name)

        is ReadingProgressResult.Memorize ->
            Intent()
                .putExtra("action", "memorize")
                .putExtra("startOrdinal", result.startOrdinal)
                .putExtra("endOrdinal", result.endOrdinal)
                .putExtra(ActivityResultKind.EXTRA, ActivityResultKind.ReadingProgress.name)
    }

    /**
     * The label editor's exit: classic `LabelEditComposeActivity.finishWithData` (`:249-254`) and
     * the `Cancel` arm of its `onFinish` (`:242-245`). This pair — the `"data"` extra carrying
     * `LabelEditContract.LabelData`'s JSON, and the OK/CANCELED split — is the WHOLE result contract
     * `ManageLabelsComposeActivity`'s `registerForActivityResult` launcher reads back, which makes it
     * the part of that destination that most needs a test rather than a comment.
     *
     * Returns the result code WITH the `Intent` rather than an `Intent?` for the caller to grade,
     * because here the two are ONE decision: [LabelEditResult.Cancelled] is `RESULT_CANCELED` and no
     * `data` extra at all, not `RESULT_OK` with an empty one. Split across two call sites, that is
     * exactly the kind of pairing that drifts. [ActivityResult] is reused rather than a local pair
     * type — it is already the framework's name for "a result code and its optional Intent", and
     * `ActivityBase` already speaks it.
     *
     * The controller's three outcomes became these two earlier, in
     * `NavHostComposeActivity.labelEditControllerFor`; by the time a result reaches here it is
     * already one of the two.
     */
    fun forLabelEdit(result: LabelEditResult): ActivityResult = when (result) {
        is LabelEditResult.Saved ->
            ActivityResult(Activity.RESULT_OK, Intent().putExtra("data", result.data))

        LabelEditResult.Cancelled -> ActivityResult(Activity.RESULT_CANCELED, null)
    }

    /**
     * The label MANAGER's exit: classic `ManageLabelsComposeActivity.saveAndExit` (`:635`) and the
     * HIDELABELS branch of its `reset` (`:667`). Both build the *identical*
     * `Intent().putExtra("data", data.toJSON())` — the reset path differs only in calling
     * `ManageLabelsMapper.applyReset(data)` first, which flips a `reset` FIELD on the payload
     * (`ManageLabelsContract.kt:60`) rather than producing a second Intent shape. That is why
     * [ManageLabelsResult] carries just the JSON string and this function has no branch at all.
     *
     * Returns a bare [Intent], NOT an [ActivityResult] like [forLabelEdit], and the difference is
     * not a style choice: [forLabelEdit] pairs the two because `Cancelled` flips BOTH halves
     * (`RESULT_CANCELED` *and* no Intent), so splitting them across call sites is what would drift.
     * `ManageLabels` has one result code on every exit — classic never calls `setResult` with
     * anything but `RESULT_OK`, and has no cancel path at all (its Back press *saves*,
     * `:320-326`) — so an [ActivityResult] here would be a constant `RESULT_OK` wrapped around the
     * only thing that varies. The host lambda reads
     * `setResult(RESULT_OK, NavResultIntents.forManageLabels(result)); finish()`.
     *
     * The eight callers that read this back — six outside the bookmark cluster and two in
     * `BookmarksComposeActivity` — all do the same two steps: check `RESULT_OK`, then
     * `ManageLabelsData.fromJSON(result.data?.getStringExtra("data")!!)`. `NavResultIntentsTest`
     * pins it by performing exactly that decode.
     */
    fun forManageLabels(result: ManageLabelsResult): Intent =
        Intent().putExtra("data", result.data)

    /**
     * The bookmark LIST's exit: classic `BookmarksComposeActivity.onSelectBookmark`
     * (`BookmarksComposeActivity.kt:172-186`), itself a mirror of classic `Bookmarks.bookmarkSelected`
     * (`Bookmarks.kt:295-323`). Same keys, same order, same [ActivityResultKind.EXTRA] tag; the
     * reader, `MainBibleActivity.kt:2930-2957`, is not touched by this move.
     *
     * Returns a bare [Intent] and not an [ActivityResult] like [forLabelEdit], for
     * [forManageLabels]' reason and one more of its own: this path has exactly one result code
     * (`RESULT_OK`) because it only exists when a row was actually picked — classic's list had no
     * cancel result at all, its Back press just `finish()`ed with the default `RESULT_CANCELED` and
     * no Intent, which is not something a [BookmarkResult] can even express.
     *
     * **The returned object's IDENTITY is load-bearing**, which is why this is a function that
     * returns one Intent rather than something a caller might call twice. Classic passes the SAME
     * instance to `historyTraversal.historyManager.addHistoryItem(null, intent)` and then to
     * `setResult` (`:188-189`), and `HistoryManager.createHistoryItem` (`:153-155`) keeps the object
     * it is handed inside an `IntentHistoryItem`. Two structurally-equal Intents would behave
     * identically today, but the stored one carries NO component — `IntentHistoryItem.revertTo`
     * (`:55-63`) replays extras — so anything that later mutated the result Intent on its way out
     * would have to mutate the stored one too, exactly as it does now. See the `bookmarkResults`
     * channel in `NavHostComposeActivity`, which is the one caller.
     *
     * The two row shapes mirror classic's `when (bookmark)`: a Bible bookmark carries `"verse"`
     * alone, a generic one carries `"key"`/`"book"`/`"ordinal"` and no `"verse"` — and the ABSENCE
     * is load-bearing, because `MainBibleActivity.kt:2945` tests `"verse"` first.
     */
    fun forBookmarks(result: BookmarkResult): Intent {
        val intent = Intent()
            .putExtra(ActivityResultKind.EXTRA, ActivityResultKind.Bookmarks.name)
        if (result.verse != null) {
            intent.putExtra("verse", result.verse)
        } else {
            intent.putExtra("key", result.key)
            // Nullable exactly as classic's `bookmark.book?.initials` is: a general-book bookmark
            // whose book is gone puts a null extra rather than omitting the key, and the dispatcher's
            // `keyStr != null && bookStr != null` guard is what handles it.
            intent.putExtra("book", result.book)
            intent.putExtra("ordinal", result.ordinal ?: 0)
        }
        // A String extra, NOT a CharSequence one: `HistoryManager.createHistoryItem` reads it with
        // `getStringExtra`, which returns null for a CharSequence extra and would title the history
        // row "-". Classic passed the Activity's `title` (a `CharSequence`) and got away with it
        // only because it was in fact a String.
        intent.putExtra("description", result.description)
        intent.putExtra(BookmarkControl.LABEL_NO_EXTRA, result.labelNo)
        intent.putExtra("listPosition", result.listPosition)
        return intent
    }

    /**
     * The pages-within-a-document editor's exit -- `MyDocumentPages`, the batch's second DUAL-ENTRY
     * destination (`ManageLabels` was the first). `MainBibleActivity.kt:2902-2913` reads back
     * exactly `documentInitials`/`pageKey`, tagged with [ActivityResultKind.MyDocumentPages] so the
     * dispatcher's `when` can pick this branch out.
     *
     * Returns an [ActivityResult], like [forLabelEdit] and for the same reason: the result CODE and
     * the Intent are one decision here, not two independent ones. Classic's shapes:
     * - [MyDocumentPagesResult.Selected] is classic `returnWithPage`'s `resultIntent.putExtra(...)` +
     *   `finishOk()` (`MyDocumentPagesComposeActivity.kt:224-226`) -- `RESULT_OK` with both keys.
     * - [MyDocumentPagesResult.Saved] is the Save button's plain `finishOk()` (`:128`) with NEITHER
     *   key set -- `MainBibleActivity`'s reader guards on `bookInitials != null && pageKey != null`
     *   and no-ops otherwise, so an intentionally key-less `RESULT_OK` is inert there, exactly like
     *   [Selected] would be if either key were missing.
     * - [MyDocumentPagesResult.Cancelled] is the Dismiss button's `finishCanceled()` (`:129`, `:344`):
     *   `RESULT_CANCELED`, but -- unlike [forLabelEdit]'s `Cancelled`, which carries no Intent at
     *   all -- classic still attaches its tagged `resultIntent` on this path. The tag ends up inert
     *   either way: `MainBibleActivity`'s dispatcher early-returns on `RESULT_CANCELED` before it
     *   ever reads the kind (`:2870-2876`). Preserved rather than dropped anyway, to stay byte-for-
     *   byte with what classic actually built.
     */
    fun forMyDocumentPages(result: MyDocumentPagesResult): ActivityResult {
        val intent = Intent().putExtra(ActivityResultKind.EXTRA, ActivityResultKind.MyDocumentPages.name)
        return when (result) {
            is MyDocumentPagesResult.Selected -> {
                intent.putExtra("documentInitials", result.documentInitials)
                intent.putExtra("pageKey", result.pageKey)
                ActivityResult(Activity.RESULT_OK, intent)
            }
            MyDocumentPagesResult.Saved -> ActivityResult(Activity.RESULT_OK, intent)
            MyDocumentPagesResult.Cancelled -> ActivityResult(Activity.RESULT_CANCELED, intent)
        }
    }

    /**
     * The My-Documents LIST's exit -- nav-graph slice 4, Task 6, and this batch's ONLY caller that
     * is also its last direct `ScreenLauncher.targetFor` caller (see that task's own report). Same
     * three-shape pattern as [forMyDocumentPages], which [MyDocumentsResult] deliberately mirrors,
     * and read back by the SAME `MainBibleActivity.kt:2914-2929` branch that already handles
     * [ActivityResultKind.MyDocuments] -- untouched by this move.
     * - [MyDocumentsResult.Selected] is classic's relay of the pages child's pick
     *   (`MyDocumentsComposeActivity.kt:222-233`) -- `RESULT_OK` with both keys.
     * - [MyDocumentsResult.Saved] is the Save button's plain `finishOk()` (`:126`) with NEITHER key
     *   set, same as [MyDocumentPagesResult.Saved].
     * - [MyDocumentsResult.Cancelled] is the Dismiss button's `finishCanceled()` (`:127`):
     *   `RESULT_CANCELED`, with the tagged Intent still attached, same shape as
     *   [MyDocumentPagesResult.Cancelled].
     *
     * Classic's `"changed"` extra (`:172`) is deliberately NOT carried -- see [MyDocumentsResult]'s
     * own kdoc: nothing reads it for this kind, the tree's only `getBoolean("changed")` being the
     * unrelated WORKSPACE_CHANGED branch (plan D8).
     */
    fun forMyDocuments(result: MyDocumentsResult): ActivityResult {
        val intent = Intent().putExtra(ActivityResultKind.EXTRA, ActivityResultKind.MyDocuments.name)
        return when (result) {
            is MyDocumentsResult.Selected -> {
                intent.putExtra("documentInitials", result.documentInitials)
                intent.putExtra("pageKey", result.pageKey)
                ActivityResult(Activity.RESULT_OK, intent)
            }
            MyDocumentsResult.Saved -> ActivityResult(Activity.RESULT_OK, intent)
            MyDocumentsResult.Cancelled -> ActivityResult(Activity.RESULT_CANCELED, intent)
        }
    }

    // ——— slice 8 B1: the four channels the reading view collects since the callers moved in-graph ———
    // Each packs exactly the Intent the classic chooser Activity set, so the one reader of that shape
    // (KeyChooserResults, ReadingCommands.applyChosen*) serves both entries.

    /**
     * `ChooseGeneralBookKeyComposeActivity.buildResult` (`bookAndKey` alone, or `key`+`book`),
     * `ChooseMapKeyComposeActivity.buildResult` and `ChooseDictionaryWordComposeActivity`'s
     * `onSelect` (`key`+`book`), all tagged [ActivityResultKind.GenBookKey].
     */
    fun forKeyChooser(result: KeyChooserResult): Intent = Intent().apply {
        if (result.bookAndKeyJson != null) {
            putExtra("bookAndKey", result.bookAndKeyJson)
        } else {
            putExtra("key", result.key)
            putExtra("book", result.book)
        }
        putExtra(ActivityResultKind.EXTRA, ActivityResultKind.GenBookKey.name)
    }

    /** `GridChoosePassageComposeActivity.finishWithVerse`. */
    fun forPassage(result: PassageResult): Intent = Intent()
        .putExtra("verse", result.verse)
        .putExtra(ActivityResultKind.EXTRA, ActivityResultKind.PassageGrid.name)

    /** `ChooseDocumentComposeActivity.handleDocumentSelection`. */
    fun forDocument(result: DocumentResult): Intent = Intent()
        .putExtra("book", result.book)
        .putExtra(ActivityResultKind.EXTRA, ActivityResultKind.ChooseDocument.name)

    /** `WorkspaceSelectorComposeActivity`'s `onResult`: `workspaceId` only when one was picked, always `changed`. */
    fun forWorkspace(result: WorkspaceResult): Intent = Intent().apply {
        if (result.workspaceId != null) putExtra("workspaceId", result.workspaceId)
        putExtra("changed", result.changed)
    }
}

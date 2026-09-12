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
import net.bible.android.control.progress.ReadingProgressServiceImpl
import net.bible.android.view.activity.page.ActivityResultKind
import net.bible.sharedcore.nav.LabelEditResult
import net.bible.sharedcore.nav.ManageLabelsResult
import net.bible.sharedcore.nav.ReadingProgressResult

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
 * declaration instead (`NavHostComposeActivity`'s `bookmarkResults`, the last one still waiting),
 * so that the packing and the test that pins it arrive together, in the task that builds the
 * destination.
 *
 * [readingProgressService] is a fresh, stateless instance (as
 * `ReadingProgressServiceImplTest` builds its own) rather than the host's Koin-injected one:
 * `osisIdForChapter` reads no instance state — it re-resolves the KJVA versification on every call
 * — so this object needs no DI wiring to reuse the exact same conversion the host used.
 */
object NavResultIntents {
    private val readingProgressService = ReadingProgressServiceImpl()

    /**
     * `MainBibleActivity.kt:2930-2957` reads exactly these extras — same keys, same order, same
     * [ActivityResultKind.EXTRA] tag — and is not touched by this move.
     */
    fun forReadingProgress(result: ReadingProgressResult): Intent = when (result) {
        is ReadingProgressResult.Chapter ->
            Intent()
                .putExtra("verse", readingProgressService.osisIdForChapter(result.bookId, result.chapter))
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
}

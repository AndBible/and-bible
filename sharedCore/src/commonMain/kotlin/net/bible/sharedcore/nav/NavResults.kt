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

package net.bible.sharedcore.nav

/**
 * What the reading-progress destination hands back: either a chapter to open or an ordinal range to
 * memorize. Mirrors the two `Intent`s `NavHostComposeActivity.finishWithChapterResult` /
 * `finishWithMemorizeResult` used to build by hand, and `NavResultIntents.forReadingProgress` is now
 * the only place those `Intent`s are built.
 *
 * [Chapter]'s field names are the old host function's own parameter names (`bookId`/`chapter`).
 * [Memorize]'s are `startOrdinal`/`endOrdinal`, NOT the old host function's own `start`/`end`
 * (fix round 1): three of four naming sources agree on "ordinal" — the `"startOrdinal"`/
 * `"endOrdinal"` extras keys themselves, the KJVA-ordinal semantics the values carry, and
 * [net.bible.sharedcore.progress.ReadingProgressController]'s own
 * `onNavigateToMemorize(startOrdinal, endOrdinal)` — and only the (now-deleted) private host
 * function's parameter list said otherwise. This is new `commonMain` API iOS will consume, so the
 * unit belongs in the name.
 */
sealed interface ReadingProgressResult {
    data class Chapter(val bookId: String, val chapter: Int) : ReadingProgressResult

    data class Memorize(val startOrdinal: Int, val endOrdinal: Int) : ReadingProgressResult
}

/**
 * What the Bookmarks list hands back on a row selection. Mirrors the one result [android.content.Intent]
 * `BookmarksComposeActivity.onSelectBookmark` builds (`BookmarksComposeActivity.kt:165-194`), which
 * itself mirrors classic `Bookmarks.bookmarkSelected` (`Bookmarks.kt:295-323`).
 *
 * The row is one of two shapes and the extras reflect that: a `BibleBookmarkWithNotes` sets only
 * [verse]; a `GenericBookmarkWithNotes` sets [key]/[book]/[ordinal] instead and leaves [verse] null.
 * [description], [labelNo] and [listPosition] are always present. This is a flat mirror of the
 * Intent's extras, not a sealed hierarchy, because that Intent itself carries one optional field per
 * shape rather than a tagged union.
 */
data class BookmarkResult(
    val verse: String? = null,
    val key: String? = null,
    val book: String? = null,
    val ordinal: Int? = null,
    val description: String,
    val labelNo: Int,
    val listPosition: Int,
)

/**
 * What `ManageLabelsComposeActivity` hands back on every exit: `saveAndExit`
 * (`ManageLabelsComposeActivity.kt:573-644`) and the HIDELABELS reset path
 * (`ManageLabelsComposeActivity.kt:663-668`) both build the exact same
 * `Intent().putExtra("data", data.toJSON())` — no second extra.
 *
 * [data] is the whole `ManageLabelsData` JSON string (`ManageLabelsContract.kt:47-59`), because that
 * contract type cannot cross into this module (see the file note below). It deliberately carries no
 * separate `reset` field: `reset` is a FIELD of `ManageLabelsData` itself
 * (`ManageLabelsContract.kt:60`), set by `ManageLabelsMapper.applyReset(data)` before `:667` builds
 * this exact result, and it already travels inside [data]. A second field here would duplicate state
 * the JSON already carries and could disagree with it.
 */
data class ManageLabelsResult(val data: String)

/**
 * What `LabelEditComposeActivity` hands back. Mirrors `onFinish` (`LabelEditComposeActivity.kt:240-254`):
 * [Saved] covers both its `Save` and `Delete` arms, which both end at `finishWithData` and so build the
 * identical `Intent().putExtra("data", updated.toJSON())` (`LabelData`, `LabelEditContract.kt:31`) —
 * a completed delete is just a saved [LabelEditContract.LabelData] with its own `delete` flag set.
 * [Cancelled] mirrors the `Cancel` arm, which sets `RESULT_CANCELED` and no `data` extra at all.
 *
 * [Cancelled] is a VARIANT, not a boolean alongside a nullable [Saved.data] (plan D1): there is no
 * payload to carry on cancellation, so a variant with none is the honest shape.
 */
sealed interface LabelEditResult {
    data class Saved(val data: String) : LabelEditResult

    data object Cancelled : LabelEditResult
}

// No `@Serializable` anywhere in this file, deliberately. A result is packed into an Intent's
// EXTRAS by `NavResultIntents`, never encoded as JSON, and the two `:app` contract types slice 2
// carries (`ManageLabelsData`, `LabelData`) embed Room entities (`BookmarkEntities.Label`,
// `WorkspaceEntities.WorkspaceLabelOverride`) and cannot move into this module at all. A payload
// therefore carries their JSON as an opaque `String` and this module never decodes it. Sharing the
// Room entities themselves is a real question, but it belongs to the iOS-compatibility design (can
// Room run there, or does the app bundle its own SQLite), not to a migration batch.

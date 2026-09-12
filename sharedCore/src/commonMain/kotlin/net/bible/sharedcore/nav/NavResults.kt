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

// No `@Serializable` anywhere in this file, deliberately. A result is packed into an Intent's
// EXTRAS by `NavResultIntents`, never encoded as JSON, and the two `:app` contract types slice 2
// carries (`ManageLabelsData`, `LabelData`) embed Room entities (`BookmarkEntities.Label`,
// `WorkspaceEntities.WorkspaceLabelOverride`) and cannot move into this module at all. A payload
// therefore carries their JSON as an opaque `String` and this module never decodes it. Sharing the
// Room entities themselves is a real question, but it belongs to the iOS-compatibility design (can
// Room run there, or does the app bundle its own SQLite), not to a migration batch.

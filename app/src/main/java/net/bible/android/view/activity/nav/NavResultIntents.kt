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

import android.content.Intent
import net.bible.android.control.progress.ReadingProgressServiceImpl
import net.bible.android.view.activity.page.ActivityResultKind
import net.bible.sharedcore.nav.ReadingProgressResult

/**
 * The one place a [ReadingProgressResult] is packed into an `Intent`, byte-identical to what
 * `NavHostComposeActivity.finishWithChapterResult` / `finishWithMemorizeResult` built by hand
 * before `NavResultChannel` existed — same keys, same order, same [ActivityResultKind.EXTRA] tag.
 * `MainBibleActivity.kt:2930-2957` reads exactly these extras and is not touched by this move.
 *
 * [readingProgressService] is a fresh, stateless instance (as
 * `ReadingProgressServiceImplTest` builds its own) rather than the host's Koin-injected one:
 * `osisIdForChapter` reads no instance state — it re-resolves the KJVA versification on every call
 * — so this object needs no DI wiring to reuse the exact same conversion the host used.
 */
object NavResultIntents {
    private val readingProgressService = ReadingProgressServiceImpl()

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
}

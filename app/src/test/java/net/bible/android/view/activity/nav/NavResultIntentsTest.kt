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

import kotlin.test.assertEquals
import kotlin.test.assertNull
import net.bible.android.TEST_SDK
import net.bible.android.view.activity.page.ActivityResultKind
import net.bible.sharedcore.nav.ReadingProgressResult
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Fix round 1, Finding 3: split out of `net.bible.android.view.nav.NavResultChannelGuardTest` so
 * that class's structural ratchet stays dependency-free -- these two tests are the only reason it
 * needed Robolectric, since they exercise `android.content.Intent`, and that class's own text-walk
 * tests touch no Android type.
 *
 * `@RunWith(RobolectricTestRunner::class)`: `android.content.Intent`'s `putExtra`/`getStringExtra`
 * are no-ops under the plain `isReturnDefaultValues` unit-test jar and need Robolectric's shadow to
 * actually round-trip a value (matching `ReadingProgressServiceImplTest`'s reason for the same
 * runner). `@Config(application = android.app.Application::class)` mirrors `DocumentRowMarkersTest`:
 * a plain `Application`, not `DebugApp`/`BibleApplication`, since nothing here needs the real app's
 * heavy `onCreate`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class NavResultIntentsTest {

    /**
     * Proves the WIRING (the branch itself is unit-tested on `NavResultChannel`): that
     * [NavResultIntents.forReadingProgress] still produces what
     * `NavHostComposeActivity.finishWithChapterResult` produced by hand before this batch -- the
     * same [ActivityResultKind.EXTRA] tag and the same `"verse"` extra, and -- fix round 1, Finding
     * 4 -- NOT the `"action"` extra, which `MainBibleActivity.kt:2933` reads BEFORE `"verse"` to
     * pick the memorize branch, so its absence here is as load-bearing as the values that ARE
     * present.
     */
    @Test
    fun forReadingProgressChapterCarriesTheSameExtrasTheOldFunctionDid() {
        val intent = NavResultIntents.forReadingProgress(ReadingProgressResult.Chapter("GEN", 1))

        assertEquals(ActivityResultKind.ReadingProgress.name, intent.getStringExtra(ActivityResultKind.EXTRA))
        assertEquals("Gen.1.1", intent.getStringExtra("verse"))
        assertNull(intent.getStringExtra("action"))
    }

    /** The memorize half of the same edge -- classic `navigateToMemorize` (`:200-208`), verbatim. */
    @Test
    fun forReadingProgressMemorizeCarriesTheSameExtrasTheOldFunctionDid() {
        val intent = NavResultIntents.forReadingProgress(
            ReadingProgressResult.Memorize(startOrdinal = 3, endOrdinal = 7),
        )

        assertEquals(ActivityResultKind.ReadingProgress.name, intent.getStringExtra(ActivityResultKind.EXTRA))
        assertEquals("memorize", intent.getStringExtra("action"))
        assertEquals(3, intent.getIntExtra("startOrdinal", -1))
        assertEquals(7, intent.getIntExtra("endOrdinal", -1))
    }
}

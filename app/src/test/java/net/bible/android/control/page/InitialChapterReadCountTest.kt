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

package net.bible.android.control.page

import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.progress.ProgressControl
import net.bible.android.database.bookmarks.KJVA
import net.bible.test.DatabaseResetter.resetDatabase
import org.crosswire.jsword.versification.BibleBook
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * F128: BibleView records and filters reads by `Math.max(1, chapterNumber)` (`BibleDocument.vue`), so the count
 * sent with a document that starts at the book intro (chapter 0, e.g. FinRK `Gen.0-Gen.1`) must be chapter 1's.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class InitialChapterReadCountTest {
    @After fun tearDown() = resetDatabase()

    @Test fun aDocumentStartingAtTheIntroGetsChapterOnesCount() {
        ProgressControl.recordChapterRead(KJVA, BibleBook.GEN, 1)
        ProgressControl.recordChapterRead(KJVA, BibleBook.GEN, 1)
        assertEquals(2, initialChapterReadCount(KJVA, BibleBook.GEN, 0))
    }

    @Test fun aNormalChapterKeepsItsOwnCount() {
        ProgressControl.recordChapterRead(KJVA, BibleBook.GEN, 1)
        ProgressControl.recordChapterRead(KJVA, BibleBook.GEN, 3)
        assertEquals(1, initialChapterReadCount(KJVA, BibleBook.GEN, 3))
        assertEquals(0, initialChapterReadCount(KJVA, BibleBook.GEN, 2))
    }
}

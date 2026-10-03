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

package net.bible.android.misc

import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.service.sword.SwordContentFacade
import net.bible.test.DatabaseResetter
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.passage.VerseRangeFactory
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Ticket 3868: [OsisFragment.toHashMap]'s isNewTestament flag used to compare a verse's absolute
 * ordinal in the versification against BibleBook.MATT's enum ordinal (~42) - a unit mismatch that
 * made nearly every Old Testament verse register as New Testament, since verse ordinals run into
 * the thousands. This broke Strong's number links (H/G prefix) for MyBible modules in the OT.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class OsisFragmentIsNewTestamentTest {

    @After
    fun finishComponentTesting() {
        DatabaseResetter.resetDatabase()
    }

    private fun isNewTestament(verseStr: String): Boolean {
        val kjv = Books.installed().getBook("KJV") as SwordBook
        val key = VerseRangeFactory.fromString(kjv.versification, verseStr)
        val xml = SwordContentFacade.readOsisFragment(kjv, key)
        val fragment = OsisFragment(xml, key, kjv)
        return fragment.toHashMap["isNewTestament"] == "true"
    }

    @Test
    fun earlyOldTestamentVerseIsNotNewTestament() =
        assertThat(isNewTestament("Gen.1.1"), equalTo(false))

    @Test
    fun lateOldTestamentVerseIsNotNewTestament() =
        // This verse's absolute ordinal is well past BibleBook.MATT.ordinal (~42), which is
        // exactly the unit mismatch that caused the bug.
        assertThat(isNewTestament("Mal.4.6"), equalTo(false))

    @Test
    fun newTestamentVerseIsNewTestament() =
        assertThat(isNewTestament("Matt.1.1"), equalTo(true))
}

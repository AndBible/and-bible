/*
 * Copyright (c) 2022-2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.versification.system.Versifications
import org.crosswire.jsword.versification.BibleBook
import org.junit.Before
import kotlin.Throws
import org.hamcrest.CoreMatchers
import org.junit.After
import org.junit.Assert
import org.junit.Test
import java.lang.Exception

class CurrentBibleVerseTest {
    private var currentBibleVerse: CurrentBibleVerse? = null
    private val synodalV11n = Versifications.instance().getVersification("Synodal")
    private val kjvV11n = Versifications.instance().getVersification("KJV")
    private val synodalPs9v22 = Verse(synodalV11n, BibleBook.PS, 9, 22)
    private val kjvPs10v1 = Verse(kjvV11n, BibleBook.PS, 10, 1)
    @Before
    @Throws(Exception::class)
    fun setUp() {
        currentBibleVerse = CurrentBibleVerse()
    }

    @After
    @Throws(Exception::class)
    fun tearDown() {
    }

    @Test
    @Throws(Exception::class)
    fun testGetCurrentBibleBookNo() {
        currentBibleVerse!!.setVerseSelected(kjvV11n, kjvPs10v1)
        Assert.assertThat(
            currentBibleVerse!!.currentBibleBookNo,
            CoreMatchers.equalTo(BibleBook.PS.ordinal)
        )
    }

    @Test
    @Throws(Exception::class)
    fun testGetCurrentBibleBook() {
        currentBibleVerse!!.setVerseSelected(kjvV11n, kjvPs10v1)
        Assert.assertThat(currentBibleVerse!!.currentBibleBook, CoreMatchers.equalTo(BibleBook.PS))
        currentBibleVerse!!.setVerseSelected(synodalV11n, kjvPs10v1)
        Assert.assertThat(currentBibleVerse!!.currentBibleBook, CoreMatchers.equalTo(BibleBook.PS))
    }

    @Test
    @Throws(Exception::class)
    fun testGetVerseSelected() {
        currentBibleVerse!!.setVerseSelected(kjvV11n, kjvPs10v1)
        Assert.assertThat(
            currentBibleVerse!!.getVerseSelected(synodalV11n),
            CoreMatchers.equalTo(synodalPs9v22)
        )
        Assert.assertThat(
            currentBibleVerse!!.getVerseSelected(kjvV11n),
            CoreMatchers.equalTo(kjvPs10v1)
        )
        currentBibleVerse!!.setVerseSelected(synodalV11n, kjvPs10v1)
        Assert.assertThat(
            currentBibleVerse!!.getVerseSelected(synodalV11n),
            CoreMatchers.equalTo(synodalPs9v22)
        )
        Assert.assertThat(
            currentBibleVerse!!.getVerseSelected(kjvV11n),
            CoreMatchers.equalTo(kjvPs10v1)
        )
    }
}

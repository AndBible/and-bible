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
package net.bible.service.readingplan

import net.bible.android.control.versification.TestData
import org.junit.Before
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.junit.Test

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class PassageReaderTest {
    private var passageReader: PassageReader? = null
    @Before
    fun setup() {
        passageReader = PassageReader(TestData.KJV)
    }

    /**
     * various names were use for Song of Songs - check which is correct.
     */
    @Test
    fun testSongOfSongsChapter() {
        val key = passageReader!!.getKey("Song.8")
        MatcherAssert.assertThat(key.cardinality, Matchers.greaterThan(10))
    }

    @Test
    fun testSongOfSongsChapters() {
        val key = passageReader!!.getKey("Song.1-Song.3")
        MatcherAssert.assertThat(key.cardinality, Matchers.greaterThan(30))
    }

    @Test
    fun testSongOfSongsBook() {
        val key = passageReader!!.getKey("Song")
        MatcherAssert.assertThat(key.cardinality, Matchers.greaterThan(100))
    }
}

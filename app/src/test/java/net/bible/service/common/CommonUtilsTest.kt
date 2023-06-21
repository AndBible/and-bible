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
package net.bible.service.common

import net.bible.service.common.CommonUtils.getKeyDescription
import org.crosswire.jsword.versification.system.Versifications
import org.crosswire.jsword.versification.BibleBook
import org.crosswire.jsword.passage.Verse
import org.hamcrest.CoreMatchers
import org.hamcrest.MatcherAssert
import org.hamcrest.core.IsEqual
import org.junit.Assert
import org.junit.Test

class CommonUtilsTest {
    @Test
    fun testGetVerseDescription() {
        val kjv = Versifications.instance().getVersification("KJV")
        val gen1_0 = Verse(kjv, BibleBook.GEN, 1, 0)
        Assert.assertThat(getKeyDescription(gen1_0), CoreMatchers.equalTo("Genesis 1"))
        val gen1_1 = Verse(kjv, BibleBook.GEN, 1, 1)
        Assert.assertThat(getKeyDescription(gen1_1), CoreMatchers.equalTo("Genesis 1:1"))
        val gen1_10 = Verse(kjv, BibleBook.GEN, 1, 10)
        Assert.assertThat(getKeyDescription(gen1_10), CoreMatchers.equalTo("Genesis 1:10"))
    }

    @Test
    fun tryCatchFinallyIsRunAlways() {
        var v = 0
        try {
            try {
                throw Exception()
            } catch (e: Exception) {
                throw e
            } finally {
                v = 1
            }
        } catch (e: Exception) {

        }
        MatcherAssert.assertThat(v, IsEqual.equalTo(1))
    }
}

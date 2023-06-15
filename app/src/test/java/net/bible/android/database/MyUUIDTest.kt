/*
 * Copyright (c) 2023 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.android.database


import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.core.IsEqual
import org.junit.Test
import java.util.Random

val r = Random(1L)

class MyUUIDTest {
    @Test
    fun digits() {
        val d = MyUUID.digits(20, 1)

    }
    @Test
    fun fromStringToString1() {
        val myUUID = MyUUID.randomUUID()
        val comp = MyUUID.fromString(myUUID.toString()).toString()
        assertThat(myUUID.toString(), IsEqual.equalTo(comp))
    }

    @Test
    fun fromStringToString2() {
        val l1 = r.nextLong()
        val l2 = r.nextLong()
        val myUUID = MyUUID(l1, l2)
        val comp = MyUUID.fromString(myUUID.toString())
        assertThat(comp.mostSignificantBits, IsEqual.equalTo(l1))
        assertThat(comp.leastSignificantBits, IsEqual.equalTo(l1))
    }
}

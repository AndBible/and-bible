/*
 * Copyright (c) 2023-2026 Martin Denham, Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.service.cloudsync

import kotlin.test.Test
import kotlin.test.assertEquals

class SyncNamespaceTest {
    private val shared = "net.bible.android.activity"

    @Test
    fun releaseIdsMapToShared() {
        assertEquals(4, SyncNamespace.RELEASE_IDS.size)
        SyncNamespace.RELEASE_IDS.forEach { id ->
            assertEquals(shared, SyncNamespace.forAppId(id), "release id $id")
        }
    }

    @Test
    fun suffixIsKept() {
        assertEquals("net.bible.android.activity.debug", SyncNamespace.forAppId("net.bible.android.activity.debug"))
        assertEquals("net.bible.android.activity.debug", SyncNamespace.forAppId("com.app.calculator.debug"))
        assertEquals("net.bible.android.activity.dev", SyncNamespace.forAppId("org.andbible.ios.dev"))
        assertEquals("net.bible.android.activity.next", SyncNamespace.forAppId("org.andbible.andbible.next"))
    }

    @Test
    fun unknownIdIsItself() {
        assertEquals("org.example.other", SyncNamespace.forAppId("org.example.other"))
        assertEquals("", SyncNamespace.forAppId(""))
    }

    @Test
    fun prefixWithoutDotIsNotASuffix() {
        assertEquals("com.app.calculatorx", SyncNamespace.forAppId("com.app.calculatorx"))
    }
}

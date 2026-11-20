/*
 * Copyright (c) 2026 Martin Denham, Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.shareddata

import net.bible.android.database.IdType
import net.bible.android.database.MyUUID
import net.bible.android.database.bookmarks.ADDON_ID_TAG_OFFSET
import net.bible.android.database.bookmarks.isAddonId
import net.bible.android.database.bookmarks.withAddonIdTag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * New ids are RFC 4122 version 4 UUIDs, so their version nibble (byte 6) can never equal the add-on id tag's
 * (`ADD08C01A011` over bytes 4..9 puts 0x8C in byte 6): a new user id is never mistaken for an add-on row.
 */
class IdTypeRandomTest {
    @Test
    fun newIdsAreVersion4WithTheRfcVariant() {
        repeat(2000) {
            val bytes = IdType().toByteArray()!!
            assertEquals(0x40, bytes[6].toInt() and 0xF0, "version nibble")
            assertEquals(0x80, bytes[8].toInt() and 0xC0, "variant bits")
            assertEquals(4, MyUUID.randomUUID().toString()[14].digitToInt())
        }
    }

    @Test
    fun aNewIdCannotCarryTheAddonTagEvenWhenItsOtherBytesMatch() {
        // The tag's byte 6 is not a version-4 byte: an id whose other bytes match the tag is still not an add-on id
        // once the version is set.
        val tagged = withAddonIdTag(ByteArray(16))
        assertTrue(IdType.fromByteArray(tagged)!!.isAddonId)
        assertEquals(0x80, tagged[ADDON_ID_TAG_OFFSET + 2].toInt() and 0xF0)
        repeat(2000) { assertFalse(IdType().isAddonId) }
    }
}

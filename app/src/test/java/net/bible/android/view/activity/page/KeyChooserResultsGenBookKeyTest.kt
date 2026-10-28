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

package net.bible.android.view.activity.page

import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.view.activity.page.KeyChooserResults.ChosenKey
import org.crosswire.jsword.book.Books
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * #3591: choosing a Strong's dictionary entry crashed the result delivery with
 * `NoSuchKeyException: No entry for '00001' in InvStrongsRealHebrew`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class KeyChooserResultsGenBookKeyTest {
    private val dictionary get() = Books.installed().getBook("StrongsHebrew")!!

    private fun chosen(osisRef: String?) =
        ChosenKey.GenBookKey(bookAndKeyJson = null, bookInitials = "StrongsHebrew", osisRef = osisRef)

    @Test fun aKeyTheDictionaryDoesNotHaveResolvesToNoKeyInsteadOfThrowing() {
        val (book, key) = KeyChooserResults.resolveGenBookKey(chosen("00001-not-an-entry"))

        assertEquals(dictionary, book)
        assertNull(key)
    }

    /** What the dictionary chooser lists is what it hands back, and the book must resolve it. */
    @Test fun everyKeyTheChooserListsResolvesBackToItself() {
        val listed = dictionary.getGlobalKeyList().filter { it.getName().isNotEmpty() }.take(200)
        assertTrue(listed.isNotEmpty())

        for (listedKey in listed) {
            val (_, key) = KeyChooserResults.resolveGenBookKey(chosen(listedKey.getOsisRef()))
            assertNotNull("'${listedKey.getOsisRef()}' listed but not resolvable", key)
            assertEquals(listedKey.getName(), key!!.getName())
        }
    }
}

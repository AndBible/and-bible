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

import net.bible.android.view.activity.page.KeyChooserResults.ChosenKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Reading-host re-typing T8b step 0, one case per arm of `CurrentGeneralBookPage.startKeyChooser`.
 *
 * What was broken was not the applying but the READING: three arms handed their answer back through
 * `startActivityForResult(…, STD_REQUEST_CODE)`, whose dispatcher lives only on
 * `MainBibleActivity.onActivityResult`, so on every other `ActivityBase` these extras were never
 * looked at at all. This pins that each of the three result shapes is now recognised and named, on
 * any host. `ReadingHostLauncherGuardTest.everyGeneralBookKeyChooserArmAwaitsItsOwnResult` pins the
 * other half — that the arms actually route their awaited result in here.
 */
class KeyChooserResultsTest {

    private fun read(vararg extras: Pair<String, String>): ChosenKey? {
        val map = extras.toMap()
        return KeyChooserResults.chosenKeyFrom(map[ActivityResultKind.EXTRA]) { map[it] }
    }

    @Test fun theMultiDocumentArmsChooseDocumentResultNamesTheChosenDocument() {
        assertEquals(
            ChosenKey.Document("ESV2011"),
            read(ActivityResultKind.EXTRA to ActivityResultKind.ChooseDocument.name, "book" to "ESV2011"),
        )
    }

    @Test fun theMyDocumentArmsResultNamesTheDocumentAndThePage() {
        assertEquals(
            ChosenKey.MyDocumentPage(documentInitials = "MyDoc", pageKey = "page-3"),
            read(
                ActivityResultKind.EXTRA to ActivityResultKind.MyDocumentPages.name,
                "documentInitials" to "MyDoc",
                "pageKey" to "page-3",
            ),
        )
    }

    /** `MyDocumentPagesResult.Saved` is RESULT_OK with neither key: claimed, but opens nothing. */
    @Test fun aMyDocumentPagesSaveNamesNoPageButIsStillThisChoosersAnswer() {
        assertEquals(
            ChosenKey.Nothing,
            read(ActivityResultKind.EXTRA to ActivityResultKind.MyDocumentPages.name),
        )
    }

    @Test fun theGeneralBookArmsResultCarriesTheBookAndTheOsisRef() {
        assertEquals(
            ChosenKey.GenBookKey(bookAndKeyJson = null, bookInitials = "Pilgrim", osisRef = "Pilgrim.1"),
            read(
                ActivityResultKind.EXTRA to ActivityResultKind.GenBookKey.name,
                "book" to "Pilgrim",
                "key" to "Pilgrim.1",
            ),
        )
    }

    /** An EPUB table-of-contents entry comes back as a serialised `BookAndKey` carrying its OWN
     *  document, which is not the page's current document — the reason the book is read explicitly. */
    @Test fun theGeneralBookArmAlsoUnderstandsASerialisedBookAndKey() {
        assertEquals(
            ChosenKey.GenBookKey(bookAndKeyJson = """{"a":1}""", bookInitials = null, osisRef = null),
            read(
                ActivityResultKind.EXTRA to ActivityResultKind.GenBookKey.name,
                "bookAndKey" to """{"a":1}""",
            ),
        )
    }

    @Test fun aResultOfSomeOtherKindIsNotClaimed() {
        assertNull(read(ActivityResultKind.EXTRA to ActivityResultKind.ReadingProgress.name, "verse" to "Gen.1.1"))
    }

    @Test fun aResultWithNoKindAtAllIsNotClaimed() {
        assertNull(read("book" to "ESV2011"))
    }
}

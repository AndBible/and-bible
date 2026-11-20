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

package net.bible.sharedui.platform.widget

import kotlin.test.Test
import kotlin.test.assertEquals

class WidgetUrlTest {
    private val john = WidgetUrlAction.OpenPassage("John 3:16", "KJV", "KJVA", 12)

    @Test fun openUrlEqualsTheEquivalentReadLink() {
        val read = "https://read.andbible.org/John%203:16?document=KJV&v11n=KJVA&ordinal=12"
        assertEquals(john, WidgetUrl.parseReadLink(read))
        val widget = "andbible-widget://open?link=" + "https%3A%2F%2Fread.andbible.org%2FJohn%25203%3A16%3Fdocument%3DKJV%26v11n%3DKJVA%26ordinal%3D12"
        assertEquals(WidgetUrl.parseReadLink(read), WidgetUrl.parse(widget))
    }

    @Test fun openLinkWithoutOptionalParameters() {
        assertEquals(
            WidgetUrlAction.OpenPassage("Gen.1.1", null, null, null),
            WidgetUrl.parse("andbible-widget://open?link=https://read.andbible.org/Gen.1.1"),
        )
    }

    @Test fun nonAndBibleLinkIsInvalid() {
        assertEquals(WidgetUrlAction.Invalid, WidgetUrl.parse("andbible-widget://open?link=https://evil.example/John"))
        assertEquals(WidgetUrlAction.Invalid, WidgetUrl.parse("andbible-widget://open"))
        assertEquals(WidgetUrlAction.Invalid, WidgetUrl.parse("andbible-widget://open?link=https://read.andbible.org/"))
    }

    @Test fun everySpeakCommandRoundTrips() {
        for (c in SpeakCommand.entries) {
            assertEquals(WidgetUrlAction.Speak(c), WidgetUrl.parse("andbible-widget://speak/${c.wire}"))
        }
    }

    @Test fun unknownSpeakCommandAndForeignSchemeAreInvalid() {
        assertEquals(WidgetUrlAction.Invalid, WidgetUrl.parse("andbible-widget://speak/explode"))
        assertEquals(WidgetUrlAction.Invalid, WidgetUrl.parse("andbible-widget://speak"))
        assertEquals(WidgetUrlAction.Invalid, WidgetUrl.parse("https://read.andbible.org/John%203:16"))
        assertEquals(WidgetUrlAction.Invalid, WidgetUrl.parse("andbible-widget://nonsense/1"))
    }

    @Test fun bookmarkIdIsDecoded() {
        assertEquals(WidgetUrlAction.SpeakBookmark("a b"), WidgetUrl.parse("andbible-widget://bookmark/a%20b"))
        assertEquals(WidgetUrlAction.Invalid, WidgetUrl.parse("andbible-widget://bookmark/"))
    }

    @Test fun multiByteKeysDecodeAsUtf8() {
        assertEquals(
            WidgetUrlAction.OpenPassage("Joh 3:16 ä", null, null, null),
            WidgetUrl.parseReadLink("https://read.andbible.org/Joh%203:16%20%C3%A4"),
        )
    }
}

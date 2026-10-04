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
package net.bible.sharedcore.reading

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Direct, JSword-free tests of [ShareVersesOptions.buildText]'s pure string assembly, using
 * synthetic [ShareVersesInput]s. The mechanism this ports from JSword-produced text is
 * cross-checked against the real, JSword-backed `getSelectionText` in
 * `SwordContentFacadeShareVersesCharacterizationTest` (`:app`, Task 26 Step 1) — these tests exist
 * so the pure logic has fast, JVM-independent coverage of its own, including branches (an empty
 * selection, three-plus verses) the `:app` characterisation set does not specifically target.
 *
 * `advertiseApp` defaults to `true` (mirrors the classic pref default), so every test below that
 * is not specifically about the advertise line turns it off to keep its expected string readable.
 */
class ShareVersesOptionsTest {
    private val singleVerse = ShareVersesInput(
        verses = listOf(ShareVersesEntry(1, "In the beginning God created the heaven and the earth.")),
        startOffset = 0,
        endOffset = null,
        referenceAbbreviated = "Gen 1:1",
        referenceFull = "Genesis 1:1",
        versionAbbreviation = "KJV",
        notesText = null,
        advertiseText = "Shared from AndBible Bible Study (https://andbible.github.io)",
        hasRange = false,
    )

    private val threeVerses = ShareVersesInput(
        verses = listOf(
            ShareVersesEntry(1, "In the beginning God created the heaven and the earth."),
            ShareVersesEntry(2, "And the earth was without form, and void."),
            ShareVersesEntry(3, "And God said, Let there be light: and there was light."),
        ),
        startOffset = 0,
        endOffset = null,
        referenceAbbreviated = "Gen 1:1-3",
        referenceFull = "Genesis 1:1-3",
        versionAbbreviation = "KJV",
        notesText = "my note",
        advertiseText = "Shared from AndBible Bible Study (https://andbible.github.io)",
        hasRange = true,
    )

    @Test fun emptySelectionBuildsEmptyText() {
        val empty = singleVerse.copy(verses = emptyList())
        assertEquals("", ShareVersesOptions().buildText(empty))
    }

    @Test fun defaultsPutReferenceAtFrontWithVersion() {
        val text = ShareVersesOptions(advertiseApp = false).buildText(singleVerse)
        assertEquals("Gen 1:1 KJV In the beginning God created the heaven and the earth.", text)
    }

    @Test fun referenceAtBackParenthesisesReferenceAndVersion() {
        val text = ShareVersesOptions(showReferenceAtFront = false, advertiseApp = false).buildText(singleVerse)
        assertEquals("In the beginning God created the heaven and the earth. (Gen 1:1, KJV)", text)
    }

    @Test fun noReferenceOmitsItEntirely() {
        val text = ShareVersesOptions(showReference = false, advertiseApp = false).buildText(singleVerse)
        assertEquals("In the beginning God created the heaven and the earth.", text)
    }

    @Test fun unabbreviatedReferenceUsesTheFullName() {
        val text = ShareVersesOptions(abbreviateReference = false, showReferenceAtFront = false, advertiseApp = false)
            .buildText(singleVerse)
        assertEquals("In the beginning God created the heaven and the earth. (Genesis 1:1, KJV)", text)
    }

    @Test fun noVersionDropsItFromTheReferenceLine() {
        val text = ShareVersesOptions(showVersion = false, showReferenceAtFront = false, advertiseApp = false)
            .buildText(singleVerse)
        assertEquals("In the beginning God created the heaven and the earth. (Gen 1:1)", text)
    }

    @Test fun quotesWrapTheVerseTextOnly() {
        val text = ShareVersesOptions(showQuotes = true, advertiseApp = false).buildText(singleVerse)
        assertEquals("Gen 1:1 KJV “In the beginning God created the heaven and the earth.”", text)
    }

    @Test fun advertiseAppendsALocalisedLine() {
        val text = ShareVersesOptions(advertiseApp = true, showReferenceAtFront = false).buildText(singleVerse)
        assertEquals(
            "In the beginning God created the heaven and the earth. (Gen 1:1, KJV)" +
                "\n\nShared from AndBible Bible Study (https://andbible.github.io)",
            text,
        )
    }

    /** `showReferenceAtFront=true`'s reference line already carries the range, so the FIRST verse
     *  of a multi-verse selection is not itself numbered (only verse 2 and verse 3 are) — the
     *  `!showReferenceAtFront || separateVersesWithNewlines` condition `buildText` guards
     *  `startVerseNumber` with. */
    @Test fun referenceAtFrontSuppressesTheFirstVerseNumber() {
        val text = ShareVersesOptions(showReferenceAtFront = true, advertiseApp = false, showNotes = false)
            .buildText(threeVerses)
        assertEquals(
            "Gen 1:1-3 KJV In the beginning God created the heaven and the earth. " +
                "2. And the earth was without form, and void. " +
                "3. And God said, Let there be light: and there was light.",
            text,
        )
    }

    /** With the reference at the BACK, there is no leading range to lean on, so the first verse
     *  gets numbered too — the same guard as above, the other way round. */
    @Test fun referenceAtBackNumbersEveryVerseIncludingTheFirst() {
        val text = ShareVersesOptions(showReferenceAtFront = false, advertiseApp = false, showNotes = false)
            .buildText(threeVerses)
        assertEquals(
            "1. In the beginning God created the heaven and the earth. " +
                "2. And the earth was without form, and void. " +
                "3. And God said, Let there be light: and there was light. (Gen 1:1-3, KJV)",
            text,
        )
    }

    @Test fun separateVersesWithNewlinesPutsEachVerseOnItsOwnLineAndNumbersTheFirstToo() {
        val text = ShareVersesOptions(
            showReferenceAtFront = true,
            separateVersesWithNewlines = true,
            advertiseApp = false,
            showNotes = false,
        ).buildText(threeVerses)
        assertEquals(
            "Gen 1:1-3 KJV\n\n" +
                "1. In the beginning God created the heaven and the earth.\n\n" +
                "2. And the earth was without form, and void.\n\n" +
                "3. And God said, Let there be light: and there was light.",
            text,
        )
    }

    @Test fun notesAreAppendedOnlyWhenShowNotesIsOnAndThereAreNotes() {
        val withNotes = ShareVersesOptions(showReferenceAtFront = false, advertiseApp = false).buildText(threeVerses)
        assertTrue(withNotes.endsWith("\n\nmy note"))

        val withoutNotesToggle = ShareVersesOptions(showNotes = false, showReferenceAtFront = false, advertiseApp = false)
            .buildText(threeVerses)
        assertTrue(!withoutNotesToggle.contains("my note"))

        val noNotesAtAll = ShareVersesOptions(showReferenceAtFront = false, advertiseApp = false).buildText(singleVerse)
        assertTrue(!noNotesAtAll.contains("my note"))
    }

    @Test fun selectionOnlyOffIncludesTheWholeVerseAroundTheSelection() {
        val partial = singleVerse.copy(startOffset = 3, endOffset = 12, hasRange = true)
        val selectionOnly = ShareVersesOptions().buildText(partial)
        val wholeVerse = ShareVersesOptions(showSelectionOnly = false).buildText(partial)
        assertTrue(selectionOnly.length < wholeVerse.length, "selection-only text must be the shorter one")
        assertTrue(
            wholeVerse.contains("In the beginning God created the heaven and the earth."),
            "the whole verse must be present when selection-only is off",
        )
    }

    @Test fun ellipsisMarksATruncatedSelection() {
        val partial = singleVerse.copy(startOffset = 3, endOffset = 12, hasRange = true)
        val withEllipsis = ShareVersesOptions(showEllipsis = true).buildText(partial)
        val withoutEllipsis = ShareVersesOptions(showEllipsis = false).buildText(partial)
        assertTrue(withEllipsis.contains("..."))
        assertTrue(!withoutEllipsis.contains("..."))
    }
}

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

package net.bible.android.control.page

import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.passage.VerseRange
import org.crosswire.jsword.versification.BibleBook
import org.crosswire.jsword.versification.BookName
import org.crosswire.jsword.versification.system.Versifications
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression test for the process-global [BookName] flag leak fixed alongside F6 Task 10:
 * [VerseRange.abbreviated] used to flip [BookName.setFullBookName] to false, compute the name,
 * then flip it back -- all on the happy path with no try/finally. When the name computation
 * threw, the restore never ran and the flag stayed false for the rest of the JVM (every later
 * book name anywhere in the app/test run then rendered short, e.g. "Genesis 1" -> "Gen 1").
 *
 * There is no natural, JSword-API-only way to make [VerseRange.getName] throw (Verse
 * construction validates book/chapter/verse up front, so an "invalid" VerseRange can't be built
 * through public constructors). To pin the actual contract -- "the global flag is restored even
 * when the body throws" -- this test corrupts a real, validly-constructed VerseRange via
 * reflection (nulling its private `end` field) so that [VerseRange.abbreviated]'s own
 * `doGetName()` throws a genuine NullPointerException from real JSword code, then asserts the
 * flag was restored anyway. This is deliberately NOT a happy-path test: before the fix it fails
 * because the flag is left at `false` after the throw.
 */
class ClientPageObjectsTest {

    @After
    fun restoreDefault() {
        // Leave the process-global flag in its default state for any other test in this JVM.
        BookName.setFullBookName(true)
    }

    @Test
    fun `abbreviated restores the FullBookName flag even when name computation throws`() {
        val kjv = Versifications.instance().getVersification("KJV")
        val verseRange = VerseRange(kjv, Verse(kjv, BibleBook.GEN, 1, 1), Verse(kjv, BibleBook.GEN, 1, 3))

        // Corrupt the (otherwise validly-constructed) VerseRange so that computing its name
        // throws a genuine NPE inside VerseRange.doGetName() -- exercising the real exception
        // path of the production `abbreviated` getter, not a synthetic substitute.
        val endField = VerseRange::class.java.getDeclaredField("end")
        endField.isAccessible = true
        endField.set(verseRange, null)

        // Pin a known starting value for the global flag -- this IS the state under test.
        BookName.setFullBookName(true)

        var threw = false
        try {
            verseRange.abbreviated
        } catch (e: NullPointerException) {
            threw = true
        }

        assertTrue("expected VerseRange.abbreviated to actually throw for this corrupted range " +
            "(otherwise this test isn't exercising the throwing path it claims to)", threw)
        assertTrue(
            "BookName.isFullBookName() should have been restored to true after the throw, " +
                "but the process-global flag leaked (was left false) -- this is the exact " +
                "defect that made every later book name in the JVM render short",
            BookName.isFullBookName()
        )
    }
}

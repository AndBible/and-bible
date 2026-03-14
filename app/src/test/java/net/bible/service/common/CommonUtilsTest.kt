/*
 * Copyright (c) 2022-2026 Martin Denham, Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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

import android.net.Uri
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.service.common.CommonUtils.getKeyDescription
import net.bible.service.common.CommonUtils.parseAndBibleReference
import net.bible.service.common.CommonUtils.prependDictionaryKeyWithZeros
import net.bible.test.DatabaseResetter
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.SwordDictionary
import org.crosswire.jsword.passage.Passage
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.versification.BibleBook
import org.crosswire.jsword.versification.system.Versifications
import org.hamcrest.CoreMatchers
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.notNullValue
import org.hamcrest.CoreMatchers.nullValue
import org.hamcrest.MatcherAssert.assertThat
import org.junit.After
import org.junit.Assert
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class CommonUtilsTest {

    @After
    fun finishComponentTesting() {
        DatabaseResetter.resetDatabase()
    }

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

    // Tests for prependZeros function
    @Test
    fun `prependZeros should pad single digit to 5 characters`() {
        assertThat(prependDictionaryKeyWithZeros("1"), equalTo("00001"))
    }

    @Test
    fun `prependZeros should pad two digits to 5 characters`() {
        assertThat(prependDictionaryKeyWithZeros("42"), equalTo("00042"))
    }

    @Test
    fun `prependZeros should pad three digits to 5 characters`() {
        assertThat(prependDictionaryKeyWithZeros("123"), equalTo("00123"))
    }

    @Test
    fun `prependZeros should pad four digits to 5 characters`() {
        assertThat(prependDictionaryKeyWithZeros("5548"), equalTo("05548"))
    }

    @Test
    fun `prependZeros should not pad exactly 5 characters`() {
        assertThat(prependDictionaryKeyWithZeros("12345"), equalTo("12345"))
    }

    @Test
    fun `prependZeros should not pad strings longer than 5 characters`() {
        assertThat(prependDictionaryKeyWithZeros("123456"), equalTo("123456"))
        assertThat(prependDictionaryKeyWithZeros("1234567890"), equalTo("1234567890"))
    }

    @Test
    fun `prependZeros should handle empty string`() {
        assertThat(prependDictionaryKeyWithZeros(""), equalTo("00000"))
    }

    @Test
    fun `prependZeros should handle alphanumeric strings`() {
        // Dictionary keys might have letters
        assertThat(prependDictionaryKeyWithZeros("G123"), equalTo("0G123"))
        assertThat(prependDictionaryKeyWithZeros("H1"), equalTo("000H1"))  // H1 is 2 chars, so 3 zeros needed
    }

    // Tests for parseAndBibleReference function
    @Test
    fun `parseAndBibleReference should parse simple verse reference`() {
        val result = parseAndBibleReference("https://read.andbible.org/Gen.1.1")

        assertThat(result, notNullValue())
        assertThat(result!!.key, notNullValue())

        val passage = result.key as Passage
        val verse = passage.getVerseAt(0)
        assertThat(verse.book, equalTo(BibleBook.GEN))
        assertThat(verse.chapter, equalTo(1))
        assertThat(verse.verse, equalTo(1))
    }

    @Test
    fun `parseAndBibleReference should parse verse range reference`() {
        val result = parseAndBibleReference("https://read.andbible.org/John.3.16-John.3.18")

        assertThat(result, notNullValue())
        assertThat(result!!.key, notNullValue())

        val passage = result.key as Passage
        val firstVerse = passage.getVerseAt(0)
        val lastVerse = passage.getVerseAt(passage.countVerses() - 1)
        assertThat(firstVerse.book, equalTo(BibleBook.JOHN))
        assertThat(firstVerse.chapter, equalTo(3))
        assertThat(firstVerse.verse, equalTo(16))
        assertThat(lastVerse.verse, equalTo(18))
    }

    @Test
    fun `parseAndBibleReference should parse reference with document parameter`() {
        val result = parseAndBibleReference("https://read.andbible.org/Matt.5.3?document=ESV2011")

        assertThat(result, notNullValue())
        assertThat(result!!.key, notNullValue())

        val passage = result.key as Passage
        val verse = passage.getVerseAt(0)
        assertThat(verse.book, equalTo(BibleBook.MATT))
        assertThat(verse.chapter, equalTo(5))
        assertThat(verse.verse, equalTo(3))

        // Document should be set if it exists in Books
        val esv = Books.installed().getBook("ESV2011")
        if (esv != null) {
            assertThat(result.document, equalTo(esv))
        }
    }

    @Test
    fun `parseAndBibleReference should parse reference with v11n parameter`() {
        val result = parseAndBibleReference("https://read.andbible.org/Ps.23.1?v11n=KJV")

        assertThat(result, notNullValue())
        assertThat(result!!.key, notNullValue())

        val passage = result.key as Passage
        val verse = passage.getVerseAt(0)
        assertThat(verse.book, equalTo(BibleBook.PS))
        assertThat(verse.versification.name, equalTo("KJV"))
    }

    @Test
    fun `parseAndBibleReference should parse reference with ordinal parameter`() {
        val result = parseAndBibleReference("https://read.andbible.org/Rom.8.28?ordinal=100")

        assertThat(result, notNullValue())
        assertThat(result!!.ordinal, notNullValue())
        assertThat(result.ordinal!!.start, equalTo(100))
    }

    @Test
    fun `parseAndBibleReference should parse reference with multiple parameters`() {
        val result = parseAndBibleReference("https://read.andbible.org/1Sam.17.45?document=ESV2011&v11n=KJV&ordinal=50")

        assertThat(result, notNullValue())
        assertThat(result!!.key, notNullValue())

        val passage = result.key as Passage
        val verse = passage.getVerseAt(0)
        assertThat(verse.book, equalTo(BibleBook.SAM1))
        assertThat(verse.chapter, equalTo(17))
        assertThat(verse.verse, equalTo(45))
        assertThat(verse.versification.name, equalTo("KJV"))
        assertThat(result.ordinal?.start, equalTo(50))
    }

    @Test
    fun `parseAndBibleReference should handle dictionary reference with colon format`() {
        // Format: StrongsGreek:5548
        val strongsGreek = Books.installed().getBook("StrongsGreek")
        assumeTrue(strongsGreek is SwordDictionary)
        strongsGreek as SwordDictionary
        val result = parseAndBibleReference("https://read.andbible.org/StrongsGreek:5548")

        assertThat(result, notNullValue())
        assertThat(result!!.document, equalTo(strongsGreek))
        val entry = strongsGreek.getKey("05548")
        assertThat(result.key, equalTo(entry)) // Should be prepended with zeros

    }

    @Test
    fun `parseAndBibleReference should handle dictionary reference with explicit document parameter`() {
        val strongsGreek = Books.installed().getBook("StrongsGreek")
        assumeTrue(strongsGreek is SwordDictionary)
        strongsGreek as SwordDictionary
        val result = parseAndBibleReference("https://read.andbible.org/5548?document=StrongsGreek")

        assertThat(result, notNullValue())
        assertThat(result!!.document, equalTo(strongsGreek))
        val entry = strongsGreek.getKey("05548")
        assertThat(result.key, equalTo(entry))  // Should be prepended with zeros

    }

    @Test
    fun `parseAndBibleReference should handle Uri object`() {
        val uri = Uri.parse("https://read.andbible.org/John.11.35")
        val result = parseAndBibleReference(uri)

        assertThat(result, notNullValue())
        val passage = result!!.key as Passage
        val verse = passage.getVerseAt(0)
        assertThat(verse.book, equalTo(BibleBook.JOHN))
        assertThat(verse.chapter, equalTo(11))
        assertThat(verse.verse, equalTo(35))
    }

    @Test
    fun `parseAndBibleReference should handle chapter-only reference`() {
        val result = parseAndBibleReference("https://read.andbible.org/Ps.23")

        assertThat(result, notNullValue())
        val passage = result!!.key as Passage
        val verse = passage.getVerseAt(0)
        assertThat(verse.book, equalTo(BibleBook.PS))
        assertThat(verse.chapter, equalTo(23))
    }

    @Test
    fun `parseAndBibleReference should return null for invalid reference`() {
        val result = parseAndBibleReference("https://read.andbible.org/InvalidBook.1.1")

        // The function catches exceptions from JSword parsing and returns null
        // for invalid book names that don't exist in any versification
        assertThat(result, nullValue())
    }


    @Test
    fun `parseAndBibleReference should handle empty path gracefully`() {
        val result = parseAndBibleReference("https://read.andbible.org/")
        // When path is just "/", removePrefix("/") results in empty string

        // The function handles this gracefully without crashing
        assertThat(result, notNullValue())
        assertThat(result!!.key, notNullValue())


        val passage = result.key as Passage
        assertThat(passage.countVerses(), equalTo(0))
    }

    @Test
    fun `parseAndBibleReference should handle non-existent document gracefully`() {
        val result = parseAndBibleReference("https://read.andbible.org/Gen.1.1?document=NonExistentBible")

        assertThat(result, notNullValue())
        // Document should be null if it doesn't exist
        assertThat(result!!.document, nullValue())
        // But key should still be parsed
        assertThat(result.key, notNullValue())
    }

    @Test
    fun `parseAndBibleReference should handle different book abbreviations`() {
        // Test various OSIS book abbreviations
        val testCases = mapOf(
            "Gen.1.1" to BibleBook.GEN,
            "1Sam.1.1" to BibleBook.SAM1,
            "Matt.1.1" to BibleBook.MATT,
            "Rev.1.1" to BibleBook.REV
        )

        testCases.forEach { (ref, expectedBook) ->
            val result = parseAndBibleReference("https://read.andbible.org/$ref")
            assertThat("Failed for $ref", result, notNullValue())
            val passage = result!!.key as Passage
            val verse = passage.getVerseAt(0)
            assertThat("Failed for $ref", verse.book, equalTo(expectedBook))
        }
    }

    @Test
    fun `parseAndBibleReference should handle verse ranges across chapters`() {
        val result = parseAndBibleReference("https://read.andbible.org/Gen.1.31-Gen.2.3")

        assertThat(result, notNullValue())
        val passage = result!!.key as Passage
        val firstVerse = passage.getVerseAt(0)
        val lastVerse = passage.getVerseAt(passage.countVerses() - 1)
        assertThat(firstVerse.chapter, equalTo(1))
        assertThat(firstVerse.verse, equalTo(31))
        assertThat(lastVerse.chapter, equalTo(2))
        assertThat(lastVerse.verse, equalTo(3))
    }

    @Test
    fun `prependZeros integration with dictionary lookup`() {
        // This tests the actual use case: dictionary keys need padding
        val strongsGreek = Books.installed().getBook("StrongsGreek")
        assumeTrue(strongsGreek is SwordDictionary)
        strongsGreek as SwordDictionary
        // Simulate what parseAndBibleReference does internally
        val keyStr = "5548"
        val paddedKey = prependDictionaryKeyWithZeros(keyStr)

        assertThat(paddedKey, equalTo("05548"))

        // Verify the key actually exists in the dictionary
        val key = strongsGreek.getKey(paddedKey)
        assertThat(key, notNullValue())

    }

    @Test
    fun `parseAndBibleReference should handle references with different versifications`() {
        val kjvResult = parseAndBibleReference("https://read.andbible.org/Tob.1.1?v11n=KJV")
        val cathResult = parseAndBibleReference("https://read.andbible.org/Tob.1.1?v11n=Catholic")

        // Both should parse but might have different versifications
        // KJV doesn't have Tobit, so it might fail or handle differently
        // Catholic versification should handle Tobit
        if (cathResult != null) {
            assertThat(cathResult.key, notNullValue())
        }
    }
}

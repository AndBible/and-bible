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

package net.bible.sharedui.reading.session

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** [parseLink] against the behaviour the old `android.net.Uri` based `BibleView.openLink` relied on. */
class ReadingLinkParserTest {
    private fun parsed(url: String) = assertNotNull(parseLink(url), "expected $url to parse")

    @Test fun repeatedOsisParametersKeepTheirOrder() {
        val link = parsed("multi://?osis=John.3.16&osis=Rom.5.8&v11n=KJV")
        assertEquals("multi", link.scheme)
        assertEquals(listOf("John.3.16", "Rom.5.8"), link.query["osis"])
        assertEquals(listOf("KJV"), link.query["v11n"])
        assertEquals("", link.authority)
    }

    @Test fun parameterNamesKeepFirstOccurrenceOrderAndValuesAreGrouped() {
        val link = parsed("ab-w://?strong=G1&robinson=x&strong=G2")
        assertEquals(listOf("strong", "robinson"), link.query.keys.toList())
        assertEquals(listOf("G1", "G2"), link.query["strong"])
    }

    @Test fun percentTwentyAndPlusBothBecomeSpace() {
        assertEquals(listOf("1 John.3.16"), parsed("osis://?osis=1%20John.3.16").query["osis"])
        assertEquals(listOf("1 John.3.16"), parsed("osis://?osis=1+John.3.16").query["osis"])
        assertEquals(listOf("a+b"), parsed("osis://?osis=a%2Bb").query["osis"])
    }

    @Test fun encodedAmpersandAndEqualsStayInsideTheValue() {
        val link = parsed("osis://?osis=a%26b%3Dc&doc=KJV")
        assertEquals(listOf("a&b=c"), link.query["osis"])
        assertEquals(listOf("KJV"), link.query["doc"])
    }

    @Test fun onlyTheFirstRawEqualsSplitsNameFromValue() {
        assertEquals(listOf("a=b"), parsed("osis://?osis=a=b").query["osis"])
    }

    @Test fun emptyValueAndMissingEqualsAreEmptyStrings() {
        val link = parsed("epub-ref://?book=B&toKey=ch1&toId=&flag")
        assertEquals(listOf(""), link.query["toId"])
        assertEquals(listOf(""), link.query["flag"])
    }

    @Test fun emptyQueryAndNoQueryGiveAnEmptyMap() {
        assertTrue(parsed("ab-error://error?").query.isEmpty())
        assertTrue(parsed("ab-error://error").query.isEmpty())
        assertTrue(parsed("ab-error://").query.isEmpty())
    }

    @Test fun emptyPairsAreSkipped() {
        assertEquals(listOf("a"), parsed("osis://?&&osis=a&").query["osis"])
        assertEquals(setOf("osis"), parsed("osis://?&&osis=a&").query.keys)
    }

    @Test fun fragmentIsNotPartOfTheQuery() {
        val link = parsed("osis://?osis=Gen.1.1#verse")
        assertEquals(listOf("Gen.1.1"), link.query["osis"])
        assertTrue(parsed("osis://#?osis=Gen.1.1").query.isEmpty())
    }

    @Test fun schemeCaseIsPreserved() {
        assertEquals("OSIS", parsed("OSIS://?osis=Gen.1.1").scheme)
    }

    @Test fun linksWithoutASchemeAreNull() {
        assertNull(parseLink("Gen.1.1"))
        assertNull(parseLink(""))
        assertNull(parseLink("://?osis=x"))
        assertNull(parseLink("//host/path"))
        assertNull(parseLink("1abc://x"))
        assertNull(parseLink("a b://x"))
    }

    @Test fun authorityAndPathOfAHierarchicalLink() {
        val link = parsed("morphology://robinson/V-PAI-3S")
        assertEquals("robinson", link.authority)
        assertEquals("/V-PAI-3S", link.path)
    }

    @Test fun authorityIsEmptyWhenTheQueryFollowsTheSlashes() {
        val link = parsed("osis://?osis=Gen.1.1")
        assertEquals("", link.authority)
        assertEquals("", link.path)
    }

    @Test fun strongsAuthorityAndOpaqueForm() {
        assertEquals("G2316", parsed("strongs://G2316").authority)
        val opaque = parsed("strongs:G2316")
        assertNull(opaque.authority)
        assertNull(opaque.path)
        assertEquals("G2316", opaque.schemeSpecificPart)
    }

    @Test fun journalOpaqueFormHasNoQueryInsteadOfThrowing() {
        val link = parsed("osis:?osis=Gen.1.1")
        assertTrue(link.query.isEmpty())
    }

    @Test fun rightToLeftBookNamesDecodeFromPercentEncodingAndPassThroughRaw() {
        val hebrew = "\u05D1\u05E8\u05D0\u05E9\u05D9\u05EA"
        val encoded = "%D7%91%D7%A8%D7%90%D7%A9%D7%99%D7%AA"
        assertEquals(listOf("$hebrew.1.1"), parsed("osis://?osis=$encoded.1.1").query["osis"])
        assertEquals(listOf("$hebrew.1.1"), parsed("osis://?osis=$hebrew.1.1").query["osis"])
        assertEquals(listOf("$hebrew 1:1"), parsed("osis://?osis=$encoded%201:1").query["osis"])
    }

    @Test fun malformedPercentSequencesStayLiteral() {
        assertEquals(listOf("100%"), parsed("osis://?osis=100%").query["osis"])
        assertEquals(listOf("a%zzb"), parsed("osis://?osis=a%zzb").query["osis"])
    }

    @Test fun encodedNameIsDecodedForLookup() {
        assertEquals(listOf("x"), parsed("osis://?o%73is=x").query["osis"])
    }

    @Test fun schemeSpecificPartOfAHierarchicalLinkIsEverythingBeforeTheFragment() {
        assertEquals("//KJV/Matt.5.3", parsed("sword://KJV/Matt.5.3#x").schemeSpecificPart)
    }

    @Test fun booleanParameterFollowsUriGetBooleanQueryParameter() {
        assertEquals(false, parsed("osis://?osis=x").booleanParam("force-doc", false))
        assertEquals(true, parsed("osis://?force-doc=true").booleanParam("force-doc", false))
        assertEquals(true, parsed("osis://?force-doc").booleanParam("force-doc", false))
        assertEquals(false, parsed("osis://?force-doc=FALSE").booleanParam("force-doc", true))
        assertEquals(false, parsed("osis://?force-doc=0").booleanParam("force-doc", true))
        assertEquals(true, parsed("osis://?force-doc=yes").booleanParam("force-doc", false))
    }
}

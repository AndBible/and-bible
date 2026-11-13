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

package net.bible.sharedui.reading.bridge

import org.crosswire.jsword.versification.system.Versifications
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

/** One case per branch of [classifyExternalLink]'s `when`, in its order. */
class ExternalLinkClassifierTest {
    private fun app(link: String): net.bible.android.control.link.BibleLink =
        assertIs<ExternalLinkAction.AppLink>(classifyExternalLink(link)).link

    @Test fun myBibleBibleLink() {
        val l = app("B:10 1:1")
        assertEquals("content", l.type)
        assertEquals("Gen 1:1", l.target)
    }

    @Test fun myBibleBibleLinkWithUnknownBookNumberIsIgnored() {
        assertSame(ExternalLinkAction.Ignored, classifyExternalLink("B:99999 1:1"))
    }

    /** Issue #2747: MyBible 192 is EsthGr in NRSVA (AddEsth would not resolve there), 345 AddDan in Luther. */
    @Test fun myBibleBibleLinkUsesTheSourceDocumentsVersification() {
        val nrsva = Versifications.instance().getVersification("NRSVA")!!
        val esther = assertIs<ExternalLinkAction.AppLink>(classifyExternalLink("B:192 10:3", nrsva)).link
        assertEquals("EsthGr 10:3", esther.target)
        assertSame(nrsva, esther.versification)
        val luther = Versifications.instance().getVersification("Luther")!!
        assertEquals("AddDan 1:1", assertIs<ExternalLinkAction.AppLink>(classifyExternalLink("B:345 1:1", luther)).link.target)
        // Unknown source versification: the plain book map, parsed as before.
        assertEquals("AddEsth 10:3", app("B:192 10:3").target)
    }

    @Test fun myBibleStrongs() {
        val l = app("S:G2316")
        assertEquals("strong", l.type)
        assertEquals("G2316", l.target)
    }

    @Test fun mySwordBibleLink() {
        val l = app("#b1.2.3")
        assertEquals("content", l.type)
        assertEquals("Gen.2.3", l.target)
    }

    @Test fun mySwordBibleLinkWithUnknownBookNumberIsIgnored() {
        assertSame(ExternalLinkAction.Ignored, classifyExternalLink("#b9999.1.1"))
    }

    @Test fun mySwordStrongsBothPrefixes() {
        for (prefix in listOf("#s", "#d")) {
            val l = app("${prefix}H430")
            assertEquals("strong", l.type)
            assertEquals("H430", l.target)
        }
    }

    @Test fun swordAndOsisLinksKeepTheirFullText() {
        for (link in listOf("sword://CalvinCommentaries/Eph.1.11", "osis:Gen.1.1")) {
            val l = app(link)
            assertEquals("sword", l.type)
            assertEquals(link, l.target)
        }
    }

    @Test fun strongsScheme() {
        val l = app("strongs://H430")
        assertEquals("strong", l.type)
        assertEquals("H430", l.target)
    }

    @Test fun morphologySchemeSplitsTypeAndCode() {
        val l = app("morphology://robinson/V-PAI-3S")
        assertEquals("robinson", l.type)
        assertEquals("V-PAI-3S", l.target)
    }

    @Test fun morphologySchemeWithoutCodeHasEmptyTarget() {
        val l = app("morphology://robinson")
        assertEquals("robinson", l.type)
        assertEquals("", l.target)
    }

    @Test fun plainHttpsLinkGoesToTheBrowser() {
        assertEquals(ExternalLinkAction.Browser("https://example.org"), classifyExternalLink("https://example.org"))
    }

    @Test fun unknownSchemeGoesToTheBrowser() {
        assertEquals(ExternalLinkAction.Browser("mailto:a@b.c"), classifyExternalLink("mailto:a@b.c"))
    }
}

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

package net.bible.sharedcore.ui.dialog

import kotlin.test.Test
import kotlin.test.assertEquals

class HtmlRunsTest {

    private fun plain(html: String) = parseHtmlRuns(html).joinToString("") { it.text }

    @Test fun plainTextIsOneRun() =
        assertEquals(listOf(HtmlRun("Download failed")), parseHtmlRuns("Download failed"))

    @Test fun newlinesCollapseLikeHtmlFromHtml() =
        // Dialogs.showErrorMsg passes raw exception text through Html.fromHtml today: \n is a space.
        assertEquals("line one line two", plain("line one\n  line two"))

    @Test fun brBreaksTheLine() =
        assertEquals("a\nb", plain("a<br>b"))

    @Test fun selfClosingBrAndUpperCase() =
        assertEquals("a\nb\nc", plain("a<br/>b<BR>c"))

    @Test fun paragraphsGetABlankLine() =
        assertEquals("one\n\ntwo", plain("<p>one</p><p>two</p>").trim())

    @Test fun boldAndItalicNest() {
        val runs = parseHtmlRuns("<b>bold <i>both</i></b> none")
        assertEquals(HtmlRun("bold ", bold = true), runs[0])
        assertEquals(HtmlRun("both", bold = true, italic = true), runs[1])
        assertEquals(HtmlRun(" none"), runs[2])
    }

    @Test fun linkCarriesItsHref() {
        // CommonUtils.showHelpDialog's shape.
        val runs = parseHtmlRuns("Blurb<br><br><i><a href=\"https://docs.andbible.org/en/latest/ai.html\">Read more</a></i>")
        val link = runs.single { it.href != null }
        assertEquals("Read more", link.text)
        assertEquals("https://docs.andbible.org/en/latest/ai.html", link.href)
        assertEquals(true, link.italic)
    }

    @Test fun mailtoLinkIsKept() {
        val runs = parseHtmlRuns("<a href='mailto:x@example.org'>mail</a>")
        assertEquals("mailto:x@example.org", runs.single().href)
    }

    @Test fun bigAndSmallSizes() {
        // ReadingAppBootstrap's stable notice shape.
        val runs = parseHtmlRuns("<big><a href=\"u\"><b>Video</b></a></big>&nbsp;<small>support</small>")
        assertEquals(HtmlRun("Video", bold = true, size = HtmlRun.Size.Big, href = "u"), runs[0])
        assertEquals(HtmlRun(" "), runs[1])
        assertEquals(HtmlRun("support", size = HtmlRun.Size.Small), runs[2])
    }

    @Test fun entitiesDecode() =
        assertEquals("• a & b <c> \"d\" 'e' é é", plain("&bull;&nbsp;a &amp; b &lt;c&gt; &quot;d&quot; &#39;e&#39; &#233; &#xE9;"))

    @Test fun unknownTagsAreDroppedTextKept() =
        assertEquals("keep this", plain("<font color=\"red\">keep</font> <span>this</span>"))

    @Test fun aBareLessThanIsText() =
        // An exception message such as "expected x < 3" must not lose text.
        assertEquals("expected x < 3", plain("expected x < 3"))

    @Test fun unknownEntityIsLiteral() =
        assertEquals("&nope;", plain("&nope;"))

    @Test fun emptyInputIsNoRuns() =
        assertEquals(emptyList(), parseHtmlRuns(""))

    @Test fun adjacentRunsWithTheSameStyleMerge() =
        assertEquals(listOf(HtmlRun("ab c")), parseHtmlRuns("a<span>b</span> c"))
}

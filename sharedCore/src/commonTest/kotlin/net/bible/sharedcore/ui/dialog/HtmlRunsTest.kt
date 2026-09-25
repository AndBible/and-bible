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

    @Test fun doubleBrMakesABlankLine() =
        // Html.fromHtml(FROM_HTML_MODE_LEGACY) turns every <br> into its own newline.
        assertEquals("a\n\nb", plain("a<br><br>b"))

    @Test fun linkCarriesItsHref() {
        // CommonUtils.showHelpDialog's shape.
        val runs = parseHtmlRuns("Blurb<br><br><i><a href=\"https://docs.andbible.org/en/latest/ai.html\">Read more</a></i>")
        assertEquals("Blurb\n\nRead more", runs.joinToString("") { it.text })
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
        assertEquals(HtmlRun(" "), runs[1])
        assertEquals(HtmlRun("support", size = HtmlRun.Size.Small), runs[2])
    }

    @Test fun entitiesDecode() =
        assertEquals("• a & b <c> \"d\" 'e' é é", plain("&bull;&nbsp;a &amp; b &lt;c&gt; &quot;d&quot; &#39;e&#39; &#233; &#xE9;"))

    @Test fun nbspIsNotCollapsedLikeARegularSpace() =
        // A real non-breaking space (run-1 minor): unlike a decoded ' ', it must survive standing
        // next to another (collapsible) space instead of being swallowed by appendText's collapse.
        assertEquals("a  b", plain("a&nbsp; b"))

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

    // --- I1: comments, lists, headings (+ run-1 minors) ---------------------------------------

    @Test fun htmlCommentIsDropped() =
        assertEquals("ab", plain("a<!-- x > y -->b"))

    @Test fun theLicenseHeaderCommentIsDroppedEntirely() {
        // The exact 16-line header comment R.raw.license opens with (F1's original symptom: it used
        // to be emitted as literal text because TAG never matches `<!--`).
        val comment = """
            |<!--
            |  ~ Copyright (c) 2022-2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
            |  ~
            |  ~ This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
            |  ~
            |  ~ AndBible is free software: you can redistribute it and/or modify it under the
            |  ~ terms of the GNU General Public License as published by the Free Software Foundation,
            |  ~ either version 3 of the License, or (at your option) any later version.
            |  ~
            |  ~ AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
            |  ~ without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
            |  ~ See the GNU General Public License for more details.
            |  ~
            |  ~ You should have received a copy of the GNU General Public License along with AndBible.
            |  ~ If not, see http://www.gnu.org/licenses/.
            |  -->
        """.trimMargin()
        assertEquals("", plain(comment).trim())
    }

    @Test fun anUnterminatedCommentStaysLiteral() =
        assertEquals("before<!-- never closed", plain("before<!-- never closed"))

    @Test fun aDoctypeDeclarationIsDropped() =
        assertEquals("ab", plain("a<!DOCTYPE html>b"))

    @Test fun listItemsAreSeparateBulletedParagraphs() =
        assertEquals("• a) one\n\n• b) two", plain("<ul><li>a) one</li><li>b) two</li></ul>").trim())

    @Test fun orderedListItemsAreSeparateBulletedParagraphsToo() =
        assertEquals("• one\n\n• two", plain("<ol><li>one</li><li>two</li></ol>").trim())

    @Test fun headingIsABoldParagraphOfItsOwn() {
        val runs = parseHtmlRuns("<p>before</p><h3>Title</h3><p>after</p>")
        // The run's own text still carries the trailing block break the closing </h3> emitted
        // (as p/div's already do -- run boundaries aren't newline-trimmed, only the joined text is).
        val heading = runs.single { it.text.trimEnd('\n') == "Title" }
        assertEquals(true, heading.bold)
        assertEquals(HtmlRun.Size.Big, heading.size)
        assertEquals("before\n\nTitle\n\nafter", runs.joinToString("") { it.text }.trim())
    }

    @Test fun blockBreakRightAfterAStyleFlushDoesNotDoubleTheBlankLine() =
        // A heading's closing tag flushes sb (its restyle changes style), so the very next block
        // tag's lineBreak(2) starts from an EMPTY sb -- it must still see the two newlines the
        // flushed run already ends with, not add two MORE on top of them.
        assertEquals("Title\n\nafter", plain("<h3>Title</h3><p>after</p>").trim())

    @Test fun h1ToH3AreBigH4ToH6AreNormalSizeButBothAreBold() {
        assertEquals(HtmlRun.Size.Big, parseHtmlRuns("<h1>a</h1>").single().size)
        assertEquals(HtmlRun.Size.Big, parseHtmlRuns("<h2>a</h2>").single().size)
        assertEquals(HtmlRun.Size.Big, parseHtmlRuns("<h3>a</h3>").single().size)
        for (tag in listOf("h4", "h5", "h6")) {
            val run = parseHtmlRuns("<$tag>a</$tag>").single()
            assertEquals(HtmlRun.Size.Normal, run.size)
            assertEquals(true, run.bold)
        }
    }

    @Test fun brAdjacentToBlockTagsDoesNotDoubleBreak() =
        // Run-1 minor: <br> immediately inside a <p> must not add a THIRD newline on top of the
        // paragraph's own break.
        assertEquals("a\n\nb", plain("<p>a<br></p><p>b</p>").trim())

    // A real excerpt of R.raw.license's opening (comment + <h3> title + the fsf.org link) -- proves
    // the three I1 fixes work together on the actual content, not just isolated snippets.
    @Test fun licenseExcerptRendersHeadingCommentAndLink() {
        val html = """
            |<!--
            |  ~ Copyright (c) 2022-2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
            |  ~
            |  ~ This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
            |  ~
            |  ~ AndBible is free software: you can redistribute it and/or modify it under the
            |  ~ terms of the GNU General Public License as published by the Free Software Foundation,
            |  ~ either version 3 of the License, or (at your option) any later version.
            |  ~
            |  ~ AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
            |  ~ without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
            |  ~ See the GNU General Public License for more details.
            |  ~
            |  ~ You should have received a copy of the GNU General Public License along with AndBible.
            |  ~ If not, see http://www.gnu.org/licenses/.
            |  -->
            |
            |<h3 style="text-align: center;">GNU GENERAL PUBLIC LICENSE</h3>
            |    <p style="text-align: center;">Version 3, 29 June 2007</p>
            |
            |    <p>Copyright &#169; 2007 Free Software Foundation, Inc.
            |        &lt;<a href="https://fsf.org/">https://fsf.org/</a>&gt;</p><p>
            |    Everyone is permitted to copy and distribute verbatim copies
            |    of this license document, but changing it is not allowed.</p>
        """.trimMargin()
        val runs = parseHtmlRuns(html)
        val text = runs.joinToString("") { it.text }
        assertEquals(true, text.trimStart().startsWith("GNU GENERAL PUBLIC LICENSE"))
        assertEquals(false, text.contains("Copyright (c) 2022"))
        assertEquals(false, text.contains("<!--"))
        assertEquals(false, text.contains("-->"))
        val heading = runs.single { it.text.trimEnd('\n') == "GNU GENERAL PUBLIC LICENSE" }
        assertEquals(true, heading.bold)
        assertEquals(HtmlRun.Size.Big, heading.size)
        val link = runs.single { it.href == "https://fsf.org/" }
        assertEquals("https://fsf.org/", link.text)
    }
}

/** [plainTextToHtml] (I2): a plain string posted into a dialog body must keep its line breaks and
 *  any literal `<...>` once [parseHtmlRuns] parses it back. */
class PlainTextToHtmlTest {
    @Test fun escapesAmpersandLessThanAndGreaterThan() =
        assertEquals("a &amp; b &lt;c&gt;", plainTextToHtml("a & b <c>"))

    @Test fun turnsNewlinesIntoBr() =
        assertEquals("one<br><br>two", plainTextToHtml("one\n\ntwo"))

    @Test fun escapesAmpersandBeforeItInsertsAnyOfItsOwnEntities() =
        // Escaping order matters: '&' first, so the '&amp;'/'&lt;'/'&gt;' this function inserts are
        // never themselves re-escaped into '&amp;amp;' etc.
        assertEquals("&amp;lt;", plainTextToHtml("&lt;"))

    @Test fun roundTripsThroughParseHtmlRunsLineByLineWithAngleBracketsSurviving() {
        // I2's exact regression shape: BookmarkControl's CSV-import error summary. If `<row>` had
        // been left unescaped, parseHtmlRuns would treat it as an unknown tag and drop it -- so this
        // equality only holds if plainTextToHtml really escaped it.
        val message = "2 created, 1 error\n\nRecord 2: bad <row> data"
        val html = plainTextToHtml(message)
        val runs = parseHtmlRuns(html)
        assertEquals(message, runs.joinToString("") { it.text })
    }
}

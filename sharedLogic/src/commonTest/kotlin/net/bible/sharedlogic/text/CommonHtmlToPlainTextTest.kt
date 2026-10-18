package net.bible.sharedlogic.text

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Expected strings are literal copies of what Android's `htmlToSpan(html).toString()` returns
 * (`:app` `CommonHtmlToPlainTextOracleTest` checks the same fixtures against the live Android code).
 */
class CommonHtmlToPlainTextTest {
    private fun c(html: String) = CommonHtmlToPlainText.convert(html)

    @Test fun plain() = assertEquals("In the beginning", c("In the beginning"))

    @Test fun boldTagIsStripped() = assertEquals("In the beginning God", c("In the <b>beginning</b> God"))

    @Test fun entitiesAreDecoded() = assertEquals("Tom & Jerry — end", c("Tom &amp; Jerry &#8212; end"))

    @Test fun brBecomesNewline() = assertEquals("line one\nline two", c("line one<br/>line two"))

    @Test fun brRunsAreKept() {
        assertEquals("a\n\n\nb", c("a<br><br><br>b"))
        assertEquals("\nlead", c("<br>lead"))
    }

    @Test fun nestedSpansAndWhitespaceCollapse() =
        assertEquals("a b c d", c("<span>a  <span>b\n\n <i>c</i></span></span>\n d"))

    @Test fun paragraphsAreSeparatedByBlankLines() {
        assertEquals("one\n\ntwo\n\n", c("<p>one</p><p>two</p>"))
        assertEquals("one\n\ntwo\n\n", c("<p>one</p>\n<p>two</p>"))
        assertEquals("outer\n\ninner\n\ntail", c("<p>outer<p>inner</p>tail</p>"))
        assertEquals("text \n\npara\n\ntext", c("text <p>para</p> text"))
    }

    @Test fun blocksAreSeparatedByBlankLinesLikeParagraphs() {
        assertEquals("one\n\ntwo\n\n", c("<div>one</div><div>two</div>"))
        assertEquals("a\n\nb\n\n", c("<ul><li>a</li><li>b</li></ul>"))
        assertEquals("T1\n\nx\n\nT2\n\ny\n\nT3\n\nz", c("<h1>T1</h1>x<h2>T2</h2>y<h3>T3</h3>z"))
        assertEquals("before\n\nquoted\n\nafter", c("before<blockquote>quoted</blockquote>after"))
        assertEquals("a\n\nb\n\nc", c("<div>a<div>b</div></div>c"))
    }

    @Test fun nonBreakingSpaceStaysAndNumericEntitiesDecode() =
        assertEquals("x  y   AB", c("x&nbsp;&nbsp;y &#160; &#x41;&#66;"))

    @Test fun commentsAreDroppedAndStrayAngleBracketIsKept() {
        assertEquals("a b", c("a <!-- hidden --> b"))
        assertEquals("1 < 2 and 3 > 2", c("1 < 2 and 3 > 2"))
    }

    @Test fun leadingWhitespaceDroppedTrailingCollapsed() =
        assertEquals("leading and trailing ", c("  leading and trailing  "))

    /** The note-HTML shape the share path hands to `convert` (a bookmark note: paragraphs of text). */
    @Test fun bookmarkNoteFragment() =
        assertEquals(
            "Personal reflection:\n\nThis verse speaks to God's amazing love.\n\n",
            c("<p>Personal reflection:</p><p>This verse speaks to God&#39;s amazing love.</p>"),
        )

    @Test fun linkWithEntityAndNbspFragment() = assertEquals(
        "See Gen 1:1 — the beginning.\n\n",
        c("<p class=\"note\">See <a href=\"https://example.org/x?a=1&amp;b=2\">Gen\n  1:1</a>&nbsp;&mdash; <i>the beginning</i>.</p>"),
    )
}

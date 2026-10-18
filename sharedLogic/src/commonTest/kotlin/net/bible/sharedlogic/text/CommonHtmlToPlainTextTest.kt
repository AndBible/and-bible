package net.bible.sharedlogic.text

import kotlin.test.Test
import kotlin.test.assertEquals

/** Expected strings follow `Html.fromHtml(.., FROM_HTML_MODE_LEGACY).toString()` for note/verse HTML. */
class CommonHtmlToPlainTextTest {
    private fun c(html: String) = CommonHtmlToPlainText.convert(html)

    @Test fun plain() = assertEquals("In the beginning", c("In the beginning"))

    @Test fun boldTagIsStripped() = assertEquals("In the beginning God", c("In the <b>beginning</b> God"))

    @Test fun entitiesAreDecoded() = assertEquals("Tom & Jerry — end", c("Tom &amp; Jerry &#8212; end"))

    @Test fun brBecomesNewline() = assertEquals("line one\nline two", c("line one<br/>line two"))

    @Test fun nestedSpansAndWhitespaceCollapse() =
        assertEquals("a b c d", c("<span>a  <span>b\n\n <i>c</i></span></span>\n d"))

    @Test fun paragraphsAndBlocksEndWithNewlines() {
        assertEquals("one\n\ntwo\n\n", c("<p>one</p><p>two</p>"))
        assertEquals("one\ntwo\n", c("<div>one</div><div>two</div>"))
    }

    @Test fun scriptAndCommentsAreDropped() = assertEquals("a b", c("a <!-- hidden --><script>x<y</script>b"))

    @Test fun strayAngleBracketIsKept() = assertEquals("1 < 2", c("1 < 2"))

    @Test fun realVerseFragment() = assertEquals(
        "See Gen 1:1 — the beginning.\n\n",
        c("<p class=\"note\">See <a href=\"https://example.org/x?a=1&amp;b=2\">Gen\n  1:1</a>&nbsp;&mdash; <i>the beginning</i>.</p>"),
    )
}

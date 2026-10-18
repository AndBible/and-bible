package net.bible.service.text

import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.service.common.htmlToSpan
import net.bible.sharedlogic.text.CommonHtmlToPlainText
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Oracle: [CommonHtmlToPlainText] must equal Android's `htmlToSpan(html).toString()` (legacy mode). */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class CommonHtmlToPlainTextOracleTest {
    private val fixtures = listOf(
        "In the beginning",
        "In the <b>beginning</b> God",
        "Tom &amp; Jerry &#8212; end",
        "line one<br/>line two",
        "<span>a  <span>b\n\n <i>c</i></span></span>\n d",
        "<p class=\"note\">See <a href=\"https://example.org/x?a=1&amp;b=2\">Gen\n  1:1</a>&nbsp;&mdash; <i>the beginning</i>.</p>",
        "<p>Personal reflection:</p><p>This verse speaks to God&#39;s amazing love.</p>",
        "<div>one</div><div>two</div>",
        "<ul><li>a</li><li>b</li></ul>",
        "<h1>T1</h1>x<h2>T2</h2>y<h3>T3</h3>z",
        "before<blockquote>quoted</blockquote>after",
        "<p>outer<p>inner</p>tail</p>",
        "a<br><br><br>b",
        "<br>lead",
        "x&nbsp;&nbsp;y &#160; &#x41;&#66;",
        "<p>one</p>\n<p>two</p>",
        "text <p>para</p> text",
        "<div>a<div>b</div></div>c",
        "a <!-- hidden --> b",
        "1 < 2 and 3 > 2",
        "  leading and trailing  ",
        "<b>bold</b> <i>it</i>",
    )

    @Test fun matchesAndroidHtmlToSpan() {
        val failures = fixtures.mapNotNull {
            val expected = htmlToSpan(it).toString()
            val actual = CommonHtmlToPlainText.convert(it)
            if (expected == actual) null else "html=${it.replace("\n", "\\n")} expected=${expected.replace("\n", "\\n")} actual=${actual.replace("\n", "\\n")}"
        }
        assertEquals(failures.joinToString("\n"), 0, failures.size)
    }
}

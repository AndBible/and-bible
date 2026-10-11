package net.bible.service.format

import android.text.Html
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class HtmlEntitiesDifferentialTest {
    private val corpus = listOf(
        "plain", "a &amp; b", "&amp;quot;x&amp;quot;", "&quot;q&quot;", "&lt;tag&gt;", "x&nbsp;y", "&#8212;", "&#x2014;",
        "a < b", "1 <2", "trailing &", "&unknown;", "  two  spaces  ", "line\nbreak", "tab\there", "Ἐν ἀρχῇ", "שָׁלוֹם",
        "<b>bold</b>", "<br>", "a&amp;amp;b",
        // Distinct characters() values captured from the two existing handler fixture suites.
        "something", "  ", " ", "Be exalted,", "O God", "inNoteBeforeRef", "inNoteAfterRef",
        "&copy; &apos; &euro;", "a&#32;&#32;b", "a&#10;b", "a\r\nb", "<p>x</p><p>y</p>",
        "a<br>b", "<i>x</i>", "<unknown>x</unknown>", "a <", "x &amp y", "a<!-- hidden -->b",
    )

    @Test fun matchesFrameworkHtml() {
        val differences = corpus.mapNotNull { input ->
            val expected = Html.fromHtml(input, 0).toString()
            println("HTML_ORACLE ${input.toByteArray().let { java.util.Base64.getEncoder().encodeToString(it) }} ${expected.toByteArray().let { java.util.Base64.getEncoder().encodeToString(it) }}")
            val actual = decodeHtmlEntities(input)
            if (expected == actual) null else "input=$input expected=$expected actual=$actual"
        }
        assertEquals(differences.joinToString("\n"), emptyList<String>(), differences)
    }
}

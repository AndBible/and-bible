package net.bible.sharedlogic.markdown

import fi.sykero.kdom.input.KdomBuilder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class HtmlToXhtmlTest {
    private fun assertWellFormed(fragment: String) {
        KdomBuilder(allowDocType = true).build("<root>$fragment</root>") // throws when not well-formed
    }

    @Test fun stripsTheBodyWrapper() {
        assertEquals("<p>a</p>", HtmlToXhtml.toXhtml("<body><p>a</p></body>"))
    }

    @Test fun emptyBodyBecomesEmptyString() {
        assertEquals("", HtmlToXhtml.toXhtml("<body></body>"))
    }

    @Test fun voidElementsAreSelfClosedWhetherOrNotAlreadyClosed() {
        assertEquals("<p>a<br />b</p><hr />", HtmlToXhtml.toXhtml("<p>a<br>b</p><hr>"))
        assertEquals("<p>a<br />b</p><hr />", HtmlToXhtml.toXhtml("<p>a<br />b</p><hr/>"))
        assertEquals("""<img src="x.png" alt="x" />""", HtmlToXhtml.toXhtml("""<img src="x.png" alt="x">"""))
        assertEquals("""<img src="x.png" alt="x" />""", HtmlToXhtml.toXhtml("""<img src="x.png" alt="x"/>"""))
    }

    @Test fun elementsThatMerelyStartWithAVoidNameAreNotTouched() {
        assertEquals("<brand>x</brand>", HtmlToXhtml.toXhtml("<brand>x</brand>"))
    }

    @Test fun xmlBuiltInEntitiesAndNumericReferencesStay() {
        val s = "<p>&amp; &lt; &gt; &quot; &apos; &#169; &#x1F600;</p>"
        assertEquals(s, HtmlToXhtml.toXhtml(s))
    }

    @Test fun knownNamedEntitiesBecomeCharacters() {
        assertEquals("a b — … ä", HtmlToXhtml.toXhtml("a&nbsp;b &mdash; &hellip; &auml;"))
    }

    @Test fun everyHtml4EntityIsResolvedNotJustCommonOnes() {
        // Expected characters written as code points by hand (Latin-1 capital E acute, Greek alpha, ...).
        assertEquals("\u00C9 \u03B1 \u00F1 \u00E7 \u00F8 \u03A9 \u2014 \u00A9 \u2026", HtmlToXhtml.toXhtml("&Eacute; &alpha; &ntilde; &ccedil; &oslash; &Omega; &mdash; &copy; &hellip;"))
    }

    @Test fun unknownNamedEntityIsEscapedSoTheResultStaysWellFormed() {
        val out = HtmlToXhtml.toXhtml("<p>&bogus; &nbsp;</p>")
        assertEquals("<p>&amp;bogus;  </p>", out)
        assertWellFormed(out)
    }

    @Test fun aRealisticFragmentIsWellFormed() {
        val html = "<body><h1>T</h1><p>a<br>b &copy; &foo;</p><ul><li>x</li></ul><hr><img src=\"i.png\"></body>"
        val out = HtmlToXhtml.toXhtml(html)
        assertFalse("<body" in out)
        assertWellFormed(out)
    }
}

package net.bible.sharedlogic.text

import kotlin.test.Test
import kotlin.test.assertEquals

class HtmlEntitiesTest {
    private fun u(s: String) = HtmlEntities.unescapeHtml4(s)

    @Test fun basic() = assertEquals("\"a&b<>", u("&quot;a&amp;b&lt;&gt;"))
    @Test fun latinAndExtended() = assertEquals("é€ ", u("&eacute;&euro;&nbsp;"))
    @Test fun numeric() = assertEquals("AB", u("&#65;&#x42;"))
    @Test fun numericSupplementary() = assertEquals("😀", u("&#128512;"))
    @Test fun semicolonRequired() = assertEquals("&#65", u("&#65"))
    @Test fun unknownLeftAsIs() = assertEquals("&bogus;", u("&bogus;"))
    @Test fun empty() = assertEquals("", u(""))
    @Test fun noDoubleDecode() = assertEquals("&quot;", u("&amp;quot;"))
    @Test fun badNumericLeftAsIs() = assertEquals("&#1a;&#;&#xZ;", u("&#1a;&#;&#xZ;"))
}

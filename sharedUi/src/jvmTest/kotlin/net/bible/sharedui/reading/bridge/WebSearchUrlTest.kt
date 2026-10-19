package net.bible.sharedui.reading.bridge

import kotlin.test.Test
import kotlin.test.assertEquals

class WebSearchUrlTest {
    /**
     * Android sends ACTION_WEB_SEARCH with the raw query and has no URL of its own; the Google search URL is the
     * iOS equivalent of what the default browser does with that intent.
     */
    @Test
    fun webSearchUrlMatchesAndroid() {
        assertEquals("https://www.google.com/search?q=grace", webSearchUrl("grace"))
    }

    @Test
    fun spacesAndReservedCharactersArePercentEncoded() {
        assertEquals("https://www.google.com/search?q=a%20b%26c%3Dd%2Be%23f", webSearchUrl("a b&c=d+e#f"))
    }

    @Test
    fun nonAsciiIsUtf8Encoded() {
        assertEquals("https://www.google.com/search?q=%C3%A4%E2%82%AC", webSearchUrl("ä€"))
    }

    @Test
    fun unreservedCharactersStayLiteral() {
        assertEquals("https://www.google.com/search?q=A-z_0.9~", webSearchUrl("A-z_0.9~"))
    }
}

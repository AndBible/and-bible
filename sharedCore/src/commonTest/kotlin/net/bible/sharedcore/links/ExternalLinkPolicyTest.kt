package net.bible.sharedcore.links

import net.bible.sharedcore.platform.AppSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ExternalLinkPolicyTest {
    private class MapSettings : AppSettings {
        val m = mutableMapOf<String, Any?>()
        override fun getString(key: String, default: String?) = (m[key] as String?) ?: default
        override fun getLong(key: String, default: Long) = (m[key] as Long?) ?: default
        override fun getInt(key: String, default: Int) = (m[key] as Int?) ?: default
        override fun getBoolean(key: String, default: Boolean) = (m[key] as Boolean?) ?: default
        override fun getDouble(key: String, default: Double) = (m[key] as Double?) ?: default
        override fun getFloat(key: String, default: Float) = (m[key] as Float?) ?: default
        override fun setString(key: String, value: String?) { m[key] = value }
        override fun setLong(key: String, value: Long?) { m[key] = value }
        override fun setInt(key: String, value: Int?) { m[key] = value }
        override fun setBoolean(key: String, value: Boolean?) { m[key] = value }
        override fun setDouble(key: String, value: Double?) { m[key] = value }
        override fun setFloat(key: String, value: Float?) { m[key] = value }
    }

    private val yt = "https://www.youtube.com/watch?v=abc"

    @Test fun nothingIsTrustedByDefault() {
        val trust = TrustedLinkDomains(MapSettings())
        assertEquals(emptySet(), trust.domains())
        assertEquals(ExternalLinkDecision.Ask(trustableHost = "www.youtube.com"), trust.decide(yt, askRequested = true, discrete = false))
    }

    @Test fun trustedHostOpensWithoutAsking() {
        val trust = TrustedLinkDomains(MapSettings())
        trust.trust("www.youtube.com")
        assertEquals(ExternalLinkDecision.Open, trust.decide(yt, askRequested = true, discrete = false))
        assertEquals(ExternalLinkDecision.Open, trust.decide("HTTPS://WWW.YouTube.com:443/x", askRequested = true, discrete = false))
    }

    @Test fun matchingIsExactHost() {
        val trust = TrustedLinkDomains(MapSettings())
        trust.trust("www.youtube.com")
        assertEquals(ExternalLinkDecision.Ask("m.youtube.com"), trust.decide("https://m.youtube.com/x", true, false))
        assertEquals(ExternalLinkDecision.Ask("youtube.com"), trust.decide("https://youtube.com/x", true, false))
        // A look-alike host that merely starts with the trusted one is not trusted.
        assertEquals(ExternalLinkDecision.Ask("www.youtube.com.evil.example"),
            trust.decide("https://www.youtube.com.evil.example/x", true, false))
    }

    @Test fun discreteAlwaysAsksWithoutTheOption() {
        val trust = TrustedLinkDomains(MapSettings())
        trust.trust("www.youtube.com")
        assertEquals(ExternalLinkDecision.Ask(trustableHost = null), trust.decide(yt, askRequested = true, discrete = true))
        assertEquals(ExternalLinkDecision.Ask(trustableHost = null), trust.decide("https://other.org", askRequested = false, discrete = true))
    }

    @Test fun removingAHostAsksAgain() {
        val settings = MapSettings()
        val trust = TrustedLinkDomains(settings)
        trust.trust("www.youtube.com")
        trust.trust("andbible.org")
        trust.setDomains(setOf("andbible.org"))
        assertEquals(ExternalLinkDecision.Ask("www.youtube.com"), trust.decide(yt, true, false))
        assertEquals(ExternalLinkDecision.Open, TrustedLinkDomains(settings).decide("https://andbible.org/docs", true, false))
    }

    @Test fun nonWebLinksAskWithoutTheOption() {
        val trust = TrustedLinkDomains(MapSettings())
        assertEquals(ExternalLinkDecision.Ask(null), trust.decide("mailto:a@b.org", true, false))
        assertEquals(ExternalLinkDecision.Ask(null), trust.decide("not a url", true, false))
    }

    @Test fun aCallerThatDoesNotAskStillOpensDirectly() {
        val trust = TrustedLinkDomains(MapSettings())
        assertEquals(ExternalLinkDecision.Open, trust.decide("https://other.org", askRequested = false, discrete = false))
    }

    @Test fun hostOfParsesWebUrlsOnly() {
        assertEquals("www.youtube.com", ExternalLinkPolicy.hostOf("https://user@www.YouTube.com:8080/a?b#c"))
        assertEquals("andbible.org", ExternalLinkPolicy.hostOf("http://andbible.org"))
        assertNull(ExternalLinkPolicy.hostOf("ftp://andbible.org"))
        assertNull(ExternalLinkPolicy.hostOf("https:///nohost"))
        assertNull(ExternalLinkPolicy.hostOf("javascript:alert(1)"))
    }

    /** Review finding: a URL must never parse to a trusted host while a browser (WHATWG parsing) opens another. */
    @Test fun hostOfNeverReportsAHostTheBrowserWouldNotOpen() {
        // Browsers treat "\\" as "/": the real host is evil.com. Such links ask without the option.
        assertNull(ExternalLinkPolicy.hostOf("https://evil.com\\@trusted.com/"))
        // Multiple userinfo separators are ambiguous across native URL parsers and browsers.
        assertNull(ExternalLinkPolicy.hostOf("https://x@trusted.com:@evil.com/"))
        assertEquals("trusted.com", ExternalLinkPolicy.hostOf("https://evil%2F@trusted.com/x"))
        // Whitespace or control characters inside the URL: ask without the option.
        assertNull(ExternalLinkPolicy.hostOf("https://trusted.com\tevil.com/"))
        assertNull(ExternalLinkPolicy.hostOf("https://trusted.com evil.com/"))
        // IPv6 literal: the whole bracketed address, never "[".
        assertEquals("[::1]", ExternalLinkPolicy.hostOf("https://[::1]:8080/"))
        assertNull(ExternalLinkPolicy.hostOf("https://[::1/"))
        // An "@" in the path or query is not userinfo.
        assertEquals("trusted.com", ExternalLinkPolicy.hostOf("https://trusted.com/a@evil.com"))
        assertEquals("trusted.com", ExternalLinkPolicy.hostOf("https://trusted.com?x=@evil.com"))
    }

    @Test fun aTrustedHostDoesNotLetALookAlikeUrlOpenDirectly() {
        val trust = TrustedLinkDomains(MapSettings())
        trust.trust("trusted.com")
        assertEquals(ExternalLinkDecision.Ask(null), trust.decide("https://evil.com\\@trusted.com/", true, false))
        assertEquals(ExternalLinkDecision.Ask(null), trust.decide("https://x@trusted.com:@evil.com/", true, false))
    }

    @Test fun multipleUserinfoSeparatorsNeverOfferOrUseTrust() {
        val trust = TrustedLinkDomains(MapSettings()).apply { trust("c") }
        val url = "https://a@b@c/path"
        assertNull(ExternalLinkPolicy.hostOf(url))
        assertEquals(ExternalLinkDecision.Ask(null), trust.decide(url, true, false))
        assertEquals("c", ExternalLinkPolicy.hostOf("https://a@c/path@b?mail=a@b"))
    }
}

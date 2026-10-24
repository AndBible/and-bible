package net.bible.sharedcore.webdav

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DigestAuthTest {
    private fun param(header: String, name: String): String? =
        Regex("""(?:^Digest |, )$name=("([^"]*)"|[^,]*)""").find(header)?.let { it.groupValues[2].ifEmpty { it.groupValues[1] } }

    @Test fun rfc2617Md5Vector() {
        val challenge = assertNotNull(DigestChallenge.parse(listOf(
            "Digest realm=\"testrealm@host.com\", qop=\"auth,auth-int\", " +
                "nonce=\"dcd98b7102dd2f0e8b11d0f600bfb0c093\", opaque=\"5ccc069c403ebaf9f0171e9517f40e41\""
        )))
        val header = challenge.authorization("Mufasa", "Circle Of Life", "GET", "/dir/index.html", 1, "0a4f113b")
        assertEquals("6629fae49393a05397450978507c4ef1", param(header, "response"))
        assertEquals("auth", param(header, "qop"))
        assertEquals("00000001", param(header, "nc"))
        assertEquals("0a4f113b", param(header, "cnonce"))
        assertEquals("/dir/index.html", param(header, "uri"))
        assertEquals("5ccc069c403ebaf9f0171e9517f40e41", param(header, "opaque"))
        assertEquals("Mufasa", param(header, "username"))
    }

    @Test fun rfc7616Sha256Vector() {
        val challenge = assertNotNull(DigestChallenge.parse(listOf(
            "Digest realm=\"http-auth@example.org\", qop=\"auth, auth-int\", algorithm=SHA-256, " +
                "nonce=\"7ypf/xlj9XXwfDPEoM4URrv/xwf94BcCAzFZH4GiTo0v\", opaque=\"FQhe/qaU925kfnzjCev0ciny7QMkPqMAFRtzCUYo5tdS\""
        )))
        val header = challenge.authorization("Mufasa", "Circle of Life", "GET", "/dir/index.html", 1,
            "f2/wE4q74E6zIJEtWaHKaf5wv/H5QzzpXusqGemxURZJ")
        assertEquals("753927fa0e85d155564e2e272a28d1802ca10daf4496794697cf8db5856cb6c1", param(header, "response"))
        assertEquals("SHA-256", param(header, "algorithm"))
    }

    @Test fun rfc7616Md5Vector() {
        val challenge = assertNotNull(DigestChallenge.parse(listOf(
            "Digest realm=\"http-auth@example.org\", qop=\"auth, auth-int\", algorithm=MD5, " +
                "nonce=\"7ypf/xlj9XXwfDPEoM4URrv/xwf94BcCAzFZH4GiTo0v\", opaque=\"FQhe/qaU925kfnzjCev0ciny7QMkPqMAFRtzCUYo5tdS\""
        )))
        val header = challenge.authorization("Mufasa", "Circle of Life", "GET", "/dir/index.html", 1,
            "f2/wE4q74E6zIJEtWaHKaf5wv/H5QzzpXusqGemxURZJ")
        assertEquals("8ca523f5e9506fed4657c9700eebdbec", param(header, "response"))
    }

    @Test fun challengeWithoutQopUsesTheRfc2069Response() {
        val challenge = assertNotNull(DigestChallenge.parse(listOf(
            "Digest realm=\"testrealm@host.com\", nonce=\"dcd98b7102dd2f0e8b11d0f600bfb0c093\", opaque=\"5ccc069c403ebaf9f0171e9517f40e41\""
        )))
        val header = challenge.authorization("Mufasa", "CircleOfLife", "GET", "/dir/index.html", 1, "unused")
        assertEquals("1949323746fe6a43ef61f9606e7febea", param(header, "response"))
        assertNull(param(header, "qop"))
        assertNull(param(header, "nc"))
        assertNull(param(header, "cnonce"))
    }

    @Test fun digestIsFoundBesideOtherSchemesInOneHeaderAndAcrossHeaders() {
        val inline = assertNotNull(DigestChallenge.parse(listOf("Basic realm=\"a, b\", Digest realm=\"dav\", nonce=\"n1\", qop=auth")))
        assertEquals("n1", param(inline.authorization("u", "p", "GET", "/", 1, "c"), "nonce"))
        val split = assertNotNull(DigestChallenge.parse(listOf("Basic realm=\"x\"", "digest realm=\"dav\", nonce=\"n2\"")))
        assertEquals("n2", param(split.authorization("u", "p", "GET", "/", 1, "c"), "nonce"))
    }

    @Test fun quotedValuesKeepEscapedQuotesAndCommas() {
        val challenge = assertNotNull(DigestChallenge.parse(listOf("Digest realm=\"a \\\"b\\\", c\", nonce=\"n\"")))
        assertTrue(challenge.authorization("u", "p", "GET", "/", 1, "c").contains("realm=\"a \\\"b\\\", c\""))
    }

    @Test fun staleFlagIsRead() {
        assertTrue(DigestChallenge.parse(listOf("Digest realm=\"r\", nonce=\"n\", stale=TRUE"))!!.stale)
        assertFalse(DigestChallenge.parse(listOf("Digest realm=\"r\", nonce=\"n\""))!!.stale)
    }

    @Test fun unusableChallengesAreRejected() {
        assertNull(DigestChallenge.parse(listOf("Basic realm=\"x\"")))
        assertNull(DigestChallenge.parse(listOf("Digest realm=\"r\"")))  // no nonce
        assertNull(DigestChallenge.parse(listOf("Digest realm=\"r\", nonce=\"n\", algorithm=SHA-512-256")))
        assertNull(DigestChallenge.parse(listOf("Digest realm=\"r\", nonce=\"n\", qop=\"auth-int\"")))
        assertNull(DigestChallenge.parse(emptyList()))
    }
}

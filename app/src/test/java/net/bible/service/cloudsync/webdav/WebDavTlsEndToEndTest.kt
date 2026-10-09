package net.bible.service.cloudsync.webdav

import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import net.bible.sharedcore.webdav.DavUntrustedCertificateException
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class WebDavTlsEndToEndTest {
    private val server = MockWebServer()
    private val cert = HeldCertificate.Builder().commonName("localhost").addSubjectAlternativeName("localhost").build()
    private val state = object : WebDavStateStore {
        override var propagation = Propagation.UNKNOWN
        override var certPin: CertPin? = null
    }

    @Before fun start() {
        server.useHttps(HandshakeCertificates.Builder().heldCertificate(cert).build().sslSocketFactory())
        server.start()
    }
    @After fun stop() = server.close()

    private fun config() = WebDavConfig(server.url("/dav/").toString().replace("127.0.0.1", "localhost"), "u", "p", "")
    private fun okMultistatus() = MockResponse.Builder().code(207)
        .body("""<d:multistatus xmlns:d="DAV:"><d:response><d:href>/dav/</d:href><d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response></d:multistatus>""")
        .build()

    @Test fun selfSigned_unpinned_surfacesUntrustedCertificate_notIOException() = runBlocking {
        server.enqueue(okMultistatus())
        try {
            createWebDavClient(config(), state).propfind("", 0); fail()
        } catch (e: DavUntrustedCertificateException) {
            assertEquals(sha256Hex(cert.certificate.encoded), e.certificate.sha256)
        }
    }

    @Test fun selfSigned_pinned_connects() = runBlocking {
        state.certPin = CertPin("localhost", sha256Hex(cert.certificate.encoded))
        server.enqueue(okMultistatus())
        assertEquals(1, createWebDavClient(config(), state).propfind("", 0).size)
    }

    @Test fun pinnedCertReachedByIp_passesHostnameCheck() = runBlocking {
        // cert is issued for "localhost"; reach it by IP. The pin makes both the TrustManager and
        // PinnedHostnameVerifier accept it (the verifier's default check fails on the name).
        val c = WebDavConfig(server.url("/dav/").toString().replace("localhost", "127.0.0.1"), "u", "p", "")
        state.certPin = CertPin("127.0.0.1", sha256Hex(cert.certificate.encoded))
        server.enqueue(okMultistatus())
        assertEquals(1, createWebDavClient(c, state).propfind("", 0).size)
    }

    @Test fun pinnedForOtherHost_byIp_surfacesUntrustedCertificate() = runBlocking {
        val c = WebDavConfig(server.url("/dav/").toString().replace("localhost", "127.0.0.1"), "u", "p", "")
        state.certPin = CertPin("localhost", sha256Hex(cert.certificate.encoded))
        server.enqueue(okMultistatus())
        try { createWebDavClient(c, state).propfind("", 0); fail() }
        catch (e: DavUntrustedCertificateException) { assertEquals("127.0.0.1", e.certificate.host) }
    }
}

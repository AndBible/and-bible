package net.bible.service.cloudsync.webdav

import net.bible.sharedcore.webdav.DavUntrustedCertificateException
import okhttp3.tls.HeldCertificate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.security.Principal
import java.security.cert.Certificate
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLSession
import javax.net.ssl.X509TrustManager

class PinnedTrustTest {
    private val selfSigned = HeldCertificate.Builder().commonName("nas.local").addSubjectAlternativeName("nas.local").build()
    private val chain = arrayOf(selfSigned.certificate)

    /** Platform validation that rejects everything (a self-signed cert) or accepts everything (a CA-issued cert). */
    private fun platform(trusts: Boolean) = object : X509TrustManager {
        override fun checkClientTrusted(c: Array<out X509Certificate>?, a: String?) = Unit
        override fun checkServerTrusted(c: Array<out X509Certificate>?, a: String?) { if (!trusts) throw CertificateException("untrusted") }
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    @Test fun untrustedWithoutPin_throwsWithCertInfo() {
        val tm = PinnedTrustManager("nas.local", { null }, platform(false))
        try { tm.checkServerTrusted(chain, "RSA"); fail() } catch (e: CertificateException) {
            val dav = generateSequence<Throwable>(e) { it.cause }.filterIsInstance<DavUntrustedCertificateException>().single()
            assertEquals("nas.local", dav.certificate.host)
            assertEquals(sha256Hex(selfSigned.certificate.encoded), dav.certificate.sha256)
            assertTrue(dav.certificate.subject.contains("nas.local"))
        }
    }

    @Test fun pinnedLeaf_isAccepted() {
        val pin = CertPin("nas.local", sha256Hex(selfSigned.certificate.encoded))
        PinnedTrustManager("nas.local", { pin }, platform(false)).checkServerTrusted(chain, "RSA")
    }

    @Test fun pinForOtherHost_isRejected() {
        val pin = CertPin("other.host", sha256Hex(selfSigned.certificate.encoded))
        try { PinnedTrustManager("nas.local", { pin }, platform(false)).checkServerTrusted(chain, "RSA"); fail() }
        catch (_: CertificateException) {}
    }

    @Test fun changedCertificate_isRejected() {
        val pin = CertPin("nas.local", sha256Hex(HeldCertificate.Builder().build().certificate.encoded))
        try { PinnedTrustManager("nas.local", { pin }, platform(false)).checkServerTrusted(chain, "RSA"); fail() }
        catch (_: CertificateException) {}
    }

    @Test fun systemTrusted_ignoresPin() {
        val wrongPin = CertPin("nas.local", "00")
        PinnedTrustManager("nas.local", { wrongPin }, platform(true)).checkServerTrusted(chain, "RSA")
    }

    @Test fun hostnameMismatch_withoutPin_isRejectedAndRecorded() {
        val v = PinnedHostnameVerifier("10.0.2.2", { null }, { _, _ -> false })
        assertFalse(v.verify("10.0.2.2", FakeSession(chain)))
        assertEquals(sha256Hex(selfSigned.certificate.encoded), v.lastRejected!!.sha256)
    }

    @Test fun hostnameMismatch_withPin_isAccepted() {
        val pin = CertPin("10.0.2.2", sha256Hex(selfSigned.certificate.encoded))
        val v = PinnedHostnameVerifier("10.0.2.2", { pin }, { _, _ -> false })
        assertTrue(v.verify("10.0.2.2", FakeSession(chain)))
    }
}

/** Minimal session: only [getPeerCertificates] is implemented. */
private class FakeSession(private val chain: Array<X509Certificate>) : SSLSession {
    override fun getPeerCertificates(): Array<Certificate> = chain.map { it as Certificate }.toTypedArray()
    private fun nope(): Nothing = throw UnsupportedOperationException()
    override fun getId(): ByteArray = nope()
    override fun getSessionContext(): javax.net.ssl.SSLSessionContext = nope()
    override fun getCreationTime(): Long = nope()
    override fun getLastAccessedTime(): Long = nope()
    override fun invalidate() = nope()
    override fun isValid(): Boolean = nope()
    override fun putValue(name: String?, value: Any?) = nope()
    override fun getValue(name: String?): Any = nope()
    override fun removeValue(name: String?) = nope()
    override fun getValueNames(): Array<String> = nope()
    override fun getPeerCertificateChain(): Array<javax.security.cert.X509Certificate> = nope()
    override fun getLocalCertificates(): Array<Certificate> = nope()
    override fun getPeerPrincipal(): Principal = nope()
    override fun getLocalPrincipal(): Principal = nope()
    override fun getCipherSuite(): String = nope()
    override fun getProtocol(): String = nope()
    override fun getPeerHost(): String = nope()
    override fun getPeerPort(): Int = nope()
    override fun getPacketBufferSize(): Int = nope()
    override fun getApplicationBufferSize(): Int = nope()
}

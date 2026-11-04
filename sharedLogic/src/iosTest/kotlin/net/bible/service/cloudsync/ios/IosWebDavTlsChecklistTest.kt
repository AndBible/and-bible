package net.bible.service.cloudsync.ios

import kotlinx.coroutines.test.runTest
import net.bible.service.cloudsync.webdav.CertPin
import net.bible.service.cloudsync.webdav.Propagation
import net.bible.service.cloudsync.webdav.WebDavConfig
import net.bible.service.cloudsync.webdav.WebDavStateStore
import net.bible.sharedcore.webdav.DavUntrustedCertificateException
import okio.FileSystem
import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/** Opt-in real NSURLSession trust challenges against the controller's disposable loopback TLS server. */
class IosWebDavTlsChecklistTest {
    @Test
    fun rejectedChallengeThenPinnedCredentialAndWrongPin() = runTest(timeout = 90.seconds) {
        val marker = "/tmp/andbible-checklist-3945-tls-port".toPath()
        if (!FileSystem.SYSTEM.exists(marker)) {
            println("CHECKLIST-TLS NOT RUN: disposable TLS server marker is absent")
            return@runTest
        }
        val port = FileSystem.SYSTEM.read(marker) { readUtf8() }.trim().toInt()
        require(port in 1024..65535)
        val config = WebDavConfig("https://localhost:$port/", "fixture", "fixture", "")
        val state = object : WebDavStateStore {
            override var propagation = Propagation.UNKNOWN
            override var certPin: CertPin? = null
        }
        // Fresh clients ensure the accepted connection/session cannot mask a later wrong-pin challenge.
        val rejected = assertFailsWith<DavUntrustedCertificateException> {
            createIosWebDavClient(config, state) { 0L }.propfind("", 0)
        }
        assertEquals("localhost", rejected.certificate.host)
        assertTrue(rejected.certificate.sha256.length == 64)
        println("CHECKLIST-TLS PASS: real server-trust challenge rejected and mapped to certificate decision")
        state.certPin = CertPin("localhost", rejected.certificate.sha256)
        val resources = createIosWebDavClient(config, state) { 0L }.propfind("", 0)
        assertEquals(1, resources.size)
        println("CHECKLIST-TLS PASS: credentialForTrust accepted only the observed certificate")
        state.certPin = CertPin("localhost", "00".repeat(32))
        assertFailsWith<DavUntrustedCertificateException> {
            createIosWebDavClient(config, state) { 0L }.propfind("", 0)
        }
        println("CHECKLIST-TLS PASS: wrong pin rejected on a fresh NSURLSession")
    }
}

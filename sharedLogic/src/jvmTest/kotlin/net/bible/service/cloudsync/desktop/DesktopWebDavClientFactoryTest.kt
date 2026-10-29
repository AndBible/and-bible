package net.bible.service.cloudsync.desktop

import com.sun.net.httpserver.HttpServer
import com.sun.net.httpserver.HttpsConfigurator
import com.sun.net.httpserver.HttpsServer
import kotlinx.coroutines.runBlocking
import net.bible.service.cloudsync.CloudSyncUserCancelledException
import net.bible.service.cloudsync.SyncSettingsPlatform
import net.bible.service.cloudsync.host.IosCloudAdapterFactory
import net.bible.service.cloudsync.host.PlainHttpRefusedException
import net.bible.service.cloudsync.webdav.CertPin
import net.bible.service.cloudsync.webdav.Propagation
import net.bible.service.cloudsync.webdav.WebDavCloudAdapter
import net.bible.service.cloudsync.webdav.WebDavConfig
import net.bible.service.cloudsync.webdav.WebDavMessage
import net.bible.service.cloudsync.webdav.WebDavSignInUi
import net.bible.service.cloudsync.webdav.WebDavStateStore
import net.bible.service.cloudsync.webdav.sha256HexUpper
import net.bible.sharedcore.webdav.DavCertificateInfo
import net.bible.sharedcore.webdav.DavUntrustedCertificateException
import okio.Path.Companion.toPath
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.file.Files
import java.security.KeyStore
import java.security.cert.X509Certificate
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DesktopWebDavClientFactoryTest {
    private class Cert(val keyStore: KeyStore, val x509: X509Certificate) {
        val sha256 get() = sha256HexUpper(x509.encoded)
    }

    private val tmp = Files.createTempDirectory("d3c-tls").toFile()
    private val servers = mutableListOf<HttpServer>()

    @AfterTest fun cleanUp() {
        servers.forEach { it.stop(0) }
        tmp.deleteRecursively()
    }

    /** A self-signed certificate for [cn] (SAN: localhost and 127.0.0.1), generated with the JDK's keytool. */
    private fun selfSigned(cn: String): Cert {
        val file = File(tmp, "$cn-${System.nanoTime()}.p12")
        val keytool = File(System.getProperty("java.home"), "bin/keytool").path
        val p = ProcessBuilder(
            keytool, "-genkeypair", "-alias", "k", "-keyalg", "RSA", "-keysize", "2048", "-validity", "30",
            "-dname", "CN=$cn", "-ext", "san=dns:localhost,ip:127.0.0.1", "-keystore", file.path,
            "-storetype", "PKCS12", "-storepass", "changeit", "-keypass", "changeit",
        ).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        check(p.waitFor() == 0) { "keytool failed: $out" }
        val ks = KeyStore.getInstance("PKCS12").apply { file.inputStream().use { load(it, "changeit".toCharArray()) } }
        return Cert(ks, ks.getCertificate("k") as X509Certificate)
    }

    private val multistatus = """<d:multistatus xmlns:d="DAV:"><d:response><d:href>/dav/</d:href><d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response></d:multistatus>"""

    private fun startHttps(cert: Cert): HttpsServer {
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(cert.keyStore, "changeit".toCharArray()) }
        val ctx = SSLContext.getInstance("TLS").apply { init(kmf.keyManagers, null, null) }
        val server = HttpsServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        server.httpsConfigurator = HttpsConfigurator(ctx)
        server.createContext("/dav/") { ex ->
            val body = multistatus.toByteArray()
            ex.sendResponseHeaders(207, body.size.toLong())
            ex.responseBody.use { it.write(body) }
        }
        server.start()
        servers += server
        return server
    }

    private fun startHttp(): HttpServer {
        val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        server.start()
        servers += server
        return server
    }

    private val state = object : WebDavStateStore {
        override var propagation = Propagation.UNKNOWN
        override var certPin: CertPin? = null
    }

    private fun config(server: HttpServer, host: String = "localhost") =
        WebDavConfig("https://$host:${server.address.port}/dav/", "u", "p", "")

    private fun trusting(cert: Cert): () -> X509TrustManager = {
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            .apply { init(KeyStore.getInstance("PKCS12").apply { load(null); setCertificateEntry("t", cert.x509) }) }
            .trustManagers.filterIsInstance<X509TrustManager>().first()
    }

    private val factory = DesktopWebDavClientFactory()

    @Test fun trustedCertificateConnects() = runBlocking {
        val cert = selfSigned("localhost")
        val server = startHttps(cert)
        val client = DesktopWebDavClientFactory(platformTrust = trusting(cert)).create(config(server), state)
        assertEquals(1, client.propfind("", 0).size)
        assertEquals(null, state.certPin)
    }

    @Test fun unknownSelfSignedRaisesUntrustedWithDetails() = runBlocking {
        val cert = selfSigned("my-nas")
        val server = startHttps(cert)
        val e = assertFailsWith<DavUntrustedCertificateException> { factory.create(config(server), state).propfind("", 0) }
        val info = e.certificate
        assertEquals("localhost", info.host)
        assertEquals(cert.sha256, info.sha256)
        assertTrue(info.subject.contains("CN=my-nas"), info.subject)
        assertTrue(info.issuer.contains("CN=my-nas"), info.issuer)
        assertEquals(cert.x509.notAfter.time, info.notAfter)
        assertEquals(cert.x509.notBefore.time, info.notBefore)
    }

    private class FakeUi(val accept: Boolean) : WebDavSignInUi {
        val asked = mutableListOf<DavCertificateInfo>()
        override suspend fun confirmCertificate(info: DavCertificateInfo): Boolean { asked += info; return accept }
        override fun message(kind: WebDavMessage) = kind.name
    }

    private fun adapter(cfg: WebDavConfig, ui: WebDavSignInUi) =
        WebDavCloudAdapter(cfg, state, ui, "dev", { c, s -> factory.create(c, s) })

    @Test fun unknownSelfSignedPromptsAndPinsOnAccept() = runBlocking {
        val cert = selfSigned("localhost")
        val cfg = config(startHttps(cert))
        val ui = FakeUi(accept = true)
        assertTrue(adapter(cfg, ui).signIn())
        assertEquals(1, ui.asked.size)
        assertEquals(CertPin("localhost", cert.sha256), state.certPin)
        // A later client with the stored pin connects without asking again.
        assertEquals(1, factory.create(cfg, state).propfind("", 0).size)
    }

    @Test fun rejectedPromptFailsWithIosEquivalentError() = runBlocking {
        val cfg = config(startHttps(selfSigned("localhost")))
        val ui = FakeUi(accept = false)
        assertFailsWith<CloudSyncUserCancelledException> { adapter(cfg, ui).signIn() }
        assertEquals(1, ui.asked.size)
        assertEquals(null, state.certPin)
    }

    @Test fun changedCertificateAfterPinIsRefused() = runBlocking {
        val pinned = selfSigned("localhost")
        val other = selfSigned("localhost")
        val server = startHttps(other)
        state.certPin = CertPin("localhost", pinned.sha256)
        val e = assertFailsWith<DavUntrustedCertificateException> { factory.create(config(server), state).propfind("", 0) }
        assertEquals(other.sha256, e.certificate.sha256)
    }

    @Test fun pinTakenOnOtherHostDoesNotApply() = runBlocking {
        val cert = selfSigned("localhost")
        val server = startHttps(cert)
        state.certPin = CertPin("localhost", cert.sha256)
        val e = assertFailsWith<DavUntrustedCertificateException> { factory.create(config(server, "127.0.0.1"), state).propfind("", 0) }
        assertEquals("127.0.0.1", e.certificate.host)
    }

    @Test fun pinnedCertReachedByIpPassesHostnameCheck() = runBlocking {
        val cert = selfSigned("other-name")
        val server = startHttps(cert)
        state.certPin = CertPin("127.0.0.1", cert.sha256)
        assertEquals(1, factory.create(config(server, "127.0.0.1"), state).propfind("", 0).size)
    }

    @Test fun plainHttpAllowedOnlyForLocalHosts() {
        listOf("http://example.com", "http://nas.example.org:8080/x", "http://192.0.2.1").forEach { assertFalse(desktopPlainHttpAllowed(it), it) }
        listOf("https://example.com", "http://localhost:8080", "http://127.0.0.1:1/x", "http://nas.local").forEach { assertTrue(desktopPlainHttpAllowed(it), it) }
    }

    @Test fun plainHttpRefusedExceptNextcloudBackend() = runBlocking {
        val secrets = mutableMapOf<String, String>()
        val platform = object : SyncSettingsPlatform {
            override fun credential(key: String) = secrets[key] ?: ""
            override fun setCredential(key: String, value: String) { secrets[key] = value }
            override fun isValidUrl(value: String) = true
            override fun formatFileSize(bytes: Long) = "$bytes"
            override fun formatSyncTime(timestamp: Long) = "$timestamp"
            override fun formatDecimal2(value: Double) = "$value"
            override val webDavServerUrlHint = ""
            override val webDavState get() = state
            override fun plainHttpAllowed(url: String) = desktopPlainHttpAllowed(url)
        }
        val ui = FakeUi(false)
        val built = mutableListOf<WebDavConfig>()
        val f = IosCloudAdapterFactory(
            platform, "dev", { it }, { error("no iCloud") }, ui, tmp.path.toPath(),
            clientFactory = { c, s -> built += c; factory.create(c, s) },
        )
        // NEXT_CLOUD over plain http to a public host: refused before any client is built.
        secrets["cloud_sync_username"] = "u"; secrets["cloud_sync_password"] = "p"
        secrets["cloud_sync_server_url"] = "http://example.com"
        val failure = runCatching { f.create("NEXT_CLOUD").signIn() }.exceptionOrNull()
        val refusal = generateSequence(failure) { it.cause }.filterIsInstance<PlainHttpRefusedException>().firstOrNull()
        assertNotNull(refusal, "expected a plain-http refusal, got $failure")
        assertTrue(built.isEmpty())
        // NEXT_CLOUD over http to a local host passes the policy (a client is built).
        val local = startHttp()
        secrets["cloud_sync_server_url"] = "http://127.0.0.1:${local.address.port}"
        runCatching { f.create("NEXT_CLOUD").signIn() }
        assertEquals(1, built.size)
    }
}

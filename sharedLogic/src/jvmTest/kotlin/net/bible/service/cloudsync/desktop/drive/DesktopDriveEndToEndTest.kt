package net.bible.service.cloudsync.desktop.drive

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import io.ktor.http.parseQueryString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import net.bible.service.cloudsync.CloudSyncUserFacingException
import net.bible.service.cloudsync.drive.GoogleDriveRestAdapter
import net.bible.service.cloudsync.drive.createGoogleDriveHttpClient
import net.bible.service.cloudsync.drive.fake.FakeDriveServer
import net.bible.service.cloudsync.drive.fake.ktorEngine
import net.bible.service.cloudsync.drive.oauth.GoogleDriveSecretKeys
import net.bible.service.cloudsync.drive.oauth.GoogleOAuthClient
import net.bible.service.cloudsync.drive.oauth.Pkce
import net.bible.service.cloudsync.drive.oauth.PkceGoogleAuth
import net.bible.sharedcore.platform.secrets.SecretStore
import okio.Buffer
import okio.Path.Companion.toOkioPath
import java.net.HttpURLConnection
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Desktop Google Drive sign-in and sync end to end: [PkceGoogleAuth] with a real [LoopbackOAuthBrowser] (its
 * "browser" plays Google by GETting the redirect), a mock token endpoint and the Drive adapter on [FakeDriveServer].
 */
class DesktopDriveEndToEndTest {
    private class MemSecrets : SecretStore {
        val map = HashMap<String, String>()
        override fun get(key: String) = map[key]
        override fun put(key: String, value: String) { map[key] = value }
        override fun remove(key: String) { map.remove(key) }
    }

    private class TokenForm(val fields: Map<String, String>)

    private val secrets = MemSecrets()
    private val tokenRequests = CopyOnWriteArrayList<TokenForm>()
    private var issued = 0
    private var currentAccess = ""
    private var revokeRefresh = false
    private val browserUrls = CopyOnWriteArrayList<String>()
    private var now = 1_000_000L

    private val tokenHttp = HttpClient(MockEngine { req ->
        val p = parseQueryString(String(req.body.toByteArray()))
        val form = TokenForm(p.names().associateWith { p[it]!! })
        tokenRequests += form
        val json = HttpHeaders.ContentType to "application/json"
        when (form.fields["grant_type"]) {
            "authorization_code" -> {
                currentAccess = "access-${++issued}"
                respond("""{"access_token":"$currentAccess","expires_in":3600,"refresh_token":"refresh-1"}""", HttpStatusCode.OK, headersOf(json.first, json.second))
            }
            else -> if (revokeRefresh) {
                respond("""{"error":"invalid_grant"}""", HttpStatusCode.BadRequest, headersOf(json.first, json.second))
            } else {
                currentAccess = "access-${++issued}"
                respond("""{"access_token":"$currentAccess","expires_in":3600}""", HttpStatusCode.OK, headersOf(json.first, json.second))
            }
        }
    })

    private val drive = FakeDriveServer().also { it.authorize = { h -> h == "Bearer $currentAccess" } }

    /** Plays Google: reads redirect_uri and state from the consent URL and GETs the redirect with a code. */
    private val googleBrowser = LoopbackOAuthBrowser(
        openBrowser = { url ->
            browserUrls += url
            val q = Url(url).parameters
            val target = q["redirect_uri"]!! + "?code=the-code&state=" + q["state"]
            withContext(Dispatchers.IO) {
                (java.net.URL(target).openConnection() as HttpURLConnection).run {
                    connectTimeout = 5000
                    readTimeout = 5000
                    try { responseCode } finally { disconnect() }
                }
            }
            true
        },
        timeoutMs = 20_000,
        successPage = "<html>ok</html>",
    )

    private fun newAuth(interactive: Boolean) = PkceGoogleAuth(
        GoogleOAuthClient("desktop-id", "desktop-secret"), googleBrowser, secrets, tokenHttp, { now }, interactive,
    )

    private fun adapter(auth: PkceGoogleAuth) =
        GoogleDriveRestAdapter(auth, createGoogleDriveHttpClient(drive.ktorEngine()))

    @Test
    fun signInThenSyncThenSilentRestart() = runBlocking<Unit> {
        withTimeout(60_000) {
            val first = adapter(newAuth(interactive = true))
            assertTrue(first.signIn())

            val exchange = tokenRequests.single().fields
            assertEquals("authorization_code", exchange["grant_type"])
            assertEquals("the-code", exchange["code"])
            assertEquals("desktop-id", exchange["client_id"])
            assertEquals("desktop-secret", exchange["client_secret"])
            val consent = Url(browserUrls.single()).parameters
            assertEquals("https://www.googleapis.com/auth/drive.appdata", consent["scope"])
            assertEquals("code", consent["response_type"])
            assertEquals("offline", consent["access_type"])
            assertEquals(consent["code_challenge"], Pkce.challenge(exchange["code_verifier"]!!))
            assertTrue(exchange["redirect_uri"]!!.startsWith("http://127.0.0.1:"))
            assertEquals("refresh-1", secrets.get(GoogleDriveSecretKeys.REFRESH_TOKEN))

            val folder = first.createNewFolder("sync-folder", null)
            val tmp = Files.createTempFile("e2e", ".bin")
            try {
                Files.write(tmp, "hello drive".toByteArray())
                val uploaded = first.upload("data.bin", tmp.toOkioPath(), folder.id)
                assertEquals(listOf(uploaded.id), first.listFiles(listOf(folder.id), null, null, null).map { it.id })
                val out = Buffer()
                first.download(uploaded.id, out, null)
                assertEquals("hello drive", out.readUtf8())
            } finally {
                Files.deleteIfExists(tmp)
            }

            // Restart: new non-interactive auth over the same store refreshes silently, without a browser.
            val second = adapter(newAuth(interactive = false))
            assertTrue(second.signIn())
            assertEquals(listOf("data.bin"), second.listFiles(listOf(folder.id), null, null, null).map { it.name })
            val refresh = tokenRequests.last().fields
            assertEquals("refresh_token", refresh["grant_type"])
            assertEquals("refresh-1", refresh["refresh_token"])
            assertEquals("desktop-secret", refresh["client_secret"])
            assertEquals(1, browserUrls.size)
        }
    }

    @Test
    fun wrongStateInRedirectIsRefused() = runBlocking<Unit> {
        withTimeout(60_000) {
            val evil = LoopbackOAuthBrowser(
                openBrowser = { url ->
                    val target = Url(url).parameters["redirect_uri"]!! + "?code=the-code&state=other-state"
                    withContext(Dispatchers.IO) {
                        (java.net.URL(target).openConnection() as HttpURLConnection).run {
                            connectTimeout = 5000
                            readTimeout = 5000
                            try { responseCode } finally { disconnect() }
                        }
                    }
                    true
                },
                timeoutMs = 20_000,
                successPage = "<html>ok</html>",
            )
            val auth = PkceGoogleAuth(GoogleOAuthClient("desktop-id", "desktop-secret"), evil, secrets, tokenHttp, { now }, true)
            assertEquals(false, auth.signIn())
            assertNull(secrets.get(GoogleDriveSecretKeys.REFRESH_TOKEN))
            assertTrue(tokenRequests.isEmpty())
        }
    }

    @Test
    fun revokedTokenSurfacesReconnectAndClearsStore() = runBlocking<Unit> {
        withTimeout(60_000) {
            assertTrue(adapter(newAuth(interactive = true)).signIn())
            revokeRefresh = true
            val restarted = adapter(newAuth(interactive = false))
            val e = assertFailsWith<CloudSyncUserFacingException> { restarted.createNewFolder("x", null) }
            assertTrue(e.requiresReconnect)
            assertNull(secrets.get(GoogleDriveSecretKeys.REFRESH_TOKEN))
            assertEquals(1, browserUrls.size)
        }
    }
}

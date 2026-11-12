package net.bible.service.cloudsync.drive.oauth

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.http.parseQueryString
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import net.bible.service.cloudsync.CloudSyncUserCancelledException
import net.bible.service.cloudsync.drive.GoogleAuthRequiredException
import net.bible.sharedcore.platform.secrets.SecretStore
import okio.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class MemSecrets : SecretStore {
    val map = mutableMapOf<String, String>()
    override fun get(key: String) = map[key]
    override fun put(key: String, value: String) { map[key] = value }
    override fun remove(key: String) { map.remove(key) }
}

private const val REDIRECT = "http://127.0.0.1:5555/"

private class FakeBrowser(val reply: (Url) -> String) : OAuthBrowser {
    var calls = 0
    var lastUrl: Url? = null
    override suspend fun authorize(buildUrl: (String) -> String): String {
        calls++
        val url = Url(buildUrl(REDIRECT))
        lastUrl = url
        return reply(url)
    }
}

class PkceGoogleAuthTest {
    private val secrets = MemSecrets()
    private var now = 1_000_000L
    private val requests = mutableListOf<Pair<HttpRequestData, Map<String, String>>>()
    private var handler: suspend MockRequestHandleScope.(HttpRequestData, Map<String, String>) -> HttpResponseData =
        { _, _ -> tokenOk() }

    private fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

    private fun MockRequestHandleScope.tokenOk(access: String = "acc1", expires: Int = 3600, refresh: String? = "ref1") =
        json("""{"access_token":"$access","expires_in":$expires${refresh?.let { ""","refresh_token":"$it"""" } ?: ""}}""")

    private val http = HttpClient(MockEngine { req ->
        val body = parseQueryString(String((req.body as OutgoingContent.ByteArrayContent).bytes())).let { p -> p.names().associateWith { p[it]!! } }
        requests += req to body
        handler(req, body)
    })

    private fun auth(browser: OAuthBrowser, interactive: Boolean = true, store: SecretStore = secrets) =
        PkceGoogleAuth(GoogleOAuthClient("cid", "csecret"), browser, store, http, { now }, interactive)

    private fun okBrowser() = FakeBrowser { u ->
        "$REDIRECT?code=abc&state=${u.parameters["state"]}"
    }

    @Test
    fun authorizationUrlCarriesPkceAndScope() = runBlocking<Unit> {
        val b = okBrowser()
        auth(b).signIn()
        val p = b.lastUrl!!.parameters
        assertEquals("accounts.google.com", b.lastUrl!!.host)
        assertEquals("code", p["response_type"])
        assertEquals("cid", p["client_id"])
        assertEquals(REDIRECT, p["redirect_uri"])
        assertEquals("https://www.googleapis.com/auth/drive.appdata", p["scope"])
        assertEquals("S256", p["code_challenge_method"])
        assertTrue(p["code_challenge"]!!.length == 43)
        assertTrue(!p["state"].isNullOrEmpty())
        assertEquals("offline", p["access_type"])
        assertEquals("consent", p["prompt"])
    }

    @Test
    fun signInStoresRefreshTokenAndCachesAccessToken() = runBlocking<Unit> {
        val b = okBrowser()
        val a = auth(b)
        assertTrue(a.signIn())
        val (_, body) = requests.single()
        assertEquals("authorization_code", body["grant_type"])
        assertEquals("abc", body["code"])
        assertEquals(REDIRECT, body["redirect_uri"])
        assertEquals("cid", body["client_id"])
        assertEquals("csecret", body["client_secret"])
        assertEquals(b.lastUrl!!.parameters["code_challenge"], Pkce.challenge(body["code_verifier"]!!))
        assertEquals("ref1", secrets.get(GoogleDriveSecretKeys.REFRESH_TOKEN))
        assertEquals("acc1", a.accessToken())
        assertEquals(1, requests.size)
    }

    @Test
    fun noClientSecretIsOmitted() = runBlocking<Unit> {
        PkceGoogleAuth(GoogleOAuthClient("cid"), okBrowser(), secrets, http, { now }).signIn()
        assertNull(requests.single().second["client_secret"])
    }

    @Test
    fun wrongStateIsRefused() = runBlocking<Unit> {
        val a = auth(FakeBrowser { "$REDIRECT?code=abc&state=evil" })
        assertFalse(a.signIn())
        assertTrue(requests.isEmpty())
        assertNull(secrets.get(GoogleDriveSecretKeys.REFRESH_TOKEN))
    }

    @Test
    fun accessDeniedIsCancelled() = runBlocking<Unit> {
        val a = auth(FakeBrowser { u -> "$REDIRECT?error=access_denied&state=${u.parameters["state"]}" })
        assertFailsWith<CloudSyncUserCancelledException> { a.signIn() }
        assertNull(secrets.get(GoogleDriveSecretKeys.REFRESH_TOKEN))
        assertTrue(requests.isEmpty())
    }

    @Test
    fun otherErrorIsFalse() = runBlocking<Unit> {
        val a = auth(FakeBrowser { u -> "$REDIRECT?error=server_error&state=${u.parameters["state"]}" })
        assertFalse(a.signIn())
        assertNull(secrets.get(GoogleDriveSecretKeys.REFRESH_TOKEN))
    }

    @Test
    fun refreshAfterExpiry() = runBlocking<Unit> {
        val a = auth(okBrowser())
        a.signIn()
        now += 3600_000L - 59_000L // inside the 60 s margin
        handler = { _, _ -> tokenOk(access = "acc2", refresh = null) }
        assertEquals("acc2", a.accessToken())
        val body = requests.last().second
        assertEquals("refresh_token", body["grant_type"])
        assertEquals("ref1", body["refresh_token"])
        assertEquals("acc2", a.accessToken())
        assertEquals(2, requests.size)
    }

    @Test
    fun restartRefreshesSilently() = runBlocking<Unit> {
        secrets.put(GoogleDriveSecretKeys.REFRESH_TOKEN, "ref1")
        val b = okBrowser()
        val a = auth(b, interactive = false)
        assertTrue(a.signedIn)
        assertTrue(a.signIn())
        assertEquals("acc1", a.accessToken())
        assertEquals(1, requests.size)
        assertEquals(0, b.calls)
    }

    @Test
    fun invalidGrantForgetsToken() = runBlocking<Unit> {
        secrets.put(GoogleDriveSecretKeys.REFRESH_TOKEN, "ref1")
        handler = { _, _ -> json("""{"error":"invalid_grant"}""", HttpStatusCode.BadRequest) }
        val a = auth(okBrowser(), interactive = false)
        assertFailsWith<GoogleAuthRequiredException> { a.accessToken() }
        assertFalse(a.signedIn)
        assertNull(secrets.get(GoogleDriveSecretKeys.REFRESH_TOKEN))
    }

    @Test
    fun networkErrorKeepsToken() = runBlocking<Unit> {
        secrets.put(GoogleDriveSecretKeys.REFRESH_TOKEN, "ref1")
        handler = { _, _ -> throw IOException("offline") }
        val a = auth(okBrowser(), interactive = false)
        assertFailsWith<IOException> { a.accessToken() }
        assertEquals("ref1", secrets.get(GoogleDriveSecretKeys.REFRESH_TOKEN))
    }

    @Test
    fun noTokenAccessTokenRequiresSignIn() = runBlocking<Unit> {
        assertFailsWith<GoogleAuthRequiredException> { auth(okBrowser()).accessToken() }
    }

    @Test
    fun backgroundSignInWithoutTokenIsFalse() = runBlocking<Unit> {
        val b = okBrowser()
        assertFalse(auth(b, interactive = false).signIn())
        assertEquals(0, b.calls)
    }

    @Test
    fun invalidateDropsCache() = runBlocking<Unit> {
        val a = auth(okBrowser())
        a.signIn()
        a.invalidate("other")
        a.accessToken()
        assertEquals(1, requests.size)
        a.invalidate("acc1")
        handler = { _, _ -> tokenOk(access = "acc2", refresh = null) }
        assertEquals("acc2", a.accessToken())
    }

    @Test
    fun concurrentAccessTokenRefreshesOnce() = runBlocking<Unit> {
        secrets.put(GoogleDriveSecretKeys.REFRESH_TOKEN, "ref1")
        handler = { _, _ -> delay(100); tokenOk() }
        val a = auth(okBrowser(), interactive = false)
        val results = (1..8).map { async(Dispatchers.Default) { a.accessToken() } }.awaitAll()
        assertEquals(setOf("acc1"), results.toSet())
        assertEquals(1, requests.size)
    }

    @Test
    fun pendingSignInDoesNotBlockTokenCallsOrSignOut() = runBlocking<Unit> {
        val gate = CompletableDeferred<String>()
        val entered = CompletableDeferred<Unit>()
        val blocking = object : OAuthBrowser {
            override suspend fun authorize(buildUrl: (String) -> String): String {
                buildUrl(REDIRECT)
                entered.complete(Unit)
                return gate.await()
            }
        }
        val a = auth(blocking)
        val pending = async(Dispatchers.Default) { a.signIn() }
        entered.await()
        // Another component (or a previous run) has stored a token meanwhile.
        secrets.put(GoogleDriveSecretKeys.REFRESH_TOKEN, "ref1")
        withTimeout(5_000) {
            assertEquals("acc1", a.accessToken())
            a.invalidate("acc1")
            a.signOut()
        }
        assertNull(secrets.get(GoogleDriveSecretKeys.REFRESH_TOKEN))
        gate.complete("$REDIRECT?error=server_error&state=x")
        assertFalse(pending.await())
    }

    @Test
    fun concurrentSignInsOpenOneBrowserAtATime() = runBlocking<Unit> {
        var active = 0
        var maxActive = 0
        val browser = object : OAuthBrowser {
            override suspend fun authorize(buildUrl: (String) -> String): String {
                active++; maxActive = maxOf(maxActive, active)
                val url = Url(buildUrl(REDIRECT))
                delay(50)
                active--
                return "$REDIRECT?error=server_error&state=${url.parameters["state"]}"
            }
        }
        val a = auth(browser)
        (1..3).map { async(Dispatchers.Default) { a.signIn() } }.awaitAll()
        assertEquals(1, maxActive)
    }

    @Test
    fun failedExchangeStoresNothing() = runBlocking<Unit> {
        handler = { _, _ -> json("""{"error":"invalid_request"}""", HttpStatusCode.BadRequest) }
        assertFalse(auth(okBrowser()).signIn())
        assertNull(secrets.get(GoogleDriveSecretKeys.REFRESH_TOKEN))
    }

    @Test
    fun exchangeWithoutRefreshTokenStoresNothing() = runBlocking<Unit> {
        handler = { _, _ -> tokenOk(refresh = null) }
        val a = auth(okBrowser())
        assertFalse(a.signIn())
        assertNull(secrets.get(GoogleDriveSecretKeys.REFRESH_TOKEN))
        assertFalse(a.signedIn)
    }

    @Test
    fun forgedStateWithAccessDeniedIsRefusedNotCancelled() = runBlocking<Unit> {
        val a = auth(FakeBrowser { "$REDIRECT?error=access_denied&state=evil" })
        assertFalse(a.signIn())
        assertTrue(requests.isEmpty())
    }

    @Test
    fun tokenIsReusedJustBeforeTheMargin() = runBlocking<Unit> {
        val a = auth(okBrowser())
        a.signIn()
        now += 3600_000L - 61_000L // 1 s before the 60 s safety margin
        assertEquals("acc1", a.accessToken())
        assertEquals(1, requests.size)
    }

    @Test
    fun signOutRevokesAndForgets() = runBlocking<Unit> {
        val a = auth(okBrowser())
        a.signIn()
        handler = { _, _ -> respond("", HttpStatusCode.InternalServerError) }
        a.signOut()
        val (req, body) = requests.last()
        assertEquals("oauth2.googleapis.com", req.url.host)
        assertEquals("/revoke", req.url.encodedPath)
        assertEquals("ref1", body["token"])
        assertNull(secrets.get(GoogleDriveSecretKeys.REFRESH_TOKEN))
        assertFalse(a.signedIn)
        assertFailsWith<GoogleAuthRequiredException> { a.accessToken() }
    }
}

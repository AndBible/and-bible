package net.bible.service.cloudsync.drivebrowser

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import io.ktor.http.parseQueryString
import net.bible.service.cloudsync.drive.createGoogleDriveHttpClient
import net.bible.service.cloudsync.drive.fake.FakeDriveRequest
import net.bible.service.cloudsync.drive.fake.FakeDriveServer
import net.bible.service.cloudsync.drive.oauth.GoogleOAuthClients
import net.bible.sharedcore.platform.secrets.SecretStore
import java.util.concurrent.ConcurrentHashMap

/** In-memory [SecretStore] for tests. */
class MapSecretStore : SecretStore {
    val map = ConcurrentHashMap<String, String>()
    override fun get(key: String): String? = map[key]
    override fun put(key: String, value: String) { map[key] = value }
    override fun remove(key: String) { map.remove(key) }
}

/**
 * Plays Google for the browser sign-in: a token endpoint that issues one access token per exchange or refresh, and a
 * [FakeDriveServer] that accepts only the current access token. [browser] plays the user's browser: it reads `state`
 * from the consent URL and delivers the redirect to [OAuthRedirectInbox] as the App Link would.
 */
class FakeGoogle(val drive: FakeDriveServer = FakeDriveServer()) {
    @Volatile var currentAccess: String? = null
    private var issued = 0
    val tokenForms = mutableListOf<Map<String, String>>()
    val consentUrls = mutableListOf<String>()

    init {
        drive.authorize = { h -> currentAccess != null && h == "Bearer $currentAccess" }
    }

    val http: HttpClient = createGoogleDriveHttpClient(MockEngine { request ->
        if (request.url.host == "oauth2.googleapis.com" && request.url.encodedPath == "/token") {
            val p = parseQueryString(String(request.body.toByteArray()))
            val form = p.names().associateWith { p[it]!! }
            synchronized(tokenForms) { tokenForms += form }
            currentAccess = "access-${++issued}"
            val body = if (form["grant_type"] == "authorization_code") {
                """{"access_token":"$currentAccess","expires_in":3600,"refresh_token":"refresh-1","scope":"https://www.googleapis.com/auth/drive.appdata"}"""
            } else {
                """{"access_token":"$currentAccess","expires_in":3600}"""
            }
            return@MockEngine respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val headers = HashMap<String, String>()
        request.headers.forEach { name, values -> headers[name.lowercase()] = values.joinToString(",") }
        request.body.contentType?.let { headers.putIfAbsent("content-type", it.toString()) }
        request.body.contentLength?.let { headers.putIfAbsent("content-length", it.toString()) }
        val response = drive.handle(FakeDriveRequest(request.method.value, request.url.toString(), headers, request.body.toByteArray()))
        val out = response.headers.filterKeys { !it.equals(HttpHeaders.ContentLength, ignoreCase = true) }
        respond(response.body, HttpStatusCode.fromValue(response.status), headersOf(*out.map { it.key to listOf(it.value) }.toTypedArray()))
    })

    /** The user allows access in the browser; Google redirects to the App Link with a code. */
    val browser: (String) -> Unit = { url ->
        consentUrls += url
        val state = Url(url).parameters["state"]
        check(OAuthRedirectInbox.deliver("${GoogleOAuthClients.ANDROID_WEB_REDIRECT_URI}?code=the-code&state=$state"))
    }
}

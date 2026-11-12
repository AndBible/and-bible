package net.bible.service.cloudsync.drive.oauth

import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import io.ktor.http.parseQueryString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import net.bible.service.cloudsync.CloudSyncUserCancelledException
import net.bible.service.cloudsync.drive.DRIVE_APPDATA_SCOPE
import net.bible.service.cloudsync.drive.GoogleAuth
import net.bible.service.cloudsync.drive.GoogleAuthRequiredException
import net.bible.sharedcore.platform.secrets.SecretStore
import okio.IOException

/** [SecretStore] keys used by the Drive sign-in. */
object GoogleDriveSecretKeys {
    const val REFRESH_TOKEN = "google_drive_refresh_token"
}

private const val AUTH_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth"
private const val TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token"
private const val REVOKE_ENDPOINT = "https://oauth2.googleapis.com/revoke"
private const val EXPIRY_MARGIN_MS = 60_000L

/**
 * [GoogleAuth] using the OAuth 2.0 authorization-code flow with PKCE. The refresh token lives only in [secrets];
 * the access token is cached in memory.
 *
 * @param interactive `false` for background sync: [signIn] never opens the browser.
 */
class PkceGoogleAuth(
    private val client: GoogleOAuthClient,
    private val browser: OAuthBrowser,
    private val secrets: SecretStore,
    private val http: HttpClient,
    private val nowMs: () -> Long,
    private val interactive: Boolean = true,
) : GoogleAuth {
    private val mutex = Mutex()
    private var cachedToken: String? = null
    private var cachedExpiryMs = 0L

    override val signedIn: Boolean get() = secrets.get(GoogleDriveSecretKeys.REFRESH_TOKEN) != null

    override suspend fun signIn(): Boolean = mutex.withLock {
        if (signedIn) return true
        if (!interactive) return false
        val verifier = Pkce.verifier()
        val state = Pkce.state()
        var usedRedirect = ""
        val redirect = browser.authorize { redirectUri ->
            usedRedirect = redirectUri
            authorizationUrl(redirectUri, Pkce.challenge(verifier), state)
        }
        val query = parseQueryString(redirect.substringAfter('?', "").substringBefore('#'))
        if (query["state"] != state) return false
        query["error"]?.let { error ->
            if (error == "access_denied") throw CloudSyncUserCancelledException()
            return false
        }
        val code = query["code"] ?: return false
        val form = mutableMapOf(
            "grant_type" to "authorization_code",
            "code" to code,
            "code_verifier" to verifier,
            "redirect_uri" to usedRedirect,
        )
        val response = tokenRequest(form)
        if (!response.status.isSuccess()) return false
        val json = parse(response.bodyAsText()) ?: return false
        val refresh = json.string("refresh_token") ?: return false
        val access = json.string("access_token") ?: return false
        secrets.put(GoogleDriveSecretKeys.REFRESH_TOKEN, refresh)
        cache(access, json)
        true
    }

    override suspend fun accessToken(): String = mutex.withLock {
        cachedToken?.takeIf { nowMs() < cachedExpiryMs }?.let { return it }
        val refresh = secrets.get(GoogleDriveSecretKeys.REFRESH_TOKEN)
            ?: throw GoogleAuthRequiredException("Not signed in to Google Drive")
        // Network failures propagate as IOException; the refresh token is kept so sync can retry later.
        val response = tokenRequest(mapOf("grant_type" to "refresh_token", "refresh_token" to refresh))
        val body = response.bodyAsText()
        val json = parse(body)
        if (!response.status.isSuccess()) {
            if (json?.string("error") == "invalid_grant") {
                secrets.remove(GoogleDriveSecretKeys.REFRESH_TOKEN)
                cachedToken = null
                throw GoogleAuthRequiredException("Google Drive access was revoked; sign in again")
            }
            throw IOException("Google token refresh failed with HTTP ${response.status.value}")
        }
        val access = json?.string("access_token") ?: throw IOException("Google token response has no access_token")
        cache(access, json)
        access
    }

    override suspend fun invalidate(token: String) {
        mutex.withLock { if (cachedToken == token) cachedToken = null }
    }

    override suspend fun signOut() {
        mutex.withLock {
            val token = secrets.get(GoogleDriveSecretKeys.REFRESH_TOKEN) ?: cachedToken
            if (token != null) {
                try {
                    http.submitForm(REVOKE_ENDPOINT, Parameters.build { append("token", token) })
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Best effort: local sign-out must succeed offline.
                }
            }
            secrets.remove(GoogleDriveSecretKeys.REFRESH_TOKEN)
            cachedToken = null
        }
    }

    private fun authorizationUrl(redirectUri: String, challenge: String, state: String): String {
        val params = buildList {
            add("response_type" to "code")
            add("client_id" to client.clientId)
            add("redirect_uri" to redirectUri)
            add("scope" to DRIVE_APPDATA_SCOPE)
            add("code_challenge" to challenge)
            add("code_challenge_method" to "S256")
            add("state" to state)
            add("access_type" to "offline")
            if (!signedIn) add("prompt" to "consent")
        }
        return AUTH_ENDPOINT + "?" + params.joinToString("&") { (k, v) -> "$k=${v.encodeURLParameter()}" }
    }

    private suspend fun tokenRequest(fields: Map<String, String>): HttpResponse {
        val all = fields + ("client_id" to client.clientId) +
            listOfNotNull(client.clientSecret?.let { "client_secret" to it })
        return http.submitForm(TOKEN_ENDPOINT, Parameters.build { all.forEach { (k, v) -> append(k, v) } })
    }

    private fun cache(access: String, json: JsonObject) {
        val expiresIn = json["expires_in"]?.jsonPrimitive?.longOrNull ?: 3600L
        cachedToken = access
        cachedExpiryMs = nowMs() + expiresIn * 1000 - EXPIRY_MARGIN_MS
    }

    private fun parse(body: String): JsonObject? = try {
        Json.parseToJsonElement(body).jsonObject
    } catch (_: Exception) {
        null
    }

    private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.content
}

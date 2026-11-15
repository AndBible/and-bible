/*
 * Copyright (c) 2026 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */

package net.bible.service.cloudsync.phos

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

private const val B64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

/** Standard base64 with padding (java.util.Base64 needs API 26). */
internal fun base64(b: ByteArray): String {
    val sb = StringBuilder((b.size + 2) / 3 * 4)
    var i = 0
    while (i < b.size) {
        val n = (b[i].toInt() and 0xFF shl 16) or
            ((if (i + 1 < b.size) b[i + 1].toInt() and 0xFF else 0) shl 8) or
            (if (i + 2 < b.size) b[i + 2].toInt() and 0xFF else 0)
        sb.append(B64[n shr 18 and 63]).append(B64[n shr 12 and 63])
        sb.append(if (i + 1 < b.size) B64[n shr 6 and 63] else '=')
        sb.append(if (i + 2 < b.size) B64[n and 63] else '=')
        i += 3
    }
    return sb.toString()
}

/** Scheme added, trailing slashes removed. */
fun normalizePhosServerUrl(server: String): String {
    var s = server.trim()
    if (s.isEmpty()) return s
    if ("://" !in s) s = "https://$s"
    return s.trimEnd('/')
}

/** Nextcloud app password: web UI form (5 groups of 5 with dashes) or the 72 character issued form. */
fun looksLikeAppPassword(pw: String): Boolean {
    if (pw.length == 72) return pw.all { it.isLetterOrDigit() && it.code < 128 }
    if (pw.length != 29) return false
    return pw.indices.all { i -> if ((i + 1) % 6 == 0) pw[i] == '-' else pw[i].isLetterOrDigit() && pw[i].code < 128 }
}

private const val API = "/ocs/v2.php/apps/phos/api/v1"

/** The OCS API of the Phos app on one Nextcloud server. Uses only the platform's HttpURLConnection. */
class PhosHttpClient(
    server: String,
    private val user: String,
    private val password: String,
    private val userAgent: String = "AndBible",
) : PhosHttp {
    private val base = normalizePhosServerUrl(server)

    override fun get(path: String, params: Map<String, String>): JsonObject {
        val query = if (params.isEmpty()) "" else "?" + params.entries.joinToString("&") { (k, v) ->
            URLEncoder.encode(k, "UTF-8") + "=" + URLEncoder.encode(v, "UTF-8")
        }
        return call("GET", "$base$API$path$query", null)
    }

    override fun post(path: String, body: JsonObject): JsonObject =
        call("POST", "$base$API$path", Json.encodeToString(JsonObject.serializer(), body))

    private fun call(method: String, url: String, body: String?): JsonObject {
        val (code, text) = request(method, url, user, password, userAgent, body)
        val root = try { Json.parseToJsonElement(text) as? JsonObject } catch (t: Throwable) { null }
        val ocs = root?.get("ocs") as? JsonObject
        val data = ocs?.get("data") as? JsonObject
        if (code !in 200..299) {
            val msg = (data?.get("error") as? JsonPrimitive)?.contentOrNull
                ?: ((ocs?.get("meta") as? JsonObject)?.get("message") as? JsonPrimitive)?.contentOrNull
            throw PhosHttpException(code, when (code) {
                401 -> "Login refused (401): user name or app password wrong"
                404 -> "The Phos app is not installed on this Nextcloud (404)"
                503 -> "The Phos app needs an update on the server (503)"
                else -> "HTTP $code${msg?.let { ": $it" } ?: ""}"
            })
        }
        // some endpoints answer with an empty list as data
        return data ?: if (ocs != null) JsonObject(emptyMap())
        else throw PhosHttpException(code, "Unexpected answer from the server")
    }

    companion object {
        internal fun request(method: String, url: String, user: String, password: String, userAgent: String, body: String?): Pair<Int, String> {
            try {
                val conn = URL(url).openConnection() as HttpURLConnection
                try {
                    conn.requestMethod = method
                    conn.connectTimeout = 20_000
                    conn.readTimeout = 90_000
                    val auth = base64("$user:$password".toByteArray(Charsets.UTF_8))
                    conn.setRequestProperty("Authorization", "Basic $auth")
                    conn.setRequestProperty("OCS-APIRequest", "true")
                    conn.setRequestProperty("Accept", "application/json")
                    conn.setRequestProperty("User-Agent", userAgent)
                    if (body != null) {
                        conn.doOutput = true
                        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                        conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                    }
                    val code = conn.responseCode
                    val stream = if (code in 200..399) conn.inputStream else conn.errorStream
                    val text = stream?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
                    return code to text
                } finally {
                    conn.disconnect()
                }
            } catch (e: IOException) {
                throw PhosHttpException(0, e.message ?: "network error", network = true)
            }
        }

        /**
         * Trades a login password for a new app password (`/ocs/v2.php/core/getapppassword`), shown
         * under [userAgent] in Nextcloud's security settings. An app password is returned as it is.
         */
        fun appPassword(server: String, user: String, password: String, userAgent: String): String {
            if (looksLikeAppPassword(password)) return password
            val (code, text) = request("GET", normalizePhosServerUrl(server) + "/ocs/v2.php/core/getapppassword",
                user, password, userAgent, null)
            if (code == 401) throw PhosHttpException(401, "Login refused: user name or password wrong")
            if (code == 403) return password     // already an app password
            if (code !in 200..299) throw PhosHttpException(code, "No app password from the server (HTTP $code)")
            val data = try {
                ((Json.parseToJsonElement(text) as? JsonObject)?.get("ocs") as? JsonObject)?.get("data") as? JsonObject
            } catch (t: Throwable) { null }
            return (data?.get("apppassword") as? JsonPrimitive)?.contentOrNull
                ?: throw PhosHttpException(code, "No app password in the server's answer")
        }
    }
}

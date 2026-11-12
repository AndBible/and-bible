package net.bible.service.cloudsync.drive.fake

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.net.URI
import java.net.URLDecoder
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Base64

/** One HTTP request as seen by [FakeDriveServer]: absolute [url] (with query), lower-case header keys. */
class FakeDriveRequest(val method: String, val url: String, val headers: Map<String, String>, val body: ByteArray)

class FakeDriveResponse(val status: Int, val headers: Map<String, String>, val body: ByteArray)

data class FakeDriveFile(
    val id: String,
    val name: String,
    val mimeType: String,
    val parents: List<String>,
    val createdTime: Long,
    val content: ByteArray?,
)

/**
 * In-memory Google Drive v3 server for tests, restricted to the subset the Drive adapter's clients use: `files.list`,
 * `files.get` (metadata, `alt=media` with `Range`), `files.create` (metadata only, `uploadType=resumable`,
 * `uploadType=multipart`, `uploadType=media`) and `files.delete`, all in the `appDataFolder` space.
 *
 * Unsupported input fails loudly with a 400 so that a client emitting something new is noticed. Upload-session
 * requests (PUT with `upload_id`) are not authorised by header, like real Drive session URIs.
 */
class FakeDriveServer(
    /** Overrides the client's `pageSize` when set, so paging can be tested with few files. */
    var pageSize: Int? = null,
    val clock: () -> Long = { System.currentTimeMillis() },
) {
    /** When set, a resumable-upload chunk request keeps at most this many bytes (Drive may persist less than sent). */
    @Volatile
    var keepAtMostPerChunk: Long? = null

    /** When true, the next resumable-upload chunk request is answered 401 once (an access token expiring mid-upload). */
    @Volatile
    var rejectNextChunkUnauthorized: Boolean = false

    private class Session(
        val metadata: JsonObject,
        val declaredType: String?,
        val buffer: java.io.ByteArrayOutputStream = java.io.ByteArrayOutputStream(),
        /** Set once the upload finished; later requests on the session replay this response, as Drive does. */
        var finished: FakeDriveFile? = null,
    )

    private val lock = Any()
    private val files = LinkedHashMap<String, FakeDriveFile>()
    private val sessions = HashMap<String, Session>()
    private val log = ArrayList<FakeDriveRequest>()
    private var fileCounter = 0
    private var sessionCounter = 0

    init {
        files[ROOT_ID] = FakeDriveFile(ROOT_ID, "appDataFolder", FOLDER_MIME, emptyList(), 0L, null)
    }

    /** Every request received, in order (snapshot). */
    val requests: List<FakeDriveRequest> get() = synchronized(lock) { log.toList() }

    /** Checks the `Authorization` header value; the default accepts any non-blank `Bearer x`. */
    @Volatile
    var authorize: (String?) -> Boolean = { it != null && it.startsWith("Bearer ") && it.length > "Bearer ".length }

    fun file(id: String): FakeDriveFile? = synchronized(lock) { files[resolve(id)]?.takeIf { it.id != ROOT_ID } }

    fun putFile(name: String, parentId: String, content: ByteArray, createdTime: Long, mimeType: String = "application/gzip"): String =
        synchronized(lock) { store(name, listOf(resolve(parentId)), content, createdTime, mimeType).id }

    fun handle(request: FakeDriveRequest): FakeDriveResponse = synchronized(lock) {
        log += request
        try {
            route(request)
        } catch (e: BadRequest) {
            error(400, e.message ?: "Bad request", "badRequest")
        }
    }

    private class BadRequest(message: String) : Exception(message)

    private fun resolve(id: String) = if (id == "appDataFolder") ROOT_ID else id

    private fun store(name: String, parents: List<String>, content: ByteArray?, createdTime: Long, mimeType: String): FakeDriveFile {
        val id = "file-%06d".format(++fileCounter)
        return FakeDriveFile(id, name, mimeType, parents, createdTime, content).also { files[id] = it }
    }

    // ---- routing ----

    private fun route(r: FakeDriveRequest): FakeDriveResponse {
        val uri = URI(r.url)
        val path = uri.rawPath ?: ""
        val q = parseQuery(uri.rawQuery)
        val isSessionPut = r.method == "PUT" && path == UPLOAD_PATH && "upload_id" in q
        if (!isSessionPut && !authorize(r.headers["authorization"])) {
            return error(401, "Invalid Credentials", "authError")
        }
        return when {
            path == UPLOAD_PATH && r.method == "POST" -> when (q["uploadType"]) {
                "resumable" -> startResumable(r, uri)
                "multipart" -> multipart(r)
                "media" -> complete(JsonObject(emptyMap()), r.body, r.headers["content-type"])
                else -> throw BadRequest("Unsupported uploadType: ${q["uploadType"]}")
            }
            isSessionPut -> continueResumable(r, q.getValue("upload_id"))
            path == FILES_PATH && r.method == "GET" -> list(q)
            path == FILES_PATH && r.method == "POST" -> createMetadata(r)
            path.startsWith("$FILES_PATH/") -> {
                val id = URLDecoder.decode(path.removePrefix("$FILES_PATH/"), "UTF-8")
                when (r.method) {
                    "GET" -> if (q["alt"] == "media") media(id, r.headers["range"]) else metadata(id)
                    "DELETE" -> delete(id)
                    else -> error(405, "Unsupported method ${r.method}", "methodNotAllowed")
                }
            }
            else -> error(404, "Not Found", "notFound")
        }
    }

    private fun parseQuery(raw: String?): Map<String, String> =
        raw.orEmpty().split("&").filter { it.isNotEmpty() }.associate {
            val i = it.indexOf('=')
            val k = if (i < 0) it else it.substring(0, i)
            val v = if (i < 0) "" else it.substring(i + 1)
            URLDecoder.decode(k, "UTF-8") to URLDecoder.decode(v, "UTF-8")
        }

    // ---- files ----

    private fun list(q: Map<String, String>): FakeDriveResponse {
        if (q["spaces"] != "appDataFolder") throw BadRequest("spaces must be appDataFolder, was ${q["spaces"]}")
        val predicate = q["q"]?.let { parseExpr(it) } ?: { _: FakeDriveFile -> true }
        val matching = files.values.filter { it.id != ROOT_ID && predicate(it) }
            .sortedWith(compareBy<FakeDriveFile> { it.createdTime }.thenBy { it.id })
        val size = (pageSize ?: (q["pageSize"]?.toIntOrNull() ?: 100).coerceIn(1, 1000)).coerceAtLeast(1)
        val offset = q["pageToken"]?.let { decodeToken(it) } ?: 0
        val page = matching.drop(offset).take(size)
        val next = offset + page.size
        return json(200, buildJsonObject {
            put("kind", "drive#fileList")
            put("incompleteSearch", false)
            if (next < matching.size) put("nextPageToken", encodeToken(next))
            put("files", JsonArray(page.map { meta(it) }))
        })
    }

    private fun encodeToken(offset: Int) = Base64.getUrlEncoder().withoutPadding().encodeToString("off:$offset".encodeToByteArray())

    private fun decodeToken(token: String): Int = try {
        String(Base64.getUrlDecoder().decode(token)).removePrefix("off:").toInt().also { require(it >= 0) }
    } catch (e: IllegalArgumentException) { // includes NumberFormatException
        throw BadRequest("Invalid pageToken")
    }

    private fun createMetadata(r: FakeDriveRequest): FakeDriveResponse =
        complete(parseJson(r.body), null, null)

    private fun metadata(id: String): FakeDriveResponse {
        val f = files[resolve(id)] ?: return notFound(id)
        return json(200, meta(f))
    }

    private fun delete(id: String): FakeDriveResponse {
        val rid = resolve(id)
        if (rid == ROOT_ID || files.remove(rid) == null) return notFound(id)
        return FakeDriveResponse(204, emptyMap(), ByteArray(0))
    }

    private fun media(id: String, range: String?): FakeDriveResponse {
        val f = files[resolve(id)] ?: return notFound(id)
        val content = f.content ?: ByteArray(0)
        val common = mapOf("Content-Type" to f.mimeType, "Accept-Ranges" to "bytes")
        if (range == null) return FakeDriveResponse(200, common, content)
        val m = RANGE.matchEntire(range.trim()) ?: throw BadRequest("Unsupported Range: $range")
        val total = content.size
        val (a, b) = m.destructured
        if (a.isEmpty() && b.isEmpty()) throw BadRequest("Unsupported Range: $range")
        val start: Int
        val end: Int
        if (a.isEmpty()) { // suffix range: last N bytes
            val n = b.toLong().coerceAtMost(total.toLong()).toInt()
            start = total - n; end = total - 1
        } else {
            start = a.toLongOrNull()?.takeIf { it < total }?.toInt()
                ?: return error(416, "Requested range not satisfiable", "requestedRangeNotSatisfiable", mapOf("Content-Range" to "bytes */$total"))
            end = if (b.isEmpty()) total - 1 else b.toLong().coerceAtMost(total - 1L).toInt()
        }
        if (end < start) return error(416, "Requested range not satisfiable", "requestedRangeNotSatisfiable", mapOf("Content-Range" to "bytes */$total"))
        return FakeDriveResponse(206, common + ("Content-Range" to "bytes $start-$end/$total"), content.copyOfRange(start, end + 1))
    }

    // ---- uploads ----

    private fun startResumable(r: FakeDriveRequest, uri: URI): FakeDriveResponse {
        val id = "session-${++sessionCounter}"
        sessions[id] = Session(if (r.body.isEmpty()) JsonObject(emptyMap()) else parseJson(r.body), r.headers["x-upload-content-type"])
        val loc = "${uri.scheme}://${uri.authority}$UPLOAD_PATH?uploadType=resumable&upload_id=$id"
        return FakeDriveResponse(200, mapOf("Location" to loc, "Content-Length" to "0"), ByteArray(0))
    }

    private fun continueResumable(r: FakeDriveRequest, sessionId: String): FakeDriveResponse {
        val s = sessions[sessionId] ?: return notFound(sessionId)
        if (rejectNextChunkUnauthorized) {
            rejectNextChunkUnauthorized = false
            return error(401, "Invalid Credentials", "authError")
        }
        s.finished?.let { return json(200, meta(it)) }
        val received = s.buffer.size()
        val cr = r.headers["content-range"]
        if (cr == null) { // whole content in one request
            s.buffer.reset(); s.buffer.write(r.body)
            return finishSession(sessionId, s, r.headers["content-type"])
        }
        val status = STATUS_RANGE.matchEntire(cr.trim())
        if (status != null) { // "bytes */total": a query for progress (or an empty file)
            val total = status.groupValues[1]
            return if (total != "*" && total.toLong() == received.toLong()) finishSession(sessionId, s, null) else incomplete(received)
        }
        val m = CONTENT_RANGE.matchEntire(cr.trim()) ?: throw BadRequest("Unsupported Content-Range: $cr")
        val start = m.groupValues[1].toLong()
        val total = m.groupValues[3]
        if (start > received) throw BadRequest("Chunk starts at $start but only $received bytes were received")
        val skip = (received - start).toInt()
        if (skip < r.body.size) {
            val n = (r.body.size - skip).toLong().coerceAtMost(keepAtMostPerChunk ?: Long.MAX_VALUE).toInt()
            s.buffer.write(r.body, skip, n)
        }
        return if (total != "*" && s.buffer.size().toLong() >= total.toLong()) finishSession(sessionId, s, null) else incomplete(s.buffer.size())
    }

    private fun incomplete(received: Int) =
        FakeDriveResponse(308, if (received > 0) mapOf("Range" to "bytes=0-${received - 1}") else emptyMap(), ByteArray(0))

    private fun finishSession(id: String, s: Session, contentType: String?): FakeDriveResponse {
        val response = complete(s.metadata, s.buffer.toByteArray(), contentType ?: s.declaredType)
        if (response.status == 200) s.finished = files.values.last()
        return response
    }

    private fun multipart(r: FakeDriveRequest): FakeDriveResponse {
        val ct = r.headers["content-type"] ?: throw BadRequest("Missing Content-Type")
        val boundary = BOUNDARY.find(ct)?.groupValues?.get(1) ?: throw BadRequest("Missing multipart boundary")
        val delim = "--$boundary".encodeToByteArray()
        val body = r.body
        val parts = ArrayList<Pair<Map<String, String>, ByteArray>>()
        var pos = indexOf(body, delim, 0)
        while (pos >= 0) {
            val afterDelim = pos + delim.size
            if (body.startsWith("--", afterDelim)) break
            val headerStart = afterDelim + (if (body.startsWith("\r\n", afterDelim)) 2 else 0)
            val headerEnd = indexOf(body, "\r\n\r\n".encodeToByteArray(), headerStart)
            if (headerEnd < 0) throw BadRequest("Malformed multipart body")
            val headers = String(body, headerStart, headerEnd - headerStart).split("\r\n").filter { ':' in it }
                .associate { it.substringBefore(':').trim().lowercase() to it.substringAfter(':').trim() }
            val contentStart = headerEnd + 4
            val next = indexOf(body, "\r\n--$boundary".encodeToByteArray(), contentStart)
            if (next < 0) throw BadRequest("Malformed multipart body")
            parts += headers to body.copyOfRange(contentStart, next)
            pos = next + 2
        }
        if (parts.size != 2) throw BadRequest("multipart upload needs exactly 2 parts, got ${parts.size}")
        return complete(parseJson(parts[0].second), parts[1].second, parts[1].first["content-type"])
    }

    private fun complete(meta: JsonObject, content: ByteArray?, contentType: String?): FakeDriveResponse {
        val parents = (meta["parents"] as? JsonArray)?.map { resolve(it.jsonPrimitive.content) } ?: listOf(ROOT_ID)
        parents.forEach { if (it !in files) return notFound(it) }
        val name = (meta["name"] as? JsonPrimitive)?.content ?: "Untitled"
        val mime = (meta["mimeType"] as? JsonPrimitive)?.content ?: contentType?.substringBefore(';')?.trim()
            ?: "application/octet-stream"
        return json(200, meta(store(name, parents, content, clock(), mime)))
    }

    // ---- q grammar ----

    private fun parseExpr(expr: String): (FakeDriveFile) -> Boolean {
        val s = expr.trim()
        val and = splitTopLevel(s, " and ")
        if (and.size > 1) return and.map { parseExpr(it) }.let { ps -> { f -> ps.all { it(f) } } }
        val or = splitTopLevel(s, " or ")
        if (or.size > 1) return or.map { parseExpr(it) }.let { ps -> { f -> ps.any { it(f) } } }
        if (s.startsWith("(") && s.endsWith(")") && closingOf(s, 0) == s.length - 1) return parseExpr(s.substring(1, s.length - 1))
        return simpleClause(s)
    }

    private fun simpleClause(s: String): (FakeDriveFile) -> Boolean {
        STR.let { str ->
            Regex("""$str in parents""").matchEntire(s)?.let { m ->
                val id = resolve(unescape(m.groupValues[1])); return { f -> id in f.parents }
            }
            Regex("""name = $str""").matchEntire(s)?.let { m ->
                val n = unescape(m.groupValues[1]); return { f -> f.name == n }
            }
            Regex("""mimeType = $str""").matchEntire(s)?.let { m ->
                val n = unescape(m.groupValues[1]); return { f -> f.mimeType == n }
            }
            Regex("""createdTime > $str""").matchEntire(s)?.let { m ->
                val t = try {
                    OffsetDateTime.parse(unescape(m.groupValues[1])).toInstant().toEpochMilli()
                } catch (e: java.time.format.DateTimeParseException) {
                    throw BadRequest("Invalid timestamp in query: ${m.groupValues[1]}")
                }
                return { f -> f.createdTime > t }
            }
        }
        throw BadRequest("Unsupported query clause: $s")
    }

    private fun unescape(s: String) = s.replace("\\'", "'").replace("\\\\", "\\")

    /** Index of the paren closing the one at [open], ignoring quoted text; -1 if unbalanced. */
    private fun closingOf(s: String, open: Int): Int {
        var depth = 0
        var i = open
        var quoted = false
        while (i < s.length) {
            val c = s[i]
            if (quoted) { if (c == '\\') i++ else if (c == '\'') quoted = false }
            else if (c == '\'') quoted = true
            else if (c == '(') depth++
            else if (c == ')') { depth--; if (depth == 0) return i }
            i++
        }
        return -1
    }

    private fun splitTopLevel(s: String, sep: String): List<String> {
        val out = ArrayList<String>()
        var depth = 0
        var quoted = false
        var last = 0
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (quoted) { if (c == '\\') i++ else if (c == '\'') quoted = false }
            else if (c == '\'') quoted = true
            else if (c == '(') depth++
            else if (c == ')') depth--
            else if (depth == 0 && s.startsWith(sep, i)) { out += s.substring(last, i); i += sep.length; last = i; continue }
            i++
        }
        out += s.substring(last)
        return out
    }

    // ---- helpers ----

    private fun meta(f: FakeDriveFile): JsonObject = buildJsonObject {
        put("kind", "drive#file")
        put("id", f.id)
        put("name", f.name)
        put("mimeType", f.mimeType)
        put("createdTime", TIME_FORMAT.format(Instant.ofEpochMilli(f.createdTime)))
        putJsonArray("parents") { f.parents.forEach { add(JsonPrimitive(it)) } }
        f.content?.let { put("size", it.size.toString()) }
    }

    private fun parseJson(bytes: ByteArray): JsonObject = try {
        Json.parseToJsonElement(bytes.decodeToString()).jsonObject
    } catch (e: Exception) {
        throw BadRequest("Invalid JSON body")
    }

    private fun json(status: Int, body: JsonElement, headers: Map<String, String> = emptyMap()) =
        FakeDriveResponse(status, mapOf("Content-Type" to "application/json; charset=UTF-8") + headers, body.toString().encodeToByteArray())

    private fun notFound(id: String) = error(404, "File not found: $id.", "notFound")

    private fun error(code: Int, message: String, reason: String, headers: Map<String, String> = emptyMap()) = json(code, buildJsonObject {
        put("error", buildJsonObject {
            put("code", code)
            put("message", message)
            putJsonArray("errors") { add(buildJsonObject { put("message", message); put("domain", "global"); put("reason", reason) }) }
        })
    }, headers)

    private fun ByteArray.startsWith(prefix: String, at: Int): Boolean {
        val p = prefix.encodeToByteArray()
        return at + p.size <= size && p.indices.all { this[at + it] == p[it] }
    }

    private fun indexOf(hay: ByteArray, needle: ByteArray, from: Int): Int {
        var i = from
        while (i <= hay.size - needle.size) {
            if (needle.indices.all { hay[i + it] == needle[it] }) return i
            i++
        }
        return -1
    }

    companion object {
        const val ROOT_ID = "appdata-root"
        private const val FOLDER_MIME = "application/vnd.google-apps.folder"
        private const val FILES_PATH = "/drive/v3/files"
        private const val UPLOAD_PATH = "/upload/drive/v3/files"
        private const val STR = """'((?:[^'\\]|\\.)*)'"""
        private val RANGE = Regex("""bytes=(\d*)-(\d*)""")
        private val STATUS_RANGE = Regex("""bytes \*/(\d+|\*)""")
        private val CONTENT_RANGE = Regex("""bytes (\d+)-(\d+)/(\d+|\*)""")
        private val BOUNDARY = Regex("""boundary="?([^";]+)"?""", RegexOption.IGNORE_CASE)
        private val TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)
    }
}

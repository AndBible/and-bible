package net.bible.service.cloudsync.webdav

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.decodeURLPart
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * In-memory WebDAV server as a [MockEngine], shared by the WebDAV adapter tests. Served under
 * `https://h/dav/`. Times are whole seconds of [nowServer] (as RFC 1123 has no sub-second part).
 * [propagates] controls whether creating/deleting a child bumps the parent collection's mtime.
 *
 * Failure hooks: [statusOverride] answers matching requests with a bare status (503, 401, 507...);
 * [failWith] makes the engine throw the returned exception for matching requests.
 */
class FakeDavServer(var nowServer: Long, var propagates: Boolean = true) {
    private class Node(val isCollection: Boolean, var bytes: ByteArray = ByteArray(0), var mtime: Long)

    private val nodes = linkedMapOf<String, Node>()
    private val log = mutableListOf<HttpRequestData>()
    /** A snapshot copy of the requests seen so far. */
    val requests: List<HttpRequestData> get() = synchronized(nodes) { log.toList() }
    fun clearRequests() { synchronized(nodes) { log.clear() } }

    /** With [propagates] on, whether creating a child (PUT/MKCOL of a new resource) bumps the parent's mtime. */
    var propagatesOnCreate = true
    /** With [propagates] on, whether deleting a child bumps the parent's mtime. */
    var propagatesOnDelete = true

    var authRequired = false
    /** Omit getlastmodified from PROPFIND answers (some servers do). */
    var omitLastModified = false
    var failWith: ((HttpRequestData) -> Throwable?)? = null
    var statusOverride: ((HttpRequestData) -> Int?)? = null

    val engine = MockEngine { req -> handle(req) }

    init { nodes[""] = Node(true, mtime = stamp()) }

    private fun stamp() = nowServer / 1000 * 1000
    fun advance(ms: Long) { synchronized(nodes) { nowServer += ms } }
    fun mtime(path: String): Long? = synchronized(nodes) { nodes[path]?.mtime }
    fun put(path: String, bytes: ByteArray) {
        synchronized(nodes) {
            val parent = parentOf(path)
            nodes[path] = Node(false, bytes, stamp())
            if (propagates) nodes[parent]?.mtime = stamp()
        }
    }

    private fun parentOf(path: String) = path.substringBeforeLast('/', "")

    private fun date() = DateTimeFormatter.RFC_1123_DATE_TIME.format(Instant.ofEpochMilli(nowServer).atOffset(ZoneOffset.UTC))

    private fun MockRequestHandleScope.reply(status: Int, body: String = "", vararg extra: Pair<String, String>): HttpResponseData =
        respond(
            body, HttpStatusCode.fromValue(status),
            headersOf(HttpHeaders.Date to listOf(date()), *extra.map { it.first to listOf(it.second) }.toTypedArray()),
        )

    /**
     * All state lives under one reentrant monitor (`nodes`), so concurrent requests (the adapter lists
     * folders in parallel) are serialised. The body is read before taking it: reading suspends.
     */
    private suspend fun MockRequestHandleScope.handle(req: HttpRequestData): HttpResponseData {
        val body = if (req.method.value == "PUT") readBody(req) else null
        return synchronized(nodes) { handleLocked(req, body) }
    }

    private fun MockRequestHandleScope.handleLocked(req: HttpRequestData, putBody: ByteArray?): HttpResponseData {
        log += req
        failWith?.invoke(req)?.let { throw it }
        statusOverride?.invoke(req)?.let { return reply(it) }
        if (authRequired && req.headers[HttpHeaders.Authorization] != "Basic bWU6cHc=")
            return reply(401, "", HttpHeaders.WWWAuthenticate to "Basic realm=\"t\"")
        val path = req.url.encodedPath.removePrefix("/dav").trim('/').decodeURLPart()
        return when (req.method.value) {
            "PROPFIND" -> propfind(path, req.headers["Depth"] ?: "1")
            "MKCOL" -> when {
                nodes.containsKey(path) -> reply(405)
                !nodes.containsKey(parentOf(path)) -> reply(409)
                else -> { nodes[path] = Node(true, mtime = stamp()); bump(path, delete = false); reply(201) }
            }
            "PUT" -> {
                val existed = nodes.containsKey(path)
                if (!nodes.containsKey(parentOf(path))) reply(409) else {
                    nodes[path] = Node(false, putBody ?: ByteArray(0), stamp())
                    if (!existed) bump(path, delete = false)
                    reply(if (existed) 204 else 201)
                }
            }
            "GET" -> nodes[path]?.takeIf { !it.isCollection }
                ?.let { respond(it.bytes, HttpStatusCode.OK, headersOf(HttpHeaders.Date to listOf(date()))) } ?: reply(404)
            "DELETE" -> if (nodes.remove(path) == null) reply(404) else {
                nodes.keys.removeAll { it.startsWith("$path/") }
                bump(path, delete = true)
                reply(204)
            }
            else -> reply(405)
        }
    }

    private fun bump(path: String, delete: Boolean) {
        if (propagates && (if (delete) propagatesOnDelete else propagatesOnCreate)) nodes[parentOf(path)]?.mtime = stamp()
    }

    private suspend fun readBody(req: HttpRequestData): ByteArray {
        val content = req.body as? OutgoingContent.WriteChannelContent ?: return ByteArray(0)
        val ch = ByteChannel(autoFlush = true)
        content.writeTo(ch); ch.close()
        return ch.readRemaining().readByteArray()
    }

    private fun MockRequestHandleScope.propfind(path: String, depth: String): HttpResponseData {
        val self = nodes[path] ?: return reply(404)
        val listed = mutableListOf(path to self)
        if (depth != "0" && self.isCollection)
            nodes.filter { (p, _) -> p.isNotEmpty() && parentOf(p) == path && p != path }.forEach { (p, n) -> listed += p to n }
        val xml = buildString {
            append("""<?xml version="1.0"?><d:multistatus xmlns:d="DAV:">""")
            for ((p, n) in listed) {
                val href = "/dav/" + p.split('/').filter { it.isNotEmpty() }.joinToString("/") { enc(it) } + if (n.isCollection && p.isNotEmpty()) "/" else ""
                append("<d:response><d:href>$href</d:href><d:propstat><d:prop>")
                append("<d:resourcetype>${if (n.isCollection) "<d:collection/>" else ""}</d:resourcetype>")
                append("<d:getcontentlength>${n.bytes.size}</d:getcontentlength>")
                if (!omitLastModified) append("<d:getlastmodified>${DateTimeFormatter.RFC_1123_DATE_TIME.format(Instant.ofEpochMilli(n.mtime).atOffset(ZoneOffset.UTC))}</d:getlastmodified>")
                append("</d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>")
            }
            append("</d:multistatus>")
        }
        return reply(207, xml, HttpHeaders.ContentType to "application/xml")
    }

    private fun enc(s: String) = buildString {
        for (b in s.encodeToByteArray()) {
            val c = b.toInt() and 0xFF
            if (c < 0x80 && (c.toChar().isLetterOrDigit() || c.toChar() in "-._~")) append(c.toChar()) else append("%%%02X".format(c))
        }
    }
}

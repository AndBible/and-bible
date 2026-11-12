package net.bible.service.cloudsync.drive.fake

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.bible.service.cloudsync.drive.createGoogleDriveHttpClient
import java.net.URLEncoder
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FakeDriveServerTest {
    private val base = "https://www.googleapis.com"
    private val server = FakeDriveServer(clock = { 5_000L })

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun req(
        method: String, path: String, query: Map<String, String> = emptyMap(),
        headers: Map<String, String> = emptyMap(), body: ByteArray = ByteArray(0), auth: Boolean = true,
    ): FakeDriveResponse {
        val qs = if (query.isEmpty()) "" else "?" + query.entries.joinToString("&") { "${enc(it.key)}=${enc(it.value)}" }
        val h = (if (auth) mapOf("authorization" to "Bearer tok") else emptyMap()) + headers
        return server.handle(FakeDriveRequest(method, "$base$path$qs", h, body))
    }

    private fun json(r: FakeDriveResponse): JsonObject = Json.parseToJsonElement(r.body.decodeToString()).jsonObject
    private fun ids(r: FakeDriveResponse): List<String> = json(r)["files"]!!.jsonArray.map { it.jsonObject["id"]!!.jsonPrimitive.content }
    private fun list(q: String, extra: Map<String, String> = emptyMap()) =
        req("GET", "/drive/v3/files", mapOf("q" to q, "spaces" to "appDataFolder") + extra)

    @Test
    fun listsByParentNameAndCreatedTimeWithOffsetTimestamps() {
        val a = server.putFile("a.gz", "appDataFolder", byteArrayOf(1), 1000)
        val b = server.putFile("b.gz", "appDataFolder", byteArrayOf(2), 2000)
        val r = list("('appDataFolder' in parents) and createdTime > '1970-01-01T02:00:01.000+02:00'")
        assertEquals(200, r.status)
        assertEquals(listOf(b), ids(r))
        // strictly greater: a file at exactly the bound is excluded
        assertEquals(listOf(b), ids(list("'appDataFolder' in parents and createdTime > '1970-01-01T00:00:01Z'")))
        assertEquals(listOf(a, b), ids(list("'appDataFolder' in parents and createdTime > '1969-12-31T23:59:59.999Z'")))
        assertEquals(listOf(a), ids(list("'appDataFolder' in parents and name = 'a.gz'")))
        assertEquals(emptyList(), ids(list("'appDataFolder' in parents and mimeType = 'text/plain'")))
        assertEquals(listOf(a, b), ids(list("('appdata-root' in parents or 'other' in parents) and mimeType = 'application/gzip'")))
    }

    @Test
    fun escapedQuoteInNameMatches() {
        val id = server.putFile("it's.gz", "appDataFolder", byteArrayOf(1), 1)
        assertEquals(listOf(id), ids(list("name = 'it\\'s.gz'")))
    }

    @Test
    fun listReturnsDriveShapedMetadata() {
        val id = server.putFile("a.gz", "appDataFolder", byteArrayOf(1, 2, 3), 1234)
        val f = json(list("'appDataFolder' in parents"))["files"]!!.jsonArray.single().jsonObject
        assertEquals(id, f["id"]!!.jsonPrimitive.content)
        assertEquals("3", f["size"]!!.jsonPrimitive.content)
        assertEquals("1970-01-01T00:00:01.234Z", f["createdTime"]!!.jsonPrimitive.content)
        assertEquals("appdata-root", f["parents"]!!.jsonArray.single().jsonPrimitive.content)
    }

    @Test
    fun pagesWithNextPageToken() {
        val paged = FakeDriveServer(pageSize = 2)
        val all = (1..5).map { paged.putFile("f$it", "appDataFolder", byteArrayOf(), it * 10L) }
        val seen = mutableListOf<String>()
        var token: String? = null
        var pages = 0
        do {
            val query = mutableMapOf("q" to "'appDataFolder' in parents", "spaces" to "appDataFolder", "pageSize" to "1000")
            token?.let { query["pageToken"] = it }
            val qs = query.entries.joinToString("&") { "${enc(it.key)}=${enc(it.value)}" }
            val r = paged.handle(FakeDriveRequest("GET", "$base/drive/v3/files?$qs", mapOf("authorization" to "Bearer x"), ByteArray(0)))
            val j = Json.parseToJsonElement(r.body.decodeToString()).jsonObject
            seen += (j["files"] as JsonArray).map { it.jsonObject["id"]!!.jsonPrimitive.content }
            token = j["nextPageToken"]?.jsonPrimitive?.content
            pages++
        } while (token != null)
        assertEquals(3, pages)
        assertEquals(all, seen)
    }

    @Test
    fun requestPageSizeIsUsedWithoutOverride() {
        repeat(3) { server.putFile("f$it", "appDataFolder", byteArrayOf(), it.toLong()) }
        val r = list("'appDataFolder' in parents", mapOf("pageSize" to "2"))
        assertEquals(2, ids(r).size)
        assertNotNull(json(r)["nextPageToken"])
    }

    @Test
    fun rangedMediaGetReturns206WithContentRange() {
        val id = server.putFile("a.gz", "appDataFolder", ByteArray(100) { it.toByte() }, 1)
        val r = req("GET", "/drive/v3/files/$id", mapOf("alt" to "media"), mapOf("range" to "bytes=10-19"))
        assertEquals(206, r.status)
        assertEquals("bytes 10-19/100", r.headers["Content-Range"])
        assertContentEquals(ByteArray(10) { (10 + it).toByte() }, r.body)
        // end beyond total is clamped, open-ended works
        val tail = req("GET", "/drive/v3/files/$id", mapOf("alt" to "media"), mapOf("range" to "bytes=95-"))
        assertEquals(206, tail.status)
        assertEquals("bytes 95-99/100", tail.headers["Content-Range"])
        assertEquals(5, tail.body.size)
        assertEquals(416, req("GET", "/drive/v3/files/$id", mapOf("alt" to "media"), mapOf("range" to "bytes=100-120")).status)
    }

    @Test
    fun rangelessMediaGetReturnsWholeContent() {
        val id = server.putFile("a.gz", "appDataFolder", byteArrayOf(9, 8, 7), 1)
        val r = req("GET", "/drive/v3/files/$id", mapOf("alt" to "media"))
        assertEquals(200, r.status)
        assertContentEquals(byteArrayOf(9, 8, 7), r.body)
    }

    @Test
    fun missingFileIs404WithDriveErrorJson() {
        val r = req("GET", "/drive/v3/files/nope")
        assertEquals(404, r.status)
        val e = json(r)["error"]!!.jsonObject
        assertEquals("404", e["code"]!!.jsonPrimitive.content)
        assertEquals("File not found: nope.", e["message"]!!.jsonPrimitive.content)
        assertEquals("notFound", e["errors"]!!.jsonArray.single().jsonObject["reason"]!!.jsonPrimitive.content)
        assertEquals(404, req("DELETE", "/drive/v3/files/nope").status)
    }

    @Test
    fun unknownQueryClauseIs400() {
        assertEquals(400, list("trashed = false").status)
        assertEquals(400, list("'appDataFolder' in parents and fullText contains 'x'").status)
        assertEquals(400, list("createdTime > 'yesterday'").status)
    }

    @Test
    fun spacesMustBeAppDataFolder() {
        assertEquals(400, req("GET", "/drive/v3/files", mapOf("q" to "name = 'a'", "spaces" to "drive")).status)
        assertEquals(400, req("GET", "/drive/v3/files", mapOf("q" to "name = 'a'")).status)
    }

    @Test
    fun authFailureIs401AndCustomAuthorizeIsHonoured() {
        val r = req("GET", "/drive/v3/files", mapOf("spaces" to "appDataFolder"), auth = false)
        assertEquals(401, r.status)
        assertEquals("authError", json(r)["error"]!!.jsonObject["errors"]!!.jsonArray.single().jsonObject["reason"]!!.jsonPrimitive.content)
        server.authorize = { it == "Bearer good" }
        assertEquals(401, req("GET", "/drive/v3/files", mapOf("spaces" to "appDataFolder")).status)
        assertEquals(200, req("GET", "/drive/v3/files", mapOf("spaces" to "appDataFolder"), mapOf("authorization" to "Bearer good")).status)
    }

    @Test
    fun createMetadataGetAndDelete() {
        val r = req("POST", "/drive/v3/files", headers = mapOf("content-type" to "application/json"),
            body = """{"name":"folder","mimeType":"application/vnd.google-apps.folder","parents":["appDataFolder"]}""".encodeToByteArray())
        assertEquals(200, r.status)
        val id = json(r)["id"]!!.jsonPrimitive.content
        assertEquals(listOf("appdata-root"), server.file(id)!!.parents)
        assertEquals("folder", json(req("GET", "/drive/v3/files/$id", mapOf("fields" to "id,name")))["name"]!!.jsonPrimitive.content)
        assertEquals(204, req("DELETE", "/drive/v3/files/$id").status)
        assertNull(server.file(id))
    }

    @Test
    fun resumableUploadInTwoChunksStoresWholeContent() {
        val content = ByteArray(10) { it.toByte() }
        val start = req("POST", "/upload/drive/v3/files", mapOf("uploadType" to "resumable"),
            mapOf("content-type" to "application/json; charset=UTF-8", "x-upload-content-length" to "10"),
            """{"name":"up.gz","parents":["appDataFolder"],"mimeType":"application/gzip"}""".encodeToByteArray())
        assertEquals(200, start.status)
        val session = start.headers["Location"]!!
        assertTrue(session.startsWith("https://"))
        val first = server.handle(FakeDriveRequest("PUT", session, mapOf("content-range" to "bytes 0-5/10"), content.copyOfRange(0, 6)))
        assertEquals(308, first.status)
        assertEquals("bytes=0-5", first.headers["Range"])
        assertEquals(0, server.requests.count { it.method == "DELETE" })
        val last = server.handle(FakeDriveRequest("PUT", session, mapOf("content-range" to "bytes 6-9/10"), content.copyOfRange(6, 10)))
        assertEquals(200, last.status)
        val id = json(last)["id"]!!.jsonPrimitive.content
        assertContentEquals(content, server.file(id)!!.content)
        assertEquals("up.gz", server.file(id)!!.name)
        assertEquals("10", json(last)["size"]!!.jsonPrimitive.content)
    }

    @Test
    fun resumableUploadWithoutContentRangeAndEmptyFile() {
        fun begin(name: String) = req("POST", "/upload/drive/v3/files", mapOf("uploadType" to "resumable"),
            body = """{"name":"$name","parents":["appDataFolder"]}""".encodeToByteArray()).headers["Location"]!!
        val whole = server.handle(FakeDriveRequest("PUT", begin("w"), emptyMap(), byteArrayOf(1, 2)))
        assertEquals(200, whole.status)
        assertContentEquals(byteArrayOf(1, 2), server.file(json(whole)["id"]!!.jsonPrimitive.content)!!.content)
        val empty = server.handle(FakeDriveRequest("PUT", begin("e"), mapOf("content-range" to "bytes */0"), ByteArray(0)))
        assertEquals(200, empty.status)
        assertEquals(0, server.file(json(empty)["id"]!!.jsonPrimitive.content)!!.content!!.size)
    }

    @Test
    fun resumableUploadStatusQueryAndUnknownSession() {
        val session = req("POST", "/upload/drive/v3/files", mapOf("uploadType" to "resumable"),
            body = """{"name":"s","parents":["appDataFolder"]}""".encodeToByteArray()).headers["Location"]!!
        server.handle(FakeDriveRequest("PUT", session, mapOf("content-range" to "bytes 0-2/8"), ByteArray(3)))
        val status = server.handle(FakeDriveRequest("PUT", session, mapOf("content-range" to "bytes */8"), ByteArray(0)))
        assertEquals(308, status.status)
        assertEquals("bytes=0-2", status.headers["Range"])
        val bad = server.handle(FakeDriveRequest("PUT", "$base/upload/drive/v3/files?uploadType=resumable&upload_id=nope", emptyMap(), ByteArray(0)))
        assertEquals(404, bad.status)
    }

    @Test
    fun multipartUploadStoresMetadataAndMedia() {
        val boundary = "foo_bar_baz"
        val media = byteArrayOf(0, 13, 10, 45, 45, 1, 2, 3)
        val body = ("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n" +
            """{"name":"mp.gz","parents":["appDataFolder"]}""" + "\r\n--$boundary\r\nContent-Type: application/gzip\r\n\r\n").encodeToByteArray() +
            media + "\r\n--$boundary--\r\n".encodeToByteArray()
        val r = req("POST", "/upload/drive/v3/files", mapOf("uploadType" to "multipart"),
            mapOf("content-type" to "multipart/related; boundary=\"$boundary\""), body)
        assertEquals(200, r.status)
        val f = server.file(json(r)["id"]!!.jsonPrimitive.content)!!
        assertEquals("mp.gz", f.name)
        assertEquals("application/gzip", f.mimeType)
        assertContentEquals(media, f.content)
    }

    @Test
    fun ktorEngineRoutesToHandleAndLogsRequests() = runBlocking {
        server.putFile("a.gz", "appDataFolder", byteArrayOf(1), 1)
        val client = createGoogleDriveHttpClient(server.ktorEngine())
        val resp = client.get("$base/drive/v3/files") {
            url { parameters.append("spaces", "appDataFolder"); parameters.append("q", "name = 'a.gz'") }
            header("Authorization", "Bearer abc")
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        assertTrue(resp.bodyAsText().contains("a.gz"))
        val logged = server.requests.single()
        assertEquals("GET", logged.method)
        assertEquals("bearer abc", logged.headers["authorization"]!!.lowercase())
        assertEquals(401, client.get("$base/drive/v3/files?spaces=appDataFolder").status.value)
    }
}

package net.bible.service.cloudsync.googledrive

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.service.cloudsync.CloudAdapter
import net.bible.service.cloudsync.drive.fake.FakeDriveRequest
import net.bible.service.cloudsync.drive.fake.FakeDriveServer
import net.bible.service.common.CommonUtils
import net.bible.sharedcore.platform.cloud.CloudFile
import okio.Buffer
import okio.Path
import okio.Path.Companion.toOkioPath
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileNotFoundException
import net.bible.service.cloudsync.drive.DRIVE_MULTIPART_MAX_BYTES
import java.net.URI
import java.net.URLDecoder
import java.time.OffsetDateTime
import java.util.TimeZone
import kotlin.random.Random

/**
 * The old app's Google API client adapter (frozen as [LegacyDriveAdapter], "old") and the shared Ktor adapter
 * ([net.bible.service.cloudsync.drive.GoogleDriveRestAdapter], "new") against one [FakeDriveServer]: whatever one
 * writes, the other reads identically, and both send equivalent requests. Each test runs in both directions
 * ([writerName] writes, the other reads), because devices on the old and the new adapter share one Drive.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class DriveAdapterCompatibilityTest(private val writerName: String) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun directions(): List<Array<Any>> = listOf(arrayOf("old→new"), arrayOf("new→old"))
    }

    private val server = FakeDriveServer()
    private val old = oldDriveAdapter(server)
    private val new = newDriveAdapter(server)
    private val writer: CloudAdapter get() = if (writerName.startsWith("old")) old else new
    private val reader: CloudAdapter get() = if (writerName.startsWith("old")) new else old
    private val tempFiles = mutableListOf<File>()

    @After fun tearDown() {
        tempFiles.forEach { it.delete() }
    }

    private fun run(block: suspend () -> Unit) = runBlocking { withTimeout(60_000) { block() } }

    private fun payload(size: Int, seed: Int): Pair<ByteArray, Path> {
        val bytes = Random(seed).nextBytes(size)
        val file = File.createTempFile("drive-compat-", ".gz", CommonUtils.tmpDir).also { tempFiles += it }
        file.writeBytes(bytes)
        return bytes to file.toOkioPath()
    }

    private suspend fun CloudAdapter.downloadBytes(id: String, progress: MutableList<Long>? = null): ByteArray {
        val buffer = Buffer()
        download(id, buffer, progress?.let { list -> { n: Long -> list += n } })
        return buffer.readByteArray()
    }

    @Test fun folderCreatedByOneIsListedByOther() = run {
        val root = writer.createNewFolder("net.bible.android.activity-sync-bookmarks")
        val device = writer.createNewFolder("device-a", root.id)
        assertEquals(listOf(root), reader.getFolders("appDataFolder"))
        assertEquals(listOf(device), reader.getFolders(root.id))
        assertEquals(root, reader.get(root.id))
        assertEquals(FakeDriveServer.ROOT_ID, root.parentId)
        assertEquals(root.id, device.parentId)
        assertEquals(listOf(root), reader.listFiles(name = "net.bible.android.activity-sync-bookmarks"))
    }

    @Test fun uploadedPatchIsDownloadedIdentically() = run {
        val folder = writer.createNewFolder("device")
        for ((size, seed) in listOf(3 * 1024 to 1, 9 * 1024 * 1024 to 2)) {
            val (bytes, path) = payload(size, seed)
            val uploaded = writer.upload("1.12.sqlite3.gz", path, folder.id)
            assertEquals(size.toLong(), uploaded.size)
            assertEquals(folder.id, uploaded.parentId)
            assertEquals("application/gzip", server.file(uploaded.id)!!.mimeType)
            assertArrayEquals("$size bytes stored", bytes, server.file(uploaded.id)!!.content)
            assertEquals(uploaded, reader.get(uploaded.id))
            assertArrayEquals("$size bytes read back by the reader", bytes, reader.downloadBytes(uploaded.id))
            val progress = mutableListOf<Long>()
            assertArrayEquals("$size bytes read back with progress", bytes, reader.downloadBytes(uploaded.id, progress))
            assertEquals(size.toLong(), progress.last())
            assertArrayEquals("$size bytes read back by the writer", bytes, writer.downloadBytes(uploaded.id))
        }
    }

    @Test fun createdTimeFilteredListingsAgree() {
        val zone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Helsinki"))
        try {
            run {
                val folder = writer.createNewFolder("device")
                // Summer time (+03:00); millisecond precision matters: one file is 1 ms past the threshold.
                val base = 1_751_000_000_123L
                val times = listOf(base - 60_000, base - 1, base, base + 1, base + 86_400_000)
                val ids = times.mapIndexed { i, t -> server.putFile("$i.12.sqlite3.gz", folder.id, byteArrayOf(i.toByte()), t) }
                val expected = ids.drop(3).toSet()
                for (adapter in listOf(writer, reader)) {
                    val listed = adapter.listFiles(parentsIds = listOf(folder.id), createdTimeAtLeast = base)
                    assertEquals(expected, listed.map { it.id }.toSet())
                    assertEquals(times.drop(3).toSet(), listed.map { it.createdTime }.toSet())
                }
                // Winter time (+02:00) too.
                val winter = 1_736_000_000_456L
                val w = server.putFile("w.12.sqlite3.gz", folder.id, byteArrayOf(9), winter + 1)
                server.putFile("v.12.sqlite3.gz", folder.id, byteArrayOf(9), winter)
                assertEquals(
                    writer.listFiles(parentsIds = listOf(folder.id), createdTimeAtLeast = winter).map { it.id }.toSet(),
                    reader.listFiles(parentsIds = listOf(folder.id), createdTimeAtLeast = winter).map { it.id }.toSet(),
                )
                assertTrue(w in reader.listFiles(parentsIds = listOf(folder.id), createdTimeAtLeast = winter).map { it.id })
            }
        } finally {
            TimeZone.setDefault(zone)
        }
    }

    @Test fun multiPageListingsAgree() = run {
        server.pageSize = 2
        val a = writer.createNewFolder("device-a")
        val b = writer.createNewFolder("device-b")
        val ids = (1..7).map { n ->
            writer.upload("$n.12.sqlite3.gz", payload(16, n).second, if (n % 2 == 0) a.id else b.id).id
        }
        val before = server.requests.size
        val fromReader = reader.listFiles(parentsIds = listOf(a.id, b.id))
        val readerPages = server.requests.drop(before).count { it.method == "GET" && it.path() == "/drive/v3/files" }
        assertEquals(4, readerPages)
        assertEquals(ids.toSet(), fromReader.map { it.id }.toSet())
        assertEquals(7, fromReader.size)
        assertEquals(fromReader.sortedBy { it.id }, writer.listFiles(parentsIds = listOf(a.id, b.id)).sortedBy { it.id })
        assertEquals(listOf(a, b).sortedBy { it.id }, reader.getFolders("appDataFolder").sortedBy { it.id })
    }

    @Test fun deleteByOneIsGoneForOther() = run {
        val folder = reader.createNewFolder("device")
        val file = reader.upload("1.12.sqlite3.gz", payload(100, 3).second, folder.id)
        writer.delete(file.id)
        for (adapter in listOf(reader, writer)) {
            try {
                adapter.get(file.id)
                fail("deleted file must be gone")
            } catch (e: FileNotFoundException) { /* expected */ }
        }
        assertEquals(emptyList<CloudFile>(), reader.listFiles(parentsIds = listOf(folder.id)))
        assertEquals(emptyList<CloudFile>(), writer.listFiles(parentsIds = listOf(folder.id)))
        writer.delete(folder.id)
        assertEquals(emptyList<CloudFile>(), reader.getFolders("appDataFolder"))
        try {
            reader.get(folder.id)
            fail("deleted folder must be gone")
        } catch (e: FileNotFoundException) { /* expected */ }
    }

    /**
     * Uploads deliberately differ: the old app always starts a resumable session (two requests), the shared adapter
     * sends a file under [DRIVE_MULTIPART_MAX_BYTES] as one multipart request. What Drive stores must be identical.
     */
    @Test fun smallUploadsStoreTheSameFileOverDifferentProtocols() = run {
        val parent = writer.createNewFolder("parent")
        val (bytes, small) = payload(3 * 1024, 4)
        val stored = listOf(old, new).map { adapter ->
            val before = server.requests.size
            val uploaded = adapter.upload("1.12.sqlite3.gz", small, parent.id)
            val types = server.requests.drop(before).filter { it.method == "POST" }.map { r -> URI(r.url).rawQuery.split("&").firstOrNull { it.startsWith("uploadType=") }?.substringAfter('=') }
            assertEquals(if (adapter === old) listOf("resumable") else listOf("multipart"), types)
            if (adapter === new) assertEquals("one request", 1, server.requests.size - before)
            server.file(uploaded.id)!!
        }
        val (o, n) = stored
        assertEquals(o.name, n.name)
        assertEquals(o.mimeType, n.mimeType)
        assertEquals(o.parents, n.parents)
        assertArrayEquals(bytes, o.content)
        assertArrayEquals(bytes, n.content)
    }

    /** One request reduced to what both clients must agree on. */
    private data class Shape(
        val method: String,
        val path: String,
        val params: Map<String, String>,
        val body: Map<String, String>,
        val headers: Map<String, String>,
    )

    private fun FakeDriveRequest.path(): String = URI(url).rawPath

    private fun FakeDriveRequest.shape(): Shape {
        val query = URI(url).rawQuery.orEmpty().split("&").filter { it.isNotEmpty() }.associate {
            URLDecoder.decode(it.substringBefore('='), "UTF-8") to URLDecoder.decode(it.substringAfter('=', ""), "UTF-8")
        }
        val params = query.filterKeys { it in setOf("spaces", "fields", "pageSize", "uploadType", "q", "alt") }
            .mapValues { (k, v) -> if (k == "q") normalizeCreatedTime(v) else v }
        val json = if (method == "POST" && body.isNotEmpty()) Json.parseToJsonElement(body.decodeToString()).jsonObject else JsonObject(emptyMap())
        val bodyShape = json.filterKeys { it in setOf("name", "parents", "mimeType") }.mapValues { (_, v) ->
            when (v) {
                is JsonArray -> v.joinToString(",") { (it as JsonPrimitive).content }
                else -> (v as JsonPrimitive).content
            }
        }
        val hdrs = headers.filterKeys { it in setOf("x-upload-content-type", "x-upload-content-length", "content-range") }
        // The "delete" step deletes a folder each adapter just created itself: ids differ, the request shape must not.
        val path = if (method == "DELETE") path().replace(Regex("file-\\d+$"), "{id}") else path()
        return Shape(method, path, params, bodyShape, hdrs)
    }

    /** The `createdTime` literal as an instant, so `+03:00` (old, local offset) and `Z` (new) compare equal. */
    private fun normalizeCreatedTime(q: String): String =
        Regex("""createdTime > '([^']*)'""").replace(q) { m ->
            "createdTime > '${OffsetDateTime.parse(m.groupValues[1]).toInstant().toEpochMilli()}'"
        }

    private suspend fun shapesOf(adapter: CloudAdapter, block: suspend CloudAdapter.() -> Unit): List<Shape> {
        val before = server.requests.size
        adapter.block()
        return server.requests.drop(before).map { it.shape() }
    }

    @Test fun requestsAreEquivalent() {
        val zone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Helsinki"))
        try {
            run {
                val parent = writer.createNewFolder("parent")
                val ops: List<Pair<String, suspend CloudAdapter.() -> Unit>> = listOf(
                    "list" to { listFiles(listOf(parent.id, "appDataFolder"), "1.12.sqlite3.gz", "application/gzip", 1_751_000_000_123L) },
                    "getFolders" to { getFolders(parent.id) },
                    "create folder (root)" to { createNewFolder("sync") },
                    "create folder (child)" to { createNewFolder("device", parent.id) },
                    "get" to { get(parent.id) },
                    "delete" to { delete(createNewFolder("doomed").id) },
                )
                for ((name, op) in ops) {
                    val w = shapesOf(writer, op)
                    val r = shapesOf(reader, op)
                    assertTrue("$name sent no request", w.isNotEmpty())
                    assertEquals("$name: requests differ", w, r)
                }
            }
        } finally {
            TimeZone.setDefault(zone)
        }
    }
}

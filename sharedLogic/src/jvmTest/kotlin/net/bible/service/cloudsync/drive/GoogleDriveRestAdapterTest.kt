package net.bible.service.cloudsync.drive

import io.ktor.client.engine.mock.respond
import io.ktor.http.Url
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.bible.service.cloudsync.CloudSyncUserCancelledException
import net.bible.service.cloudsync.CloudSyncUserFacingException
import net.bible.service.cloudsync.GZIP_MIMETYPE
import net.bible.service.cloudsync.drive.fake.FakeDriveServer
import net.bible.service.cloudsync.drive.fake.FakeGoogleAuth
import net.bible.service.cloudsync.drive.fake.ktorEngine
import okio.Buffer
import okio.FileNotFoundException
import okio.ForwardingSink
import okio.Path.Companion.toOkioPath
import java.nio.file.Files
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GoogleDriveRestAdapterTest {
    private val root = FakeDriveServer.ROOT_ID
    private val server = FakeDriveServer()
    private val auth = FakeGoogleAuth()
    private val adapter = GoogleDriveRestAdapter(auth, createGoogleDriveHttpClient(server.ktorEngine()))
    private val tmp = Files.createTempDirectory("drive-adapter-test")
    private val MiB = 1024 * 1024

    @AfterTest
    fun cleanup() {
        tmp.toFile().deleteRecursively()
    }

    private fun downloadBytes(id: String, progress: MutableList<Long>? = null): ByteArray = runBlocking {
        val buffer = Buffer()
        adapter.download(id, buffer, progress?.let { p -> { n: Long -> p += n } })
        buffer.readByteArray()
    }

    @Test
    fun createFolderDefaultsToAppDataRoot() = runBlocking {
        val folder = adapter.createNewFolder("x")
        assertEquals(listOf(root), server.file(folder.id)!!.parents)
        assertEquals(root, folder.parentId)
        assertEquals("x", folder.name)
        assertEquals(listOf(folder.id), adapter.getFolders(root).map { it.id })
    }

    @Test
    fun listFilesEmitsAndroidQuery() = runBlocking {
        adapter.listFiles(listOf("p1", "p2"), name = "n", mimeType = "application/gzip", createdTimeAtLeast = 1000)
        val url = Url(server.requests.single().url)
        val p = url.parameters
        assertEquals(
            "('p1' in parents or 'p2' in parents) and createdTime > '1970-01-01T00:00:01.000Z' and name = 'n' and mimeType = 'application/gzip'",
            p["q"],
        )
        assertEquals("appDataFolder", p["spaces"])
        assertEquals("1000", p["pageSize"])
        assertEquals("nextPageToken, files(id, name, size, createdTime, parents)", p["fields"])
    }

    @Test
    fun listFilesEscapesQuotesAndBackslashes() = runBlocking {
        adapter.listFiles(name = "it's a\\b")
        assertEquals("name = 'it\\'s a\\\\b'", Url(server.requests.single().url).parameters["q"])
    }

    @Test
    fun listFilesFollowsAllPages() = runBlocking {
        server.pageSize = 3
        repeat(10) { server.putFile("f$it", root, byteArrayOf(1), it * 10L) }
        val files = adapter.listFiles(parentsIds = listOf(root))
        assertEquals(10, files.size)
        assertEquals(10, files.map { it.id }.toSet().size)
        assertEquals(4, server.requests.size)
    }

    @Test
    fun createdTimeBoundaryIsStrict() = runBlocking {
        server.putFile("at", root, byteArrayOf(1), 5000)
        val after = server.putFile("after", root, byteArrayOf(1), 5001)
        assertEquals(listOf(after), adapter.listFiles(parentsIds = listOf(root), createdTimeAtLeast = 5000).map { it.id })
    }

    @Test
    fun createdTimeFormatsMillisAndMapsBack() = runBlocking {
        val id = server.putFile("n", root, ByteArray(7), 1_700_000_123_045L)
        val f = adapter.get(id)
        assertEquals(1_700_000_123_045L, f.createdTime)
        assertEquals(7L, f.size)
        assertEquals(root, f.parentId)
        assertEquals("2023-11-14T22:15:23.045Z", formatRfc3339Millis(1_700_000_123_045L))
        assertEquals("1969-12-31T23:59:59.999Z", formatRfc3339Millis(-1))
    }

    @Test
    fun emptyQueryThrows() {
        assertFailsWith<IllegalArgumentException> { runBlocking { adapter.listFiles() } }
    }

    @Test
    fun getMissingThrowsFileNotFound() {
        assertFailsWith<FileNotFoundException> { runBlocking { adapter.get("nope") } }
    }

    @Test
    fun isSyncFolderKnownFalseAfterDelete() = runBlocking {
        val folder = adapter.createNewFolder("f")
        assertTrue(adapter.isSyncFolderKnown(unusedAccessor(), "f", folder.id))
        adapter.delete(folder.id)
        assertFalse(adapter.isSyncFolderKnown(unusedAccessor(), "f", folder.id))
    }

    @Test
    fun uploadOverOneChunkRoundTrips() = runBlocking {
        val payload = Random(1).nextBytes(9 * MiB)
        val file = tmp.resolve("up.gz").also { Files.write(it, payload) }
        val folder = adapter.createNewFolder("sync")
        val created = adapter.upload("data.gz", file.toOkioPath(), folder.id)
        assertEquals(payload.size.toLong(), created.size)
        assertEquals(folder.id, created.parentId)
        assertEquals("data.gz", created.name)
        assertEquals(GZIP_MIMETYPE, server.file(created.id)!!.mimeType)
        assertContentEquals(payload, downloadBytes(created.id))
        val starts = server.requests.filter { it.method == "POST" && Url(it.url).parameters["uploadType"] == "resumable" }
        assertEquals(1, starts.size)
        assertEquals(2, server.requests.count { it.method == "PUT" }) // 8 MiB + 1 MiB
    }

    @Test
    fun uploadEmptyFile() = runBlocking {
        val file = tmp.resolve("empty.gz").also { Files.write(it, ByteArray(0)) }
        val created = adapter.upload("e.gz", file.toOkioPath(), root)
        assertEquals(0L, created.size)
        assertEquals(0, downloadBytes(created.id).size)
    }

    @Test
    fun downloadReportsProgressPerRange() {
        val payload = Random(2).nextBytes(17 * MiB)
        val id = server.putFile("big", root, payload, 1)
        val progress = mutableListOf<Long>()
        assertContentEquals(payload, downloadBytes(id, progress))
        assertEquals(listOf(8L * MiB, 16L * MiB, 17L * MiB), progress)
    }

    @Test
    fun downloadExactMultipleOfChunkStopsWithoutExtraRequest() {
        val id = server.putFile("even", root, ByteArray(16 * MiB), 1)
        val progress = mutableListOf<Long>()
        downloadBytes(id, progress)
        assertEquals(listOf(8L * MiB, 16L * MiB), progress)
        assertEquals(2, server.requests.size)
    }

    @Test
    fun downloadEmptyFileAndMissingFile() {
        val id = server.putFile("empty", root, ByteArray(0), 1)
        assertEquals(0, downloadBytes(id).size)
        assertFailsWith<FileNotFoundException> { downloadBytes("nope") }
    }

    @Test
    fun cancelledDownloadStopsAtRangeBoundary() = runBlocking {
        val id = server.putFile("big", root, ByteArray(20 * MiB), 1)
        val buffer = Buffer()
        var closed = false
        val sink = object : ForwardingSink(buffer) {
            override fun close() { closed = true; super.close() }
        }
        lateinit var job: Job
        job = launch {
            adapter.download(id, sink) { job.cancel() }
        }
        var cause: Throwable? = null
        job.invokeOnCompletion { cause = it }
        job.join()
        assertTrue(job.isCancelled)
        assertTrue(cause is kotlinx.coroutines.CancellationException, "cause: $cause")
        assertTrue(closed)
        assertTrue(buffer.size < 20L * MiB, "wrote ${buffer.size} bytes")
        val mediaRequests = server.requests.count { Url(it.url).parameters["alt"] == "media" }
        assertTrue(mediaRequests <= 2, "media requests: $mediaRequests")
    }

    @Test
    fun expiredTokenRefreshedOnce() = runBlocking {
        val id = server.putFile("n", root, byteArrayOf(1), 1)
        server.authorize = { it == "Bearer t2" }
        assertEquals(id, adapter.get(id).id)
        assertEquals(listOf("t1"), auth.invalidateCalls)
        assertEquals(listOf("Bearer t1", "Bearer t2"), server.requests.map { it.headers["authorization"] })
    }

    @Test
    fun alwaysUnauthorizedThrowsAuthRequired() {
        server.authorize = { false }
        val e = assertFailsWith<CloudSyncUserFacingException> { runBlocking { adapter.get("x") } }
        assertTrue(e.requiresReconnect && e.cause is GoogleAuthRequiredException)
        assertEquals(2, server.requests.size)
        assertEquals(listOf("t1", "t2"), auth.invalidateCalls)
    }

    @Test
    fun serverErrorMapsToDriveHttpException() {
        // An unsupported Range makes the fake answer 400; a 500 needs a custom engine.
        val engine = io.ktor.client.engine.mock.MockEngine {
            respond500()
        }
        val a = GoogleDriveRestAdapter(auth, createGoogleDriveHttpClient(engine))
        val e = assertFailsWith<DriveHttpException> { runBlocking { a.get("x") } }
        assertEquals(500, e.status)
        assertEquals("backendError", e.reason)
    }

    private fun expireAuth() { auth.accessTokenFailure = GoogleAuthRequiredException("revoked") }

    private fun assertSignInNeeded(block: suspend () -> Unit) {
        val e = assertFailsWith<CloudSyncUserFacingException> { runBlocking { block() } }
        assertTrue(e.requiresReconnect)
        assertEquals("Sign in again", e.message)
        assertTrue(e.cause is GoogleAuthRequiredException)
    }

    private fun lostSignInAdapter() = GoogleDriveRestAdapter(auth, createGoogleDriveHttpClient(server.ktorEngine()), signInRequiredMessage = { "Sign in again" })

    @Test
    fun lostSignInSurfacesAsUserFacingReconnectOnEveryCall() {
        val a = lostSignInAdapter()
        val file = tmp.resolve("up.gz").also { Files.write(it, byteArrayOf(1, 2, 3)) }.toFile().toOkioPath()
        expireAuth()
        assertSignInNeeded { a.listFiles(listOf(root)) }
        assertSignInNeeded { a.get("x") }
        assertSignInNeeded { a.createNewFolder("f") }
        assertSignInNeeded { a.upload("n.gz", file, root) }
        assertSignInNeeded { a.download("x", Buffer()) }
        assertSignInNeeded { a.delete("x") }
    }

    @Test
    fun secondRejectedTokenSurfacesAsUserFacingReconnect() {
        val engine = io.ktor.client.engine.mock.MockEngine { respond("", io.ktor.http.HttpStatusCode.Unauthorized) }
        val a = GoogleDriveRestAdapter(auth, createGoogleDriveHttpClient(engine), signInRequiredMessage = { "Sign in again" })
        assertSignInNeeded { a.get("x") }
    }

    @Test
    fun lostSignInDefaultMessageIsNotBlank() {
        expireAuth()
        val e = assertFailsWith<CloudSyncUserFacingException> { runBlocking { adapter.listFiles(listOf(root)) } }
        assertTrue(e.requiresReconnect)
        assertTrue(!e.message.isNullOrBlank())
    }

    @Test
    fun signInCancelAndFailureKeepTheirSemantics() = runBlocking {
        val cancelling = GoogleDriveRestAdapter(FakeGoogleAuth(signedIn = false, cancelSignIn = true), createGoogleDriveHttpClient(server.ktorEngine()))
        assertFailsWith<CloudSyncUserCancelledException> { cancelling.signIn() }
        val failing = GoogleDriveRestAdapter(FakeGoogleAuth(signedIn = false, signInResult = false), createGoogleDriveHttpClient(server.ktorEngine()))
        assertFalse(failing.signIn())
    }

    @Test
    fun cancellationAndTransportErrorsAreNotRemapped() {
        auth.accessTokenFailure = kotlinx.coroutines.CancellationException("c")
        assertFailsWith<kotlinx.coroutines.CancellationException> { runBlocking { adapter.get("x") } }
        auth.accessTokenFailure = okio.IOException("offline")
        assertFailsWith<okio.IOException> { runBlocking { adapter.get("x") } }
    }

    @Test
    fun delegatesAuth() = runBlocking {
        assertTrue(adapter.signedIn)
        adapter.signOut()
        assertFalse(adapter.signedIn)
        assertEquals(1, auth.signOutCalls)
        assertTrue(adapter.signIn())
        assertTrue(adapter.signedIn)
    }

    private fun io.ktor.client.engine.mock.MockRequestHandleScope.respond500() = respond(
        content = """{"error":{"code":500,"message":"boom","errors":[{"reason":"backendError"}]}}""",
        status = io.ktor.http.HttpStatusCode.InternalServerError,
        headers = io.ktor.http.headersOf(io.ktor.http.HttpHeaders.ContentType, "application/json"),
    )

    /** The Drive adapter never touches the accessor, so an uninitialised instance stands in for it. */
    private fun unusedAccessor(): net.bible.service.cloudsync.SyncableDatabaseAccessor<*> {
        val f = sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }
        return (f.get(null) as sun.misc.Unsafe).allocateInstance(net.bible.service.cloudsync.SyncableDatabaseAccessor::class.java)
            as net.bible.service.cloudsync.SyncableDatabaseAccessor<*>
    }

    @Test
    fun uploadResumesFromServerReportedRange() = runBlocking {
        server.keepAtMostPerChunk = 3L * MiB
        val payload = Random(3).nextBytes(9 * MiB)
        val file = tmp.resolve("partial.gz").also { Files.write(it, payload) }
        val created = adapter.upload("p.gz", file.toOkioPath(), root)
        assertContentEquals(payload, downloadBytes(created.id))
        // 8 MiB chunk keeps 3, then 8 MiB re-sent from 3 keeps 3, ... : more than the 2 PUTs of a lossless upload.
        assertTrue(server.requests.count { it.method == "PUT" } > 2)
        val starts = server.requests.filter { it.method == "PUT" }.mapNotNull { it.headers["content-range"] }
        assertTrue(starts.any { it.startsWith("bytes ${3 * MiB}-") }, starts.toString())
    }

    @Test
    fun uploadOfExactlyOneChunk() = runBlocking {
        val payload = Random(4).nextBytes(8 * MiB)
        val file = tmp.resolve("exact.gz").also { Files.write(it, payload) }
        val created = adapter.upload("x.gz", file.toOkioPath(), root)
        assertContentEquals(payload, downloadBytes(created.id))
        assertEquals(1, server.requests.count { it.method == "PUT" })
    }

    @Test
    fun uploadRetriesChunkAfterUnauthorized() = runBlocking {
        server.rejectNextChunkUnauthorized = true
        val payload = Random(5).nextBytes(9 * MiB)
        val file = tmp.resolve("auth.gz").also { Files.write(it, payload) }
        val created = adapter.upload("a.gz", file.toOkioPath(), root)
        assertEquals(listOf("t1"), auth.invalidateCalls)
        assertContentEquals(payload, downloadBytes(created.id))
    }

    @Test
    fun downloadRejectsWholeBodyAtNonZeroOffset() {
        val chunk = ByteArray(DRIVE_DOWNLOAD_CHUNK_SIZE.toInt())
        val engine = io.ktor.client.engine.mock.MockEngine { request ->
            val range = request.headers[io.ktor.http.HttpHeaders.Range]!!
            if (range.startsWith("bytes=0-")) {
                respond(
                    chunk, io.ktor.http.HttpStatusCode.PartialContent,
                    io.ktor.http.headersOf(io.ktor.http.HttpHeaders.ContentRange, "bytes 0-${chunk.size - 1}/${3L * chunk.size}"),
                )
            } else {
                respond(ByteArray(3 * chunk.size), io.ktor.http.HttpStatusCode.OK) // ignores Range
            }
        }
        val a = GoogleDriveRestAdapter(auth, createGoogleDriveHttpClient(engine))
        val buffer = Buffer()
        assertFailsWith<okio.IOException> { runBlocking { a.download("x", buffer) } }
        assertEquals(chunk.size.toLong(), buffer.size)
    }

    @Test
    fun downloadClosesSinkWhenCancelledBeforeStart() = runBlocking {
        val id = server.putFile("f", root, ByteArray(10), 1)
        var closed = false
        val sink = object : ForwardingSink(Buffer()) {
            override fun close() { closed = true; super.close() }
        }
        val job = launch(kotlinx.coroutines.Dispatchers.Default, start = kotlinx.coroutines.CoroutineStart.ATOMIC) {
            adapter.download(id, sink)
        }
        job.cancel()
        job.join()
        assertTrue(job.isCancelled)
        assertTrue(closed, "sink must be closed even when cancelled before the download started")
    }

    private fun uploadType(r: net.bible.service.cloudsync.drive.fake.FakeDriveRequest) = Url(r.url).parameters["uploadType"]

    @Test
    fun smallUploadIsOneMultipartRequestStoringWhatResumableStores() = runBlocking {
        val folder = adapter.createNewFolder("sync")
        val small = Random(10).nextBytes(3 * 1024)
        val before = server.requests.size
        val created = adapter.upload("1.12.sqlite3.gz", tmp.resolve("small.gz").also { Files.write(it, small) }.toOkioPath(), folder.id)
        val sent = server.requests.drop(before)
        assertEquals(1, sent.size, "one request for a small file")
        assertEquals("POST", sent.single().method)
        assertEquals("multipart", uploadType(sent.single()))
        assertEquals("id, name, size, createdTime, parents", Url(sent.single().url).parameters["fields"])

        val big = Random(11).nextBytes(DRIVE_MULTIPART_MAX_BYTES.toInt())
        val viaResumable = adapter.upload("1.12.sqlite3.gz", tmp.resolve("big.gz").also { Files.write(it, big) }.toOkioPath(), folder.id)
        val m = server.file(created.id)!!
        val r = server.file(viaResumable.id)!!
        assertEquals(r.name, m.name)
        assertEquals(r.mimeType, m.mimeType)
        assertEquals(GZIP_MIMETYPE, m.mimeType)
        assertEquals(r.parents, m.parents)
        assertContentEquals(small, m.content)
        assertEquals(small.size.toLong(), created.size)
        assertEquals(folder.id, created.parentId)
        assertEquals("1.12.sqlite3.gz", created.name)
        assertContentEquals(small, downloadBytes(created.id))
    }

    @Test
    fun multipartThresholdPicksTheUploadProtocol() = runBlocking {
        for ((size, expected) in listOf(DRIVE_MULTIPART_MAX_BYTES - 1 to "multipart", DRIVE_MULTIPART_MAX_BYTES to "resumable")) {
            val payload = Random(size.toInt()).nextBytes(size.toInt())
            val before = server.requests.size
            val created = adapter.upload("t.gz", tmp.resolve("t$size.gz").also { Files.write(it, payload) }.toOkioPath(), root)
            val posts = server.requests.drop(before).filter { it.method == "POST" }
            assertEquals(listOf<String?>(expected), posts.map { uploadType(it) }, "size $size")
            assertContentEquals(payload, server.file(created.id)!!.content, "size $size")
        }
    }

    @Test
    fun multipartUploadRefreshesExpiredTokenOnce() = runBlocking {
        server.authorize = { it == "Bearer t2" }
        val payload = Random(12).nextBytes(1000)
        val created = adapter.upload("a.gz", tmp.resolve("a.gz").also { Files.write(it, payload) }.toOkioPath(), root)
        assertEquals(listOf("t1"), auth.invalidateCalls)
        assertEquals(listOf("multipart", "multipart"), server.requests.map { uploadType(it) })
        assertContentEquals(payload, server.file(created.id)!!.content)
        assertEquals(created.id, adapter.listFiles(parentsIds = listOf(root)).single().id, "exactly one file stored")
    }

    @Test
    fun cancelledMultipartUploadLeavesNoFile() = runBlocking {
        val started = kotlinx.coroutines.CompletableDeferred<Unit>()
        val engine = io.ktor.client.engine.mock.MockEngine { request ->
            // The whole body is built before the request goes out; Drive stores nothing until it has all of it.
            started.complete(Unit)
            kotlinx.coroutines.awaitCancellation()
        }
        val a = GoogleDriveRestAdapter(auth, createGoogleDriveHttpClient(engine))
        val file = tmp.resolve("c.gz").also { Files.write(it, ByteArray(1000)) }.toOkioPath()
        val job = launch(kotlinx.coroutines.Dispatchers.Default) { a.upload("c.gz", file, root) }
        started.await()
        job.cancel()
        job.join()
        assertTrue(job.isCancelled)
        assertTrue(adapter.listFiles(parentsIds = listOf(root)).isEmpty())
    }

    @Test
    fun multipartBoundaryNeverOccursInContent() {
        val content = "--andbible_".encodeToByteArray() + ByteArray(100)
        repeat(50) { seed ->
            val b = multipartBoundary(content, Random(seed))
            assertFalse(String(content, Charsets.ISO_8859_1).contains(b))
        }
        // A content that contains the first candidate forces a second one.
        val first = multipartBoundary(ByteArray(0), Random(7))
        val second = multipartBoundary(first.encodeToByteArray(), Random(7))
        assertTrue(first != second)
    }
}

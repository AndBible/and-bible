package net.bible.service.cloudsync.drive

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.appendPathSegments
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.bible.android.database.SyncConfiguration
import net.bible.service.cloudsync.CloudAdapter
import net.bible.service.cloudsync.GZIP_MIMETYPE
import net.bible.service.cloudsync.SyncableDatabaseAccessor
import net.bible.sharedcore.platform.cloud.CloudFile
import net.bible.sharedcore.platform.cloud.DownloadProgressListener
import okio.FileNotFoundException
import okio.FileSystem
import okio.IOException
import okio.Path
import okio.BufferedSink
import okio.Sink
import okio.buffer
import okio.use

private const val FILES_PATH = "drive/v3/files"
private const val UPLOAD_FILES_PATH = "upload/drive/v3/files"

/** Size of one `Content-Range` chunk of a resumable upload (a multiple of 256 KiB, as Drive requires). */
private const val UPLOAD_CHUNK_SIZE = 8L * 1024 * 1024

private val driveJson = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = false }

/**
 * [CloudAdapter] over the Google Drive v3 REST API, restricted to the app data space ([DRIVE_APPDATA_SPACE]).
 *
 * Mirrors the wire semantics of the Android Google API client adapter: the same `files.list` query,
 * page size and fields, a resumable upload and 8 MiB ranged downloads. Authentication is delegated to
 * [auth], which only ever hands out access tokens.
 */
class GoogleDriveRestAdapter(
    private val auth: GoogleAuth,
    private val http: HttpClient,
    private val baseUrl: String = DRIVE_BASE_URL,
) : CloudAdapter {
    override val signedIn: Boolean get() = auth.signedIn

    override suspend fun signIn(): Boolean = auth.signIn()

    override suspend fun signOut() = auth.signOut()

    override suspend fun get(id: String): CloudFile {
        val response = send(HttpMethod.Get) {
            url { appendPathSegments(FILES_PATH.split('/') + id) }
            parameter("fields", DRIVE_FILE_FIELDS)
        }
        return decode<DriveFileDto>(response, id).toCloudFile()
    }

    override suspend fun listFiles(
        parentsIds: List<String>?,
        name: String?,
        mimeType: String?,
        createdTimeAtLeast: Long?,
    ): List<CloudFile> {
        val clauses = buildList {
            if (parentsIds != null) add(parentsIds.joinToString(" or ", "(", ")") { "'${it.escapeQuery()}' in parents" })
            if (createdTimeAtLeast != null) add("createdTime > '${formatRfc3339Millis(createdTimeAtLeast)}'")
            if (name != null) add("name = '${name.escapeQuery()}'")
            if (mimeType != null) add("mimeType = '${mimeType.escapeQuery()}'")
        }
        if (clauses.isEmpty()) throw IllegalArgumentException("Illegal query")
        val q = clauses.joinToString(" and ")
        val result = ArrayList<CloudFile>()
        var pageToken: String? = null
        do {
            val response = send(HttpMethod.Get) {
                url { appendPathSegments(FILES_PATH.split('/')) }
                parameter("spaces", DRIVE_APPDATA_SPACE)
                parameter("q", q)
                parameter("pageSize", DRIVE_PAGE_SIZE)
                parameter("fields", "nextPageToken, files($DRIVE_FILE_FIELDS)")
                pageToken?.let { parameter("pageToken", it) }
            }
            val page = decode<DriveFileListDto>(response)
            page.files.mapTo(result) { it.toCloudFile() }
            pageToken = page.nextPageToken
        } while (pageToken != null)
        return result
    }

    override suspend fun getFolders(parentId: String): List<CloudFile> =
        listFiles(parentsIds = listOf(parentId), mimeType = DRIVE_FOLDER_MIMETYPE)

    override suspend fun createNewFolder(name: String, parentId: String?): CloudFile {
        val metadata = DriveCreateDto(name, listOf(parentId ?: DRIVE_APPDATA_SPACE), DRIVE_FOLDER_MIMETYPE)
        val response = send(HttpMethod.Post) {
            url { appendPathSegments(FILES_PATH.split('/')) }
            parameter("fields", DRIVE_FILE_FIELDS)
            contentType(ContentType.Application.Json)
            setBody(driveJson.encodeToString(metadata).encodeToByteArray())
        }
        return decode<DriveFileDto>(response).toCloudFile()
    }

    override suspend fun upload(name: String, file: Path, parentId: String): CloudFile = withContext(Dispatchers.IO) {
        val fs = FileSystem.SYSTEM
        val total = fs.metadata(file).size ?: throw IOException("Cannot determine size of $file")
        val metadata = DriveCreateDto(name, listOf(parentId))
        val start = send(HttpMethod.Post) {
            url { appendPathSegments(UPLOAD_FILES_PATH.split('/')) }
            parameter("uploadType", "resumable")
            parameter("fields", DRIVE_FILE_FIELDS)
            header("X-Upload-Content-Type", GZIP_MIMETYPE)
            header("X-Upload-Content-Length", total.toString())
            contentType(ContentType.Application.Json)
            setBody(driveJson.encodeToString(metadata).encodeToByteArray())
        }
        ensureSuccess(start)
        val sessionUrl = start.headers[HttpHeaders.Location] ?: throw IOException("Drive did not return an upload session URL")

        fs.openReadOnly(file).use { handle ->
            var offset = 0L
            var stalled = 0
            while (true) {
                currentCoroutineContext().ensureActive()
                val size = minOf(UPLOAD_CHUNK_SIZE, total - offset)
                // Re-read at the confirmed offset: Drive may have kept less than the whole chunk.
                val chunk = handle.source(offset).buffer().use { it.readByteArray(size) }
                val range = if (total == 0L) "bytes */0" else "bytes $offset-${offset + size - 1}/$total"
                val response = send(HttpMethod.Put) {
                    url(sessionUrl)
                    header(HttpHeaders.ContentRange, range)
                    contentType(ContentType.parse(GZIP_MIMETYPE))
                    setBody(chunk)
                }
                if (response.status.value != 308) return@withContext decode<DriveFileDto>(response).toCloudFile()
                // "Range: bytes=0-N" = Drive holds N+1 bytes; no Range header = it holds none.
                val persisted = response.headers[HttpHeaders.Range]?.substringAfterLast('-')?.toLongOrNull()?.plus(1) ?: 0L
                if (persisted > offset + size) throw IOException("Drive reports $persisted bytes received, more than the $total sent so far")
                if (persisted >= total) throw IOException("Drive upload session did not complete after all bytes were sent")
                stalled = if (persisted <= offset) stalled + 1 else 0
                if (stalled >= 3) throw IOException("Drive upload made no progress at offset $offset")
                offset = persisted
            }
        }
        error("unreachable: the upload loop returns or throws")
    }

    override suspend fun download(id: String, sink: Sink, onProgress: DownloadProgressListener?) {
        // The sink is closed here, outside withContext: a coroutine cancelled before the block starts never enters it.
        val out = sink.buffer()
        try {
            withContext(Dispatchers.IO) { downloadRanges(id, out, onProgress) }
        } finally {
            out.close()
        }
    }

    private suspend fun downloadRanges(id: String, out: BufferedSink, onProgress: DownloadProgressListener?) {
        var offset = 0L
        while (true) {
            currentCoroutineContext().ensureActive()
            val response = send(HttpMethod.Get) {
                url { appendPathSegments(FILES_PATH.split('/') + id) }
                parameter("alt", "media")
                header(HttpHeaders.Range, "bytes=$offset-${offset + DRIVE_DOWNLOAD_CHUNK_SIZE - 1}")
            }
            if (response.status.value == 416 && offset == 0L) return // empty file: nothing to range over
            if (response.status.value == 404) throw FileNotFoundException("Drive file not found: $id")
            ensureSuccess(response)
            val whole = response.status == HttpStatusCode.OK // server ignored Range and sent everything
            if (whole && offset != 0L) throw IOException("Drive ignored the Range header of a continuation request at offset $offset")
            val bytes = response.readRawBytes()
            out.write(bytes)
            out.flush()
            offset += bytes.size
            onProgress?.invoke(offset)
            val total = response.headers[HttpHeaders.ContentRange]?.substringAfter('/')?.toLongOrNull()
            if (whole || bytes.isEmpty() || (total != null && offset >= total) || (total == null && bytes.size < DRIVE_DOWNLOAD_CHUNK_SIZE)) return
        }
    }

    override suspend fun delete(id: String) {
        val response = send(HttpMethod.Delete) {
            url { appendPathSegments(FILES_PATH.split('/') + id) }
        }
        ensureSuccess(response)
    }

    override suspend fun isSyncFolderKnown(dbDef: SyncableDatabaseAccessor<*>, name: String, id: String): Boolean =
        try {
            get(id)
            true
        } catch (e: FileNotFoundException) {
            false
        }

    override suspend fun makeSyncFolderKnown(dbDef: SyncableDatabaseAccessor<*>, name: String, id: String) {}

    override suspend fun getConfigs(dbDef: SyncableDatabaseAccessor<*>): List<SyncConfiguration> = emptyList()

    // ---- plumbing ----

    /**
     * Sends an authorised request relative to [baseUrl]. A 401 invalidates the token and retries once with a fresh
     * one; a second 401 means only an interactive sign-in can help. Every other status is returned for the caller
     * to map (including 308, "resume incomplete", of a resumable upload).
     */
    private suspend fun send(
        method: HttpMethod,
        block: HttpRequestBuilder.() -> Unit,
    ): HttpResponse {
        var attempt = 0
        while (true) {
            val token = auth.accessToken()
            val response = http.request {
                this.method = method
                url(baseUrl)
                block()
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            if (response.status.value != 401) return response
            auth.invalidate(token)
            if (++attempt >= 2) throw GoogleAuthRequiredException("Google Drive rejected the access token twice")
        }
    }

    private suspend fun ensureSuccess(response: HttpResponse) {
        if (response.status.isSuccess()) return
        throw toException(response)
    }

    private suspend inline fun <reified T> decode(response: HttpResponse, missingId: String? = null): T {
        if (response.status.value == 404 && missingId != null) throw FileNotFoundException("Drive file not found: $missingId")
        ensureSuccess(response)
        return try {
            driveJson.decodeFromString<T>(response.bodyAsText())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw IOException("Unexpected Drive response: ${e.message}", e)
        }
    }

    private suspend fun toException(response: HttpResponse): DriveHttpException {
        val body = try { response.bodyAsText() } catch (e: Exception) { "" }
        val error = try { driveJson.decodeFromString<DriveErrorEnvelope>(body).error } catch (e: Exception) { null }
        val reason = error?.errors?.firstOrNull()?.reason
        val message = error?.message ?: response.status.description
        return DriveHttpException(response.status.value, reason, "Google Drive HTTP ${response.status.value}: $message")
    }
}

/** Escapes a value for use inside a single-quoted Drive query string. */
private fun String.escapeQuery() = replace("\\", "\\\\").replace("'", "\\'")

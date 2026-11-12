package net.bible.service.cloudsync.drive

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import okio.IOException

/** Root of the Google APIs; Drive v3 paths are appended to it. */
const val DRIVE_BASE_URL = "https://www.googleapis.com"

/** The only OAuth scope the adapter requests: the hidden per-app folder, never the user's whole Drive. */
const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"

/** Drive space holding the app-private folder that [DRIVE_APPDATA_SCOPE] grants access to. */
const val DRIVE_APPDATA_SPACE = "appDataFolder"

/** MIME type Drive uses for folders. */
const val DRIVE_FOLDER_MIMETYPE = "application/vnd.google-apps.folder"

/** Fields requested from `files.list` / `files.get`, so responses stay small. */
const val DRIVE_FILE_FIELDS = "id, name, size, createdTime, parents"

/** Maximum `pageSize` accepted by `files.list`. */
const val DRIVE_PAGE_SIZE = 1000

/** Byte range size for ranged downloads of large files. */
const val DRIVE_DOWNLOAD_CHUNK_SIZE = 8L * 1024 * 1024

/** Connect timeout for Drive requests. */
const val DRIVE_CONNECT_TIMEOUT_MS = 60_000L

/** Socket (inactivity) timeout for Drive requests. The overall request timeout is unlimited, since uploads can be large. */
const val DRIVE_SOCKET_TIMEOUT_MS = 120_000L

/**
 * Creates the HTTP client used by the Drive adapter on top of the given [engine].
 *
 * Non-2xx responses are not thrown by Ktor (`expectSuccess = false`); callers inspect the status and
 * raise [DriveHttpException] themselves, so error bodies and retry decisions stay in one place.
 */
fun createGoogleDriveHttpClient(engine: HttpClientEngine): HttpClient = HttpClient(engine) {
    expectSuccess = false
    install(HttpTimeout) {
        connectTimeoutMillis = DRIVE_CONNECT_TIMEOUT_MS
        socketTimeoutMillis = DRIVE_SOCKET_TIMEOUT_MS
        // null = no request-level limit (Ktor's infinite timeout); uploads can be large.
        requestTimeoutMillis = null
    }
}

/**
 * A Drive API call that returned a non-2xx status.
 *
 * @property status HTTP status code.
 * @property reason Google's machine-readable error reason (for example `rateLimitExceeded`), when present.
 */
class DriveHttpException(val status: Int, val reason: String?, message: String) : IOException(message)

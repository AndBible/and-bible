package net.bible.service.cloudsync.webdav

object WebDavPrefKeys {
    const val SERVER_URL = "webdav_sync_server_url"
    const val USERNAME = "webdav_sync_username"
    const val PASSWORD = "webdav_sync_password"
    const val FOLDER_PATH = "webdav_sync_folder_path"
    const val CERT_PIN = "webdav_sync_cert_pin"
    const val PROPAGATION = "webdav_sync_propagation"
}

data class WebDavConfig(val serverUrl: String, val username: String, val password: String, val folderPath: String) {
    val host: String get() = serverUrl.substringAfter("://").substringBefore('/').substringBefore(':')
    /** Sync root under the WebDAV root, as a [net.bible.sharedcore.webdav.DavPath] path ("" = root). */
    val baseFolder: String get() = folderPath.trim().split('/').filter { it.isNotBlank() }.joinToString("/")

    companion object
}

/** Whether the server bumps a collection's getlastmodified when a child is created (see Calibration.kt). */
enum class Propagation { UNKNOWN, YES, NO }

data class CertPin(val host: String, val sha256: String) {
    fun encode() = "$host|$sha256"
    companion object {
        fun decode(s: String?): CertPin? = s?.split('|')?.takeIf { it.size == 2 }?.let { CertPin(it[0], it[1]) }
    }
}

/** The adapter's persistent, mutable WebDAV state. An interface so tests use an in-memory one. */
interface WebDavStateStore {
    var propagation: Propagation
    var certPin: CertPin?
}

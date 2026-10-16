package net.bible.sharedcore.webdav

/**
 * WebDAV failures, split by what the sync layer must do with them. Only [DavTransientException]
 * is an IOException: the app's sync code treats any IOException as "network blip, retry quietly
 * next sync". Everything else needs the user (credentials, certificate, quota) or is a bug.
 */
class DavTransientException(message: String?, cause: Throwable? = null) : kotlinx.io.IOException(message, cause)

open class DavException(message: String?, cause: Throwable? = null) : Exception(message, cause)
class DavNotFoundException(val path: String) : DavException("Not found: $path")
class DavAuthException(val status: Int) : DavException("Authentication failed (HTTP $status)")
class DavQuotaException(val path: String) : DavException("Insufficient storage: $path")
class DavProtocolException(val status: Int, message: String, cause: Throwable? = null) : DavException(message, cause)
class DavUntrustedCertificateException(val certificate: DavCertificateInfo) :
    DavException("Untrusted certificate for ${certificate.host}")

/** What the user sees before trusting a certificate. [sha256] is uppercase hex without separators. */
data class DavCertificateInfo(
    val host: String,
    val sha256: String,
    val subject: String,
    val issuer: String,
    val notBefore: Long,
    val notAfter: Long,
)

/** Maps a non-success HTTP status to the exception the client throws; null for success. */
internal fun davStatusException(status: Int, path: String): Throwable? = when {
    status in 200..299 -> null
    status == 404 -> DavNotFoundException(path)
    status == 401 || status == 403 -> DavAuthException(status)
    status == 507 -> DavQuotaException(path)
    status == 408 || status == 429 || status in 500..599 -> DavTransientException("HTTP $status for $path")
    else -> DavProtocolException(status, "HTTP $status for $path")
}

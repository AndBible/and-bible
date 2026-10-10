package net.bible.sharedcore.webdav

/** One resource from a PROPFIND. Times are **server clock** epoch ms; null when the server omitted them. */
data class DavResource(
    val path: String,
    val isCollection: Boolean,
    val contentLength: Long,
    val lastModified: Long?,
    val creationDate: Long?,
    val etag: String?,
) {
    val name: String get() = path.substringAfterLast('/')
    val parent: String get() = path.substringBeforeLast('/', "")
}

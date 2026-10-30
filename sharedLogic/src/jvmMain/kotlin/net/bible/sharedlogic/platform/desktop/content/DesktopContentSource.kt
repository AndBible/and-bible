package net.bible.sharedlogic.platform.desktop.content

import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import net.bible.service.sword.ContentSource
import okio.Source
import okio.source

class DesktopContentSource(private val probe: (Path) -> String? = { Files.probeContentType(it) }) : ContentSource {
    private fun path(uri: String): Path? = try {
        val parsed = URI(uri)
        when {
            parsed.scheme == null -> Path.of(uri)
            parsed.scheme == "file" -> Path.of(parsed)
            else -> null
        }
    } catch (_: Exception) { null }
    override fun openSource(uri: String): Source? = try { path(uri)?.let { Files.newInputStream(it).source() } } catch (_: Exception) { null }
    override fun displayName(uri: String): String? = path(uri)?.fileName?.toString()?.takeIf { it.isNotEmpty() }
    override fun mimeType(uri: String): String? {
        val path = path(uri)?.takeIf { it.fileName != null } ?: return null
        val probed = try { probe(path) } catch (_: Exception) { null }
        return probed ?: types[path.fileName.toString().substringAfterLast('.', "").lowercase()]
    }
    private val types = mapOf(
        "json" to "application/json", "xml" to "application/xml", "txt" to "text/plain", "html" to "text/html",
        "htm" to "text/html", "csv" to "text/csv", "md" to "text/markdown", "zip" to "application/zip",
        "epub" to "application/epub+zip", "pdf" to "application/pdf", "png" to "image/png", "jpg" to "image/jpeg",
        "jpeg" to "image/jpeg", "gif" to "image/gif", "db" to "application/octet-stream", "sqlite" to "application/vnd.sqlite3",
        "mp3" to "audio/mpeg", "wav" to "audio/wav", "m4a" to "audio/mp4", "mp4" to "video/mp4",
        "conf" to "text/plain", "properties" to "text/plain",
    )
}

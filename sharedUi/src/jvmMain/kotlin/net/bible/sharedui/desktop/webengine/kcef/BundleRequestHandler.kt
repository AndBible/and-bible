package net.bible.sharedui.desktop.webengine.kcef

import net.bible.sharedui.desktop.webengine.BundleResource
import net.bible.sharedui.desktop.webengine.DesktopPageAssets
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

/**
 * The origin the BibleView bundle is served from. A real `https` origin (not `file://`) gives the page a secure,
 * same-origin context for its module scripts; nothing at this host is ever fetched from the network.
 */
const val BUNDLE_ORIGIN = "https://bibleview.andbible.local"

/**
 * Whether [url] is at [BUNDLE_ORIGIN] exactly (path, query or fragment may follow; a look-alike host such as
 * `bibleview.andbible.local.evil.org` or another scheme/port is not). The pane trusts bridge messages and allows
 * navigation only for documents at this origin.
 */
fun isAtBundleOrigin(url: String?): Boolean {
    if (url == null || !url.startsWith(BUNDLE_ORIGIN)) return false
    return url.length == BUNDLE_ORIGIN.length || url[BUNDLE_ORIGIN.length] in "/?#"
}

/**
 * Maps requests at [BUNDLE_ORIGIN] to files under [bundleDir] (pure logic, no CEF types).
 *
 * Document-start scripts: [body] inserts [documentStartScripts] as one inline `<script>` at the start of every
 * served HTML document's `<head>`, so they run before any script of the page (module scripts, inline scripts),
 * whatever the engine. This is how the KCEF engine meets the [net.bible.sharedui.desktop.webengine.DesktopWebEngine]
 * ordering contract: the scripts are part of the document itself, not raced against it from the engine side.
 */
class BundleRequestHandler(
    bundleDir: Path,
    private val documentStartScripts: List<String> = emptyList(),
    /** Asked for a path no bundle file serves (module files, background images, ...); a bundle file always wins. */
    private val pageAssets: DesktopPageAssets = DesktopPageAssets.None,
) {
    private val root: Path = bundleDir.toAbsolutePath().normalize()

    /** Whether [url] is at [BUNDLE_ORIGIN] (so it must never reach the network, even when [resolve] refuses it). */
    fun isBundleUrl(url: String): Boolean = isAtBundleOrigin(url)

    /** The file for [url]: a bundle file, else a page asset; null for another origin, a path escaping [bundleDir] or a missing file. */
    fun resolve(url: String): BundleResource? {
        if (!url.startsWith("$BUNDLE_ORIGIN/")) return null
        val rawPath = url.substring(BUNDLE_ORIGIN.length).substringBefore('#').substringBefore('?')
        // Refuse traversal in the raw form as well as after decoding: the engine normalises `..` itself, so a
        // request that still carries one was not produced by a page of ours.
        val segments = rawPath.split('/')
        if (segments.any { it == ".." || it == "." }) return null
        val decoded = try {
            URI(BUNDLE_ORIGIN + rawPath).path ?: return null
        } catch (_: Exception) {
            return null
        }
        val relative = decoded.trimStart('/').ifEmpty { "index.html" }
        if (relative.split('/').any { it == ".." || it == "." } || relative.contains('\\') || relative.contains('\u0000')) return null
        val file = root.resolve(relative).normalize()
        if (file.startsWith(root) && file != root && Files.isRegularFile(file)) {
            return BundleResource(file, mimeTypeFor(file.fileName.toString()))
        }
        // Not a bundle file: a page asset route (its resolver does its own traversal checks on the decoded path).
        return try {
            pageAssets.resolve(decoded)
        } catch (_: Exception) {
            null
        }
    }

    /** The bytes served for [resource]; HTML documents get the document-start scripts injected. */
    fun body(resource: BundleResource): ByteArray {
        val bytes = Files.readAllBytes(resource.file)
        if (!resource.fromBundle || resource.mimeType != "text/html" || documentStartScripts.isEmpty()) return bytes
        return injectDocumentStartScripts(bytes.decodeToString(), documentStartScripts).encodeToByteArray()
    }

    companion object {
        private val MIME_TYPES = mapOf(
            "html" to "text/html", "htm" to "text/html",
            "js" to "text/javascript", "mjs" to "text/javascript",
            "css" to "text/css",
            "json" to "application/json", "map" to "application/json",
            "svg" to "image/svg+xml", "png" to "image/png", "jpg" to "image/jpeg", "jpeg" to "image/jpeg",
            "gif" to "image/gif", "webp" to "image/webp", "ico" to "image/x-icon",
            "woff" to "font/woff", "woff2" to "font/woff2", "ttf" to "font/ttf", "otf" to "font/otf",
            "wasm" to "application/wasm", "txt" to "text/plain",
        )

        fun mimeTypeFor(fileName: String): String =
            MIME_TYPES[fileName.substringAfterLast('.', "").lowercase()] ?: "application/octet-stream"

        private val HEAD_OPEN = Regex("<head(\\s[^>]*)?>", RegexOption.IGNORE_CASE)
        private val HTML_OPEN = Regex("<html(\\s[^>]*)?>", RegexOption.IGNORE_CASE)

        /**
         * Returns [html] with [scripts] (in order, one inline `<script>`) right after its `<head>` tag, else after
         * `<html>`, else at the very start. Never before the doctype, which would switch the page to quirks mode.
         */
        fun injectDocumentStartScripts(html: String, scripts: List<String>): String {
            if (scripts.isEmpty()) return html
            // `</script` inside a script would end the element early; `<\/script` is the same string to JS.
            val body = scripts.joinToString("\n") { it.replace("</script", "<\\/script", ignoreCase = true) }
            val tag = "<script>\n$body\n</script>"
            val at = (HEAD_OPEN.find(html) ?: HTML_OPEN.find(html))?.range?.last?.plus(1) ?: 0
            return html.substring(0, at) + tag + html.substring(at)
        }
    }
}

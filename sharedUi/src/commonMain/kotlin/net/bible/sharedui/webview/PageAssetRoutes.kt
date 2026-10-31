package net.bible.sharedui.webview

import kotlinx.atomicfu.atomic
import okio.FileSystem
import okio.Path
import okio.Source

/** One answer of [PageAssetRoutes]: a file to stream, an in-memory body, or an open stream the caller closes. */
sealed interface PageAsset {
    val mimeType: String

    /** A file on disk (module files, background images). */
    data class File(val path: Path, override val mimeType: String) : PageAsset

    /** A generated body (the empty `fonts.css`, a cleaned EPUB stylesheet). */
    class Bytes(val bytes: ByteArray, override val mimeType: String) : PageAsset

    /** An EPUB resource stream; the caller owns and must close [source] (the backend waits for it before retiring). */
    class Stream(val source: Source, override val mimeType: String) : PageAsset
}

/** Where `/epub/` and `/module-style/epub/` read EPUB content from (Android: `EpubBackend` of the pane's book). */
interface PageAssetEpubSource {
    /** `/epub/<path>`: a resource of the pane's current EPUB, or null when the pane shows no EPUB or it is missing. */
    fun openResource(relativePath: String): Source?

    /** `/module-style/epub/<initials>/<path>`: a resource of the installed EPUB [initials]. */
    fun openBookResource(initials: String, relativePath: String): Source?

    /** `/module-style/epub/<initials>/<key>/style.css`: the stylesheets of that EPUB page joined, or null. Called off the main thread. */
    fun styleSheet(initials: String, key: String): String?
}

private val MIME = mapOf(
    "css" to "text/css", "js" to "text/javascript", "mjs" to "text/javascript", "html" to "text/html",
    "htm" to "text/html", "xhtml" to "application/xhtml+xml", "json" to "application/json", "svg" to "image/svg+xml",
    "png" to "image/png", "jpg" to "image/jpeg", "jpeg" to "image/jpeg", "gif" to "image/gif", "webp" to "image/webp",
    "bmp" to "image/bmp", "ico" to "image/x-icon", "woff" to "font/woff", "woff2" to "font/woff2", "ttf" to "font/ttf",
    "otf" to "font/otf", "txt" to "text/plain", "xml" to "application/xml", "wasm" to "application/wasm",
    "map" to "application/json",
)

/** The MIME type for a file name (by extension; `application/octet-stream` when unknown). */
fun pageAssetMimeType(name: String): String =
    MIME[name.substringAfterLast('/').substringAfterLast('.', "").lowercase()] ?: "application/octet-stream"

/** Android `ModuleStylesAssetHandler`: EPUB stylesheets lose their colours, `body` rules and line heights. */
private val epubColorRe = Regex("""\b(background-color|background|background-image|color):[^;]+;""")
private val epubBodyRe = Regex("""\bbody\s*\{[^}]*\}""")
private val epubLineHeightRe = Regex("""\bline-height:[^;]*;""")

/**
 * The BibleView page asset routes (Android `BibleView`'s `WebViewAssetLoader` path handlers), shared by the desktop
 * engines and the iOS `WKURLSchemeHandler`:
 *
 * - `/background/<module>`: the add-on background image of that module ([backgrounds]).
 * - `/module/<initials>/<path>`: a file under the installed book's location ([bookLocation]).
 * - `/fonts/<initials>/<path>`: a file under the book's location. `fonts.css` (Android's add-on font list, an
 *   Android-only catalog) is an empty stylesheet for an installed book, so the page's link succeeds; no add-on fonts.
 * - `/features/<initials>/<path>`: as `/module/`, but only with a valid `<file>.sign` ([verify]); without a verifier
 *   nothing is served.
 * - `/module-style/<initials>/<path>`: the book's `AndBibleCSS` stylesheet (`style.css`) or a file beside it.
 * - `/module-style/epub/<initials>/<key>/style.css` and `/module-style/epub/<initials>/<path>`, `/epub/<path>`: EPUB
 *   stylesheets and resources through [epub]; with no [epub] source these are a 404 logged once per route.
 *
 * [resolve] takes the decoded path. Every relative part must stay inside its base: segments `..` or `.`, empty
 * segments, backslashes, NULs and absolute paths are refused, and a file reached through a symlink must still be inside
 * the book. Unknown module, missing file or unknown route: null (404).
 */
class PageAssetRoutes(
    private val backgrounds: () -> Map<String, Path>,
    private val bookLocation: (String) -> Path?,
    private val bookStyleFile: (String) -> String?,
    private val verify: ((file: Path, signature: Path) -> Boolean)?,
    private val epub: PageAssetEpubSource? = null,
    private val log: (String) -> Unit = {},
    private val mimeType: (String) -> String = ::pageAssetMimeType,
    private val fs: FileSystem = FileSystem.SYSTEM,
) {
    private val epubLogged = atomic(false)
    private val epubStyleLogged = atomic(false)

    /** The asset for the decoded, origin-relative [path] (leading `/`, no query), or null for a 404. */
    fun resolve(path: String): PageAsset? {
        val parts = path.trimStart('/').split('/', limit = 3)
        val route = parts[0]
        val module = parts.getOrNull(1).orEmpty()
        val rest = parts.getOrNull(2)
        return when (route) {
            "background" -> if (parts.size == 2 || (parts.size == 3 && rest.isNullOrEmpty())) background(module) else null
            "module" -> inBook(module, rest, signed = false)
            "fonts" -> if (rest == "fonts.css") emptyFontsCss(module) else inBook(module, rest, signed = false)
            "features" -> inBook(module, rest, signed = true)
            "module-style" -> if (module == "epub") epubModuleStyle(rest) else moduleStyle(module, rest)
            "epub" -> epubResource(path.trimStart('/').removePrefix("epub").removePrefix("/"))
            else -> null
        }
    }

    private fun unsupported(route: String): PageAsset? {
        val first = if (route == "epub") epubLogged.compareAndSet(false, true) else epubStyleLogged.compareAndSet(false, true)
        if (first) log("page asset route /$route/ is not served here: no EPUB resource source")
        return null
    }

    private fun emptyFontsCss(module: String): PageAsset? {
        if (module.isEmpty() || bookLocation(module) == null) return null
        return PageAsset.Bytes(ByteArray(0), "text/css")
    }

    private fun background(module: String): PageAsset? {
        if (module.isEmpty()) return null
        val file = backgrounds()[module] ?: return null
        return file.takeIf { isRegularFile(it) }?.let { PageAsset.File(it, mimeType(it.name)) }
    }

    private fun inBook(module: String, relative: String?, signed: Boolean): PageAsset? {
        if (module.isEmpty() || relative.isNullOrEmpty()) return null
        val base = bookLocation(module) ?: return null
        val file = within(base, relative) ?: return null
        if (signed) {
            val check = verify ?: return null
            if (!check(file, file.parent!! / (file.name + ".sign"))) return null
        }
        return PageAsset.File(file, mimeType(relative))
    }

    private fun moduleStyle(module: String, relative: String?): PageAsset? {
        if (module.isEmpty() || relative.isNullOrEmpty()) return null
        if (!isSafeRelative(relative)) return null
        val base = bookLocation(module) ?: return null
        val styleName = bookStyleFile(module) ?: return null
        if (!isSafeRelative(styleName)) return null
        val styleFile = base / styleName
        val target = if (relative == "style.css") styleFile else (styleFile.parent ?: return null) / relative
        // The stylesheet's directory may be a subdirectory of the book; the file must still be inside the book.
        val targetRelative = try { target.relativeTo(base).toString() } catch (_: IllegalArgumentException) { return null }
        return within(base, targetRelative)?.let { PageAsset.File(it, mimeType(relative)) }
    }

    private fun epubResource(relative: String): PageAsset? {
        val source = epub ?: return unsupported("epub")
        if (!isSafeRelative(relative)) return null
        return source.openResource(relative)?.let { PageAsset.Stream(it, mimeType(relative)) }
    }

    private fun epubModuleStyle(rest: String?): PageAsset? {
        val source = epub ?: return unsupported("module-style/epub")
        if (rest.isNullOrEmpty() || !isSafeRelative(rest)) return null
        val initials = rest.substringBefore('/')
        val relative = rest.substringAfter('/', "")
        if (relative.isEmpty()) return null
        val segments = relative.split('/')
        if (segments.size == 2 && segments[1] == "style.css") {
            source.styleSheet(initials, segments[0])?.let { css ->
                val cleaned = css.replace(epubColorRe, "").replace(epubBodyRe, "").replace(epubLineHeightRe, "")
                return PageAsset.Bytes(cleaned.encodeToByteArray(), "text/css")
            }
        }
        return source.openBookResource(initials, relative)?.let { PageAsset.Stream(it, mimeType(relative)) }
    }

    private fun isRegularFile(path: Path): Boolean = fs.metadataOrNull(path)?.isRegularFile == true

    /** [relative] resolved under [base], or null when it would leave [base] (also through a symlink) or is no regular file. */
    private fun within(base: Path, relative: String): Path? {
        if (!isSafeRelative(relative)) return null
        val file = base / relative
        if (!isRegularFile(file)) return null
        val realBase = try { fs.canonicalize(base) } catch (_: Exception) { return null }
        val real = try { fs.canonicalize(file) } catch (_: Exception) { return null }
        if (real == realBase || !real.startsWith(realBase)) return null
        return file
    }

    private fun Path.startsWith(other: Path): Boolean {
        val mine = segments
        val theirs = other.segments
        return root == other.root && mine.size >= theirs.size && mine.subList(0, theirs.size) == theirs
    }

    companion object {
        /** A relative path with no traversal: no `..`/`.`/empty segments, no backslash, NUL, or leading `/`. */
        fun isSafeRelative(relative: String): Boolean =
            relative.isNotEmpty() && !relative.startsWith("/") && '\\' !in relative && '\u0000' !in relative &&
                relative.split('/').none { it.isEmpty() || it == ".." || it == "." }
    }
}

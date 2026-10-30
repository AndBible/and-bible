package net.bible.sharedui.desktop.webview

import net.bible.sharedlogic.platform.BackgroundImageCatalog
import net.bible.sharedlogic.security.FeatureSignatureVerifier
import net.bible.sharedui.desktop.webengine.BundleResource
import net.bible.sharedui.desktop.webengine.DesktopPageAssets
import org.crosswire.jsword.book.Books
import org.koin.mp.KoinPlatform
import java.net.URLConnection
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * The page asset routes of the desktop BibleView (Android `BibleView` path handlers):
 *
 * - `/background/<module>`: the add-on background image of that module ([backgrounds]).
 * - `/module/<initials>/<path>`: a file under the installed book's location.
 * - `/fonts/<initials>/<path>`: a file under the book's location (`fonts.css` needs the add-on font catalog, which
 *   desktop does not have yet: 404).
 * - `/features/<initials>/<path>`: as `/module/`, but only with a valid `<file>.sign` ([verify]).
 * - `/module-style/<initials>/<path>`: the book's `AndBibleCSS` stylesheet (`style.css`) or a file beside it.
 *   EPUB stylesheets need the EPUB backend's resources: 404.
 * - `/epub/<initials>/<path>`: no desktop source for EPUB resources yet: 404.
 *
 * Every file is resolved against its base (book location or the image file) and must stay inside it: segments `..`
 * or `.`, backslashes, NULs and absolute paths are refused. Unknown module, missing file, or an unsupported route is
 * a null (404); an unsupported route logs once per route.
 */
class DesktopPageAssetRoutes(
    private val backgrounds: () -> Map<String, Path>,
    private val bookLocation: (String) -> Path?,
    private val bookStyleFile: (String) -> String?,
    private val verify: (file: Path, signature: Path) -> Boolean,
    private val log: (String) -> Unit = {},
) : DesktopPageAssets {
    private val logged = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    override fun resolve(path: String): BundleResource? {
        val parts = path.trimStart('/').split('/', limit = 3)
        val route = parts[0]
        val module = parts.getOrNull(1).orEmpty()
        val rest = parts.getOrNull(2)
        return when (route) {
            "background" -> if (parts.size == 2) background(module) else null
            "module" -> inBook(module, rest, signed = false)
            "fonts" -> if (rest == "fonts.css") unsupported(route, "no desktop font catalog") else inBook(module, rest, signed = false)
            "features" -> inBook(module, rest, signed = true)
            "module-style" -> moduleStyle(module, rest)
            "epub" -> unsupported(route, "no desktop EPUB resource source")
            else -> null
        }
    }

    private fun unsupported(route: String, why: String): BundleResource? {
        if (logged.add(route)) log("page asset route /$route/ is not served on desktop: $why")
        return null
    }

    private fun background(module: String): BundleResource? {
        val file = backgrounds()[module] ?: return null
        return file.takeIf { Files.isRegularFile(it) }?.let { BundleResource(it, mimeFor(it.fileName.toString()), fromBundle = false) }
    }

    private fun inBook(module: String, relative: String?, signed: Boolean): BundleResource? {
        if (module.isEmpty() || relative.isNullOrEmpty()) return null
        val base = bookLocation(module) ?: return null
        val file = within(base, relative) ?: return null
        if (signed && !verify(file, file.resolveSibling(file.fileName.toString() + ".sign"))) {
            return null
        }
        return BundleResource(file, mimeFor(relative), fromBundle = false)
    }

    private fun moduleStyle(module: String, relative: String?): BundleResource? {
        if (module.isEmpty() || relative.isNullOrEmpty()) return null
        if (module == "epub" || relative.startsWith("epub/")) return unsupported("module-style/epub", "no desktop EPUB resource source")
        val base = bookLocation(module) ?: return null
        val styleName = bookStyleFile(module) ?: return null
        val styleFile = within(base, styleName, mustExist = false) ?: return null
        val target = if (relative == "style.css") styleFile else within(styleFile.parent ?: return null, relative) ?: return null
        // The stylesheet's directory may be the book's subdirectory; the file must still be inside the book.
        if (!target.normalize().startsWith(base.toAbsolutePath().normalize()) || !Files.isRegularFile(target)) return null
        return BundleResource(target, mimeFor(relative), fromBundle = false)
    }

    companion object {
        /** Resolves [relative] under [base], null when it would leave [base] or is not a regular file. */
        internal fun within(base: Path, relative: String, mustExist: Boolean = true): Path? {
            if (relative.isEmpty() || relative.startsWith("/") || relative.contains('\\') || relative.contains('\u0000')) return null
            if (relative.split('/').any { it == ".." || it == "." }) return null
            val root = base.toAbsolutePath().normalize()
            val file = root.resolve(relative).normalize()
            if (!file.startsWith(root) || file == root) return null
            if (mustExist && !Files.isRegularFile(file)) return null
            return file
        }

        private val MIME = mapOf(
            "css" to "text/css", "js" to "text/javascript", "mjs" to "text/javascript", "html" to "text/html",
            "htm" to "text/html", "json" to "application/json", "svg" to "image/svg+xml", "png" to "image/png",
            "jpg" to "image/jpeg", "jpeg" to "image/jpeg", "gif" to "image/gif", "webp" to "image/webp",
            "woff" to "font/woff", "woff2" to "font/woff2", "ttf" to "font/ttf", "otf" to "font/otf", "txt" to "text/plain",
        )

        /** The MIME from the file name (Android: `URLConnection.guessContentTypeFromName`). */
        fun mimeFor(name: String): String =
            MIME[name.substringAfterLast('.', "").lowercase()] ?: URLConnection.guessContentTypeFromName(name) ?: "application/octet-stream"

        /** Resolves through the Koin-bound [DesktopPageAssets] on each request; serves nothing until the app graph exists. */
        fun koinBound(): DesktopPageAssets = DesktopPageAssets { path ->
            KoinPlatform.getKoinOrNull()?.getOrNull<DesktopPageAssets>()?.resolve(path)
        }

        /** The Koin-backed routes: installed books from JSword, backgrounds from [BackgroundImageCatalog]. */
        fun fromKoin(log: (String) -> Unit): DesktopPageAssetRoutes {
            val verifier by lazy { FeatureSignatureVerifier() }
            return DesktopPageAssetRoutes(
                backgrounds = {
                    KoinPlatform.getKoin().get<BackgroundImageCatalog>().backgroundImages
                        .mapValues { Paths.get(it.value.path.toString()) }
                },
                bookLocation = { initials ->
                    Books.installed().getBook(initials)?.bookMetaData?.location?.getPath()?.let { Paths.get(it) }
                },
                bookStyleFile = { initials -> Books.installed().getBook(initials)?.bookMetaData?.getProperty("AndBibleCSS") },
                verify = { file, signature -> verifier.verify(file, signature) },
                log = log,
            )
        }
    }
}

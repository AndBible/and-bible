package net.bible.sharedui.desktop.webview

import net.bible.sharedlogic.platform.BackgroundImageCatalog
import net.bible.sharedlogic.security.FeatureSignatureVerifier
import net.bible.sharedui.desktop.webengine.BundleResource
import net.bible.sharedui.desktop.webengine.DesktopPageAssets
import net.bible.sharedui.webview.PageAsset
import net.bible.sharedui.webview.PageAssetRoutes
import okio.Path.Companion.toOkioPath
import org.crosswire.jsword.book.Books
import org.koin.mp.KoinPlatform
import java.net.URLConnection
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * The page asset routes of the desktop BibleView: the shared [PageAssetRoutes] (Android `BibleView` path handlers;
 * the route list and traversal rules are documented there) over `java.nio` paths, answering engine [BundleResource]s.
 *
 * Desktop has no EPUB resource source yet: `/epub/…` and `/module-style/epub/…` are a 404 logged once per route.
 * `fonts.css` is served as an empty stylesheet file ([emptyCssFile]).
 */
class DesktopPageAssetRoutes(
    backgrounds: () -> Map<String, Path>,
    bookLocation: (String) -> Path?,
    bookStyleFile: (String) -> String?,
    verify: (file: Path, signature: Path) -> Boolean,
    log: (String) -> Unit = {},
) : DesktopPageAssets {
    private val routes = PageAssetRoutes(
        backgrounds = { backgrounds().mapValues { it.value.toOkioPath() } },
        bookLocation = { bookLocation(it)?.toOkioPath() },
        bookStyleFile = bookStyleFile,
        verify = { file, signature -> verify(file.toNioPath(), signature.toNioPath()) },
        epub = null,
        log = log,
        mimeType = ::mimeFor,
    )

    override fun resolve(path: String): BundleResource? = when (val asset = routes.resolve(path)) {
        null -> null
        is PageAsset.File -> BundleResource(asset.path.toNioPath(), asset.mimeType, fromBundle = false)
        // Only `fonts.css` is generated on desktop (no EPUB source): one shared empty stylesheet file.
        is PageAsset.Bytes -> if (asset.bytes.isEmpty()) BundleResource(emptyCssFile(), asset.mimeType, fromBundle = false) else null
        is PageAsset.Stream -> { asset.source.close(); null }
    }

    companion object {
        /** The one empty stylesheet every `fonts.css` answer shares (a [BundleResource] needs a file). */
        @Volatile private var emptyCss: Path? = null

        /** The shared empty stylesheet, recreated when tmp cleaning removed it during a long session. */
        internal fun emptyCssFile(): Path = synchronized(this) {
            emptyCss?.takeIf { Files.isRegularFile(it) }
                ?: Files.createTempFile("andbible-fonts", ".css").also { it.toFile().deleteOnExit(); emptyCss = it }
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

package net.bible.sharedui.desktop.webview

import net.bible.android.control.page.OsisDocument
import net.bible.android.control.page.window.WindowControl
import net.bible.android.database.IdType
import net.bible.sharedlogic.platform.BackgroundImageCatalog
import net.bible.sharedlogic.security.FeatureSignatureVerifier
import net.bible.sharedui.desktop.webengine.BundleResource
import net.bible.sharedui.desktop.webengine.DesktopPageAssets
import net.bible.sharedui.webview.PageAsset
import net.bible.sharedui.webview.PageAssetEpubSource
import net.bible.sharedui.webview.PageAssetRoutes
import okio.Path.Companion.toOkioPath
import okio.buffer
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.Books
import org.koin.mp.KoinPlatform
import java.net.URLConnection
import java.nio.file.Path
import java.nio.file.Paths

/**
 * The page asset routes of the desktop BibleView: the shared [PageAssetRoutes] (Android `BibleView` path handlers;
 * the route list and traversal rules are documented there) over `java.nio` paths, answering engine [BundleResource]s.
 *
 * EPUB files are served straight from the book's verified directory; only generated stylesheets (and streams of
 * sources that cannot give a path) are materialized, in the one bounded [DesktopAssetCache] shared by all panes.
 * [forPane] binds unqualified EPUB resources to the document shown in that pane.
 */
class DesktopPageAssetRoutes(
    private val backgrounds: () -> Map<String, Path>,
    private val bookLocation: (String) -> Path?,
    private val bookStyleFile: (String) -> String?,
    private val verify: (content: ByteArray, signature: Path) -> Boolean,
    private val log: (String) -> Unit = {},
    private val fonts: () -> List<net.bible.sharedlogic.platform.AddonFont> = { emptyList() },
    private val epub: PageAssetEpubSource = DesktopEpubAssets({ null }),
    private val currentBook: (String) -> Book? = { null },
) : DesktopPageAssets {
    private val routes = PageAssetRoutes(
        backgrounds = { backgrounds().mapValues { it.value.toOkioPath() } },
        bookLocation = { bookLocation(it)?.toOkioPath() },
        bookStyleFile = bookStyleFile,
        verify = { content, signature -> verify(content, signature.toNioPath()) },
        epub = epub,
        log = log,
        mimeType = ::mimeFor,
        fonts = fonts,
    )

    override fun resolve(path: String): BundleResource? = when (val asset = routes.resolve(path)) {
        null -> null
        is PageAsset.File -> BundleResource(asset.path.toNioPath(), asset.mimeType, fromBundle = false)
        is PageAsset.Bytes -> BundleResource(generatedCssFile(path, asset.bytes), asset.mimeType, fromBundle = false)
        is PageAsset.Stream -> BundleResource(materializedStream(asset), asset.mimeType, fromBundle = false)
    }

    override fun forPane(id: () -> String?): DesktopPageAssets = DesktopPageAssetRoutes(
        backgrounds, bookLocation, bookStyleFile, verify, log, fonts,
        DesktopEpubAssets({ id()?.let(currentBook) }), currentBook,
    )

    /** Copy while holding the backend lease, closing it even on failure, into the shared bounded cache. */
    private fun materializedStream(asset: PageAsset.Stream): Path = asset.source.buffer().use { DesktopAssetCache.stream(it) }

    private fun generatedCssFile(route: String, bytes: ByteArray): Path = DesktopAssetCache.bytes(if (bytes.isEmpty()) "" else route, bytes)

    companion object {
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
        fun koinBound(): DesktopPageAssets = object : DesktopPageAssets {
            override fun resolve(path: String): BundleResource? =
                KoinPlatform.getKoinOrNull()?.getOrNull<DesktopPageAssets>()?.resolve(path)

            override fun forPane(id: () -> String?): DesktopPageAssets = object : DesktopPageAssets {
                private var owner: DesktopPageAssets? = null
                private var scoped: DesktopPageAssets? = null

                override fun resolve(path: String): BundleResource? = synchronized(this) {
                    val current = KoinPlatform.getKoinOrNull()?.getOrNull<DesktopPageAssets>() ?: return@synchronized null
                    if (owner !== current) {
                        scoped = current.forPane(id)
                        owner = current
                    }
                    scoped?.resolve(path)
                }
            }
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
                verify = { content, signature -> verifier.verifyBytes(content, signature.toOkioPath()) },
                log = log,
                fonts = { KoinPlatform.getKoin().get<net.bible.sharedlogic.addons.SharedAddonScanner>().fonts() },
                currentBook = { id ->
                    val window = KoinPlatform.getKoinOrNull()?.getOrNull<WindowControl>()?.windowRepository?.getWindow(IdType(id))
                    (window?.bibleView?.firstDocument as? OsisDocument)?.book
                },
            )
        }
    }
}

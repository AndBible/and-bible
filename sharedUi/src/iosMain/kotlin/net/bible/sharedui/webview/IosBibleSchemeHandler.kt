package net.bible.sharedui.webview

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.runBlocking
import net.bible.android.control.page.Document
import net.bible.android.control.page.OsisDocument
import net.bible.service.sword.epub.epubBackend
import net.bible.service.sword.epub.isEpub
import net.bible.sharedcore.log.nsLog
import net.bible.sharedlogic.platform.BackgroundImageCatalog
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.Source
import okio.buffer
import okio.use
import org.crosswire.jsword.book.Books
import org.koin.mp.KoinPlatform
import platform.Foundation.NSData
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSURL
import platform.Foundation.create
import platform.WebKit.WKURLSchemeHandlerProtocol
import platform.WebKit.WKURLSchemeTaskProtocol
import platform.WebKit.WKWebView
import platform.darwin.DISPATCH_QUEUE_PRIORITY_DEFAULT
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_global_queue
import platform.darwin.dispatch_get_main_queue

/** The custom scheme the BibleView bundle and its page assets are served from (WebKit does not let apps handle `https`). */
internal const val BIBLE_SCHEME = "bibleview"

/** The page origin: `bibleview://app`; root-relative asset paths (`/background/...`) resolve against it. */
internal const val BIBLE_ORIGIN = "$BIBLE_SCHEME://app"

/** One answer of [IosBibleSchemeHandler]: status, `Content-Type` and the whole body. */
internal class SchemeAnswer(val status: Int, val contentType: String?, val body: ByteArray)

/**
 * The pure request logic of [IosBibleSchemeHandler]: maps a `bibleview://app/<path>` URL to a bundle file under
 * [bundleDir] (`/` is `index.html`) or, when no bundle file matches, to a page asset of [routes] (#3918). A request
 * for another host, a path escaping the bundle, or anything [routes] refuses is a 404.
 */
internal class IosBibleSchemeRequests(
    private val bundleDir: Path?,
    private val routes: () -> PageAssetRoutes?,
    private val fs: FileSystem = FileSystem.SYSTEM,
) {
    /** [host] and the decoded [path] of the request URL. Never throws: a failure is a 404 with a log line. */
    fun answer(host: String?, path: String?): SchemeAnswer = try {
        if (host != "app" || path == null) notFound() else bundleFile(path) ?: pageAsset(path) ?: notFound()
    } catch (e: Exception) {
        nsLog("bibleview scheme: $path failed: $e")
        notFound()
    }

    private fun bundleFile(path: String): SchemeAnswer? {
        val dir = bundleDir ?: return null
        val relative = path.trimStart('/').ifEmpty { "index.html" }
        if (!PageAssetRoutes.isSafeRelative(relative)) return null
        val file = dir / relative
        if (fs.metadataOrNull(file)?.isRegularFile != true) return null
        val mime = pageAssetMimeType(relative)
        // Bundle text is UTF-8 and says so (as on desktop): scripts without a charset inherit the document's.
        val type = if (mime.startsWith("text/") || mime == "application/json" || mime == "image/svg+xml") "$mime; charset=utf-8" else mime
        return SchemeAnswer(200, type, fs.read(file) { readByteArray() })
    }

    private fun pageAsset(path: String): SchemeAnswer? {
        val asset = routes()?.resolve(path) ?: return null
        val body = when (asset) {
            is PageAsset.File -> fs.read(asset.path) { readByteArray() }
            is PageAsset.Bytes -> asset.bytes
            is PageAsset.Stream -> asset.source.buffer().use { it.readByteArray() }
        }
        return SchemeAnswer(200, asset.mimeType, body)
    }

    private fun notFound() = SchemeAnswer(404, null, ByteArray(0))
}

/**
 * Serves the BibleView page at [BIBLE_ORIGIN] (#3918). `loadFileURL` left the page's root-relative add-on asset URLs
 * (`/background/<module>`, `/module/...`, `/fonts/...`, `/features/...`, `/module-style/...`, `/epub/...`) pointing at
 * `file:///background/...`, which never exists; this handler gives them Android's `WebViewAssetLoader` routes
 * ([PageAssetRoutes]). Files are read off the main thread; WebKit is answered on the main thread, and only for a task it
 * has not stopped.
 *
 * [currentDocument] is the pane's shown document (`/epub/<path>` reads the current EPUB, as Android's
 * `EpubResourcesAssetHandler`); it is set when the window's session attaches.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal class IosBibleSchemeHandler(bundleDir: Path?) : NSObject(), WKURLSchemeHandlerProtocol {
    /** The pane's current document, or null (no EPUB resources) for panes without a session (PoC). */
    var currentDocument: () -> Document? = { null }

    private val requests = IosBibleSchemeRequests(bundleDir, { iosPageAssetRoutes { currentDocument() } })

    /** Tasks started and neither answered nor stopped; main thread only. */
    private val active = mutableSetOf<WKURLSchemeTaskProtocol>()

    @ObjCSignatureOverride
    override fun webView(webView: WKWebView, startURLSchemeTask: WKURLSchemeTaskProtocol) {
        val task = startURLSchemeTask
        val url: NSURL? = task.request.URL
        active += task
        val host = url?.host
        val path = url?.path
        dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT.toLong(), 0u)) {
            val answer = requests.answer(host, path)
            dispatch_async(dispatch_get_main_queue()) { respond(task, url, answer) }
        }
    }

    @ObjCSignatureOverride
    override fun webView(webView: WKWebView, stopURLSchemeTask: WKURLSchemeTaskProtocol) {
        active -= stopURLSchemeTask
    }

    private fun respond(task: WKURLSchemeTaskProtocol, url: NSURL?, answer: SchemeAnswer) {
        // A stopped task must not be answered: WebKit raises an Objective-C exception for it.
        if (!active.remove(task) || url == null) return
        val headers = buildMap<Any?, Any?> {
            answer.contentType?.let { put("Content-Type", it) }
            put("Content-Length", answer.body.size.toString())
        }
        val response = NSHTTPURLResponse(uRL = url, statusCode = answer.status.toLong(), HTTPVersion = "HTTP/1.1", headerFields = headers)
        task.didReceiveResponse(response)
        if (answer.body.isNotEmpty()) task.didReceiveData(answer.body.toNSData())
        task.didFinish()
    }

    private fun ByteArray.toNSData(): NSData = usePinned { NSData.create(bytes = it.addressOf(0), length = size.toULong()) }
}

/**
 * The page asset routes over the app's Koin graph: installed books from JSword, background images from
 * [BackgroundImageCatalog], EPUB content through the book's backend. Null before the graph exists. No `/features/`
 * files on iOS yet: there is no iOS signature verifier, so unsigned and signed feature files alike are a 404.
 */
internal fun iosPageAssetRoutes(currentDocument: () -> Document?): PageAssetRoutes? {
    val koin = KoinPlatform.getKoinOrNull() ?: return null
    return PageAssetRoutes(
        backgrounds = {
            koin.getOrNull<BackgroundImageCatalog>()?.backgroundImages?.mapValues { it.value.path }.orEmpty()
        },
        bookLocation = { initials -> Books.installed().getBook(initials)?.bookMetaData?.location?.getPath()?.toPath() },
        bookStyleFile = { initials -> Books.installed().getBook(initials)?.bookMetaData?.getProperty("AndBibleCSS") },
        verify = null,
        epub = IosEpubAssets(currentDocument),
        log = { nsLog("bibleview scheme: $it") },
    )
}

/** [PageAssetEpubSource] over JSword's installed EPUBs (Android `EpubResourcesAssetHandler` / `ModuleStylesAssetHandler`). */
private class IosEpubAssets(private val currentDocument: () -> Document?) : PageAssetEpubSource {
    override fun openResource(relativePath: String): Source? =
        (currentDocument() as? OsisDocument)?.book?.epubBackend?.openResource(relativePath)

    override fun openBookResource(initials: String, relativePath: String): Source? =
        Books.installed().getBook(initials)?.takeIf { it.isEpub }?.epubBackend?.openResource(relativePath)

    override fun styleSheet(initials: String, key: String): String? {
        val book = Books.installed().getBook(initials)?.takeIf { it.isEpub } ?: return null
        val backend = book.epubBackend ?: return null
        val pageKey = try { book.getKey(key) } catch (_: Exception) { return null }
        return try {
            runBlocking { backend.styleSheetContent(pageKey) } // background queue (IosBibleSchemeHandler), never main
        } catch (_: IllegalStateException) {
            null // a retired page must not read replacement assets
        }
    }
}

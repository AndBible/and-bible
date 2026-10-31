package net.bible.sharedui.ios.debug

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.bible.android.control.page.window.WindowControl
import net.bible.service.sword.backgroundimage.addManuallyInstalledBackgroundImageBooks
import net.bible.service.sword.backgroundimage.backgroundImageModulesDir
import net.bible.service.sword.epub.addEpubBook
import net.bible.service.sword.epub.epubModulesDir
import net.bible.sharedcore.log.nsLog
import net.bible.sharedcore.platform.AppEnvironment
import net.bible.sharedcore.platform.CoreStrings
import net.bible.sharedui.webview.IosReadingWebViews
import okio.FileSystem
import okio.Path
import org.jetbrains.skia.Color
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import org.koin.mp.KoinPlatform
import platform.Foundation.NSError
import platform.WebKit.WKContentWorld
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * Debug-only simulator fixture for the BibleView page asset routes (#3918), driven by `PageAssetsUITests`:
 *
 * - `-seedPageAssets`: writes a striped background image into `modulesDir/background` and registers it (Color settings
 *   then lists "PageAssetsBg"), and an unpacked EPUB `modulesDir/epub/PageAssets` whose only chapter shows one image,
 *   registered as `Epub-PageAssets`.
 * - `-clearPageAssets`: removes both fixtures again (files and registered books), so later UI classes do not inherit them.
 * - `-probePageAssets`: once the page is shown, logs `PAGE-ASSETS-PROBE <json>` with the HTTP status of each route
 *   and the load state of every `<img>` in the active window (read from the app log, `xcode/app-iphone.log`).
 *
 * Callers check `Platform.isDebugBinary` first (see `IosDebugLaunchArgs`).
 */
internal object IosPageAssetsDebugSeed {
    const val EPUB_DIR = "PageAssets"
    const val EPUB_INITIALS = "Epub-PageAssets"

    suspend fun seed() = withContext(Dispatchers.Default) {
        val fs = FileSystem.SYSTEM
        val background = backgroundImageModulesDir()
        fs.createDirectories(background)
        fs.write(background / "PageAssetsBg.png") { write(stripes(360, 360, Color.makeRGB(214, 184, 120), Color.makeRGB(150, 90, 40))) }
        addManuallyInstalledBackgroundImageBooks()

        val epub = epubModulesDir() / EPUB_DIR
        if (!fs.exists(epub)) {
            writeEpub(fs, epub)
            val koin = KoinPlatform.getKoin()
            addEpubBook(epub, koin.get<CoreStrings>(), koin.get<AppEnvironment>())
        }
        nsLog("PAGE-ASSETS-SEED done")
    }

    suspend fun clear() = withContext(Dispatchers.Default) {
        val books = org.crosswire.jsword.book.Books.installed()
        for (initials in listOf("BGIMG_PageAssetsBg", EPUB_INITIALS)) books.getBook(initials)?.let { books.removeBook(it) }
        FileSystem.SYSTEM.delete(backgroundImageModulesDir() / "PageAssetsBg.png")
        FileSystem.SYSTEM.deleteRecursively(epubModulesDir() / EPUB_DIR)
        nsLog("PAGE-ASSETS-SEED cleared")
    }

    private fun writeEpub(fs: FileSystem, dir: Path) {
        fs.createDirectories(dir / "META-INF")
        fs.createDirectories(dir / "OEBPS/images")
        fs.write(dir / "mimetype") { writeUtf8("application/epub+zip") }
        fs.write(dir / "META-INF/container.xml") {
            writeUtf8(
                """<?xml version="1.0"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles>
</container>""",
            )
        }
        fs.write(dir / "OEBPS/content.opf") {
            writeUtf8(
                """<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="2.0" unique-identifier="id">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
    <dc:title>Page Assets Fixture</dc:title><dc:language>en</dc:language><dc:identifier id="id">page-assets-3918</dc:identifier>
  </metadata>
  <manifest>
    <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
    <item id="ch1" href="ch1.xhtml" media-type="application/xhtml+xml"/>
    <item id="pic" href="images/pic.png" media-type="image/png"/>
  </manifest>
  <spine toc="ncx"><itemref idref="ch1"/></spine>
</package>""",
            )
        }
        fs.write(dir / "OEBPS/toc.ncx") {
            writeUtf8(
                """<?xml version="1.0" encoding="UTF-8"?>
<ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
  <head><meta name="dtb:uid" content="page-assets-3918"/></head>
  <docTitle><text>Page Assets Fixture</text></docTitle>
  <navMap><navPoint id="n1" playOrder="1"><navLabel><text>Chapter with image</text></navLabel><content src="ch1.xhtml"/></navPoint></navMap>
</ncx>""",
            )
        }
        fs.write(dir / "OEBPS/ch1.xhtml") {
            writeUtf8(
                """<?xml version="1.0" encoding="UTF-8"?>
<html xmlns="http://www.w3.org/1999/xhtml"><head><title>Chapter with image</title></head>
<body><h1>Chapter with image</h1><p>The striped picture below is served from /epub/.</p>
<p><img src="images/pic.png" alt="EPUB fixture picture"/></p><p>End of chapter.</p></body></html>""",
            )
        }
        fs.write(dir / "OEBPS/images/pic.png") { write(stripes(300, 200, Color.makeRGB(30, 90, 200), Color.makeRGB(230, 60, 60))) }
    }

    /** A PNG with diagonal stripes of [a] and [b], unmistakable in a screenshot. */
    private fun stripes(width: Int, height: Int, a: Int, b: Int): okio.ByteString {
        val surface = Surface.makeRasterN32Premul(width, height)
        val canvas = surface.canvas
        canvas.clear(a)
        val paint = Paint().apply { color = b }
        val step = 40
        var x = -height
        while (x < width) {
            canvas.save()
            canvas.translate(x.toFloat(), 0f)
            canvas.skew(1f, 0f)
            canvas.drawRect(Rect.makeXYWH(0f, 0f, step / 2f, height.toFloat()), paint)
            canvas.restore()
            x += step
        }
        val data = surface.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG) ?: error("PNG encoding failed")
        return okio.ByteString.of(*data.bytes)
    }

    private const val PROBE = """
        const urls = ['/background/BGIMG_PageAssetsBg', '/epub/images/pic.png', '/module/NOPE/x.png',
            '/background/../index.html', '/module-style/Epub-PageAssets/style.css'];
        const statuses = await Promise.all(urls.map(u => fetch(u).then(r => u + ' ' + r.status + ' ' + r.headers.get('content-type'),
            e => u + ' ERR ' + e)));
        const imgs = Array.from(document.querySelectorAll('img')).map(i => i.getAttribute('src') + ' complete=' + i.complete + ' w=' + i.naturalWidth);
        const bg = Array.from(document.querySelectorAll('*')).map(e => getComputedStyle(e).backgroundImage).filter(b => b && b !== 'none');
        return JSON.stringify({origin: location.origin, href: location.href, statuses, imgs, bg});
    """

    /** Logs the probe result of the active window's page, [delayMs] after the call (the page must have rendered). */
    @OptIn(ExperimentalForeignApi::class)
    suspend fun probe(delayMs: Long = 8_000) {
        kotlinx.coroutines.delay(delayMs)
        val windowId = KoinPlatform.getKoin().get<WindowControl>().activeWindow.id.toString()
        dispatch_async(dispatch_get_main_queue()) {
            val webView = IosReadingWebViews.webViewFor(windowId)
            webView.callAsyncJavaScript(PROBE, arguments = null, inFrame = null, inContentWorld = WKContentWorld.pageWorld) { result: Any?, error: NSError? ->
                nsLog("PAGE-ASSETS-PROBE ${result ?: ("error " + error?.localizedDescription)}")
            }
        }
    }
}

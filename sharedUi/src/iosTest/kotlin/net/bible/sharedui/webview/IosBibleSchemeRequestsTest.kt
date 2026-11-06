package net.bible.sharedui.webview

import platform.Foundation.dateWithTimeIntervalSinceNow
import platform.Foundation.runUntilDate
import platform.Foundation.timeIntervalSinceNow
import okio.FileSystem
import okio.Path
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** The `bibleview://app` request logic of the iOS BibleView (#3918): bundle files, page asset routes, 404s. */
class IosBibleSchemeRequestsTest {
    private val fs = FileSystem.SYSTEM
    private val tmp: Path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "bibleview-scheme-${Random.nextLong().toULong()}"
    private val bundle = (tmp / "bibleview-js").also {
        fs.createDirectories(it / "assets")
        fs.write(it / "index.html") { writeUtf8("<html></html>") }
        fs.write(it / "assets/app.js") { writeUtf8("js") }
    }
    private val book = (tmp / "modules/KJV").also {
        fs.createDirectories(it)
        fs.write(it / "pic.png") { writeUtf8("png") }
    }
    private val image = (tmp / "bg.png").also { fs.write(it) { writeUtf8("bg") } }
    private val secret = (tmp / "secret.txt").also { fs.write(it) { writeUtf8("secret") } }

    private val routes = PageAssetRoutes(
        backgrounds = { mapOf("BGIMG_x" to image) },
        bookLocation = { if (it == "KJV") book else null },
        bookStyleFile = { null },
        verify = null,
    )
    private val requests = IosBibleSchemeRequests(bundle, { routes })

    @AfterTest
    fun cleanUp() { fs.deleteRecursively(tmp) }

    @Test fun indexAndBundleFilesServedWithUtf8Text() {
        val index = requests.answer("app", "/")
        assertEquals(200, index.status)
        assertEquals("text/html; charset=utf-8", index.contentType)
        assertEquals("<html></html>", index.body.decodeToString())
        assertEquals("text/javascript; charset=utf-8", requests.answer("app", "/assets/app.js").contentType)
    }

    @Test fun pageAssetsServed() {
        val bg = requests.answer("app", "/background/BGIMG_x")
        assertEquals(200, bg.status); assertEquals("image/png", bg.contentType); assertEquals("bg", bg.body.decodeToString())
        assertEquals("png", requests.answer("app", "/module/KJV/pic.png").body.decodeToString())
    }

    @Test fun refusedAndMissingAre404() {
        for (path in listOf("/../secret.txt", "/assets/../../secret.txt", "/module/KJV/../../secret.txt", "/background/nope",
            "/features/KJV/pic.png", "/missing.js", "/epub/x.png")) {
            val answer = requests.answer("app", path)
            assertEquals(404, answer.status, path)
            assertEquals(0, answer.body.size, path)
        }
        fs.createSymlink(bundle / "leak.txt", secret)
        assertEquals(404, requests.answer("app", "/leak.txt").status, "symlink out of the bundle")
        assertEquals(404, requests.answer("app", "/C:secret.txt").status)
        assertEquals(404, requests.answer("evil", "/index.html").status)
        assertEquals(404, requests.answer("app", null).status)
    }

    @Test fun koinRoutesServeSignedRefParserAndFailClosedAfterTampering() {
        val installed = org.crosswire.jsword.book.Books.installed()
        val initials = "SignedFeature3984"
        val metadata = org.crosswire.jsword.book.sword.SwordBookMetaData(
            "[$initials]\nModDrv=RawGenBook\n".encodeToByteArray(), initials,
        ).apply { location = org.crosswire.common.util.NetUtil.fileKUri(book, directory = true) }
        val module = org.crosswire.jsword.book.sword.SwordBook(metadata, org.crosswire.jsword.book.sword.NullBackend())
        val parser = book / "fi_bcv_parser.js"
        val signature = book / "fi_bcv_parser.js.sign"
        fs.write(parser) { write(SignedFeatureFixture.content) }
        fs.write(signature) { write(SignedFeatureFixture.signature) }
        org.koin.core.context.startKoin { }
        try {
            installed.addBook(module)
            val production = IosBibleSchemeRequests(bundle, { iosPageAssetRoutes { null } })
            val url = "/features/$initials/fi_bcv_parser.js"
            val answer = production.answer("app", url)
            assertEquals(200, answer.status)
            assertEquals("text/javascript", answer.contentType)
            kotlin.test.assertContentEquals(SignedFeatureFixture.content, answer.body)
            fs.write(parser) { writeUtf8("tampered") }
            assertEquals(404, production.answer("app", url).status)
            fs.write(parser) { write(SignedFeatureFixture.content) }
            fs.delete(signature)
            assertEquals(404, production.answer("app", url).status)
        } finally {
            installed.removeBook(module)
            org.koin.core.context.stopKoin()
        }
    }

    @OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
    @Test fun webKitLoadsSignedFinnishRefParserThroughProductionScheme() {
        val installed = org.crosswire.jsword.book.Books.installed()
        val initials = "SignedWebKit3984"
        val metadata = org.crosswire.jsword.book.sword.SwordBookMetaData(
            "[$initials]\nModDrv=RawGenBook\n".encodeToByteArray(), initials,
        ).apply { location = org.crosswire.common.util.NetUtil.fileKUri(book, directory = true) }
        val module = org.crosswire.jsword.book.sword.SwordBook(metadata, org.crosswire.jsword.book.sword.NullBackend())
        fs.write(book / "fi_bcv_parser.js") { write(SignedFeatureFixture.content) }
        fs.write(book / "fi_bcv_parser.js.sign") { write(SignedFeatureFixture.signature) }
        fs.write(bundle / "index.html") {
            writeUtf8("""<html><head><script src="/features/$initials/fi_bcv_parser.js"></script>
                <script>var parser = new bcv_parser(); window.parsedReference = parser.parse('Johanneksen evankeliumi 3:16').osis();</script>
                </head><body>Signed feature smoke</body></html>""")
        }
        org.koin.core.context.startKoin { }
        val configuration = platform.WebKit.WKWebViewConfiguration()
        configuration.setURLSchemeHandler(IosBibleSchemeHandler(bundle), forURLScheme = BIBLE_SCHEME)
        val webView = platform.WebKit.WKWebView(platform.CoreGraphics.CGRectMake(0.0, 0.0, 320.0, 480.0), configuration)
        try {
            installed.addBook(module)
            webView.loadRequest(platform.Foundation.NSURLRequest.requestWithURL(platform.Foundation.NSURL(string = "$BIBLE_ORIGIN/index.html")))
            val deadline = platform.Foundation.NSDate.dateWithTimeIntervalSinceNow(20.0)
            var parsed: Any? = null
            while (parsed != "John.3.16" && deadline.timeIntervalSinceNow > 0) {
                webView.evaluateJavaScript("window.parsedReference") { result, _ -> parsed = result }
                platform.Foundation.NSRunLoop.mainRunLoop.runUntilDate(platform.Foundation.NSDate.dateWithTimeIntervalSinceNow(0.05))
            }
            assertEquals("John.3.16", parsed, "WebKit must execute the signed Finnish parser from the production /features/ route")
        } finally {
            webView.stopLoading()
            installed.removeBook(module)
            org.koin.core.context.stopKoin()
        }
    }

    @Test fun noBundleAndNoKoinStill404() {
        val bare = IosBibleSchemeRequests(null, { null })
        assertEquals(404, bare.answer("app", "/index.html").status)
        assertEquals(404, bare.answer("app", "/background/BGIMG_x").status)
    }
}

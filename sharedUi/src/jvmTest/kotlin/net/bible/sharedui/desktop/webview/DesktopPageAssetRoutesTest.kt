package net.bible.sharedui.desktop.webview

import net.bible.sharedui.desktop.webengine.DesktopPageAssets
import net.bible.sharedui.desktop.webengine.kcef.BundleRequestHandler
import net.bible.sharedui.desktop.webengine.kcef.BUNDLE_ORIGIN
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopPageAssetRoutesTest {
    private val tmp: Path = Files.createTempDirectory("page-assets")
    private val book: Path = tmp.resolve("modules/KJV").createDirectories().also {
        it.resolve("pic.png").writeText("png")
        it.resolve("sub").createDirectories()
        it.resolve("sub/a.js").writeText("a")
        it.resolve("css").createDirectories()
        it.resolve("css/my.css").writeText("body{}")
        it.resolve("css/extra.woff2").writeText("w")
        it.resolve("feat.js").writeText("f")
        it.resolve("feat.js.sign").writeText("sig")
        it.resolve("unsigned.js").writeText("u")
    }
    private val secret = tmp.resolve("secret.txt").also { it.writeText("secret") }
    private val image = tmp.resolve("bg.jpg").also { it.writeText("jpg") }
    private val logs = mutableListOf<String>()

    private fun routes(verify: (Path, Path) -> Boolean = { f, s -> f.fileName.toString() == "feat.js" && Files.exists(s) }) =
        DesktopPageAssetRoutes(
            backgrounds = { mapOf("D3DBackground" to image) },
            bookLocation = { if (it == "KJV") book else null },
            bookStyleFile = { if (it == "KJV") "css/my.css" else null },
            verify = verify,
            log = { logs.add(it) },
        )

    @AfterTest
    fun cleanUp() { tmp.toFile().deleteRecursively() }

    @Test fun backgroundServed() {
        val r = assertNotNull(routes().resolve("/background/D3DBackground"))
        assertEquals(image, r.file); assertEquals("image/jpeg", r.mimeType)
        assertNull(routes().resolve("/background/Nope"))
    }

    @Test fun moduleServed() {
        val r = assertNotNull(routes().resolve("/module/KJV/sub/a.js"))
        assertEquals(book.resolve("sub/a.js"), r.file); assertEquals("text/javascript", r.mimeType)
        assertNull(routes().resolve("/module/KJV/missing.js"))
        assertNull(routes().resolve("/module/KJV/sub"))
    }

    @Test fun fontFileServedAndFontsCssIs404() {
        val r = routes()
        assertEquals("font/woff2", assertNotNull(r.resolve("/fonts/KJV/css/extra.woff2")).mimeType)
        assertNull(r.resolve("/fonts/KJV/fonts.css"))
        assertNull(r.resolve("/fonts/KJV/fonts.css"))
        assertEquals(1, logs.size, "one log line per unsupported route: $logs")
    }

    @Test fun signedFeatureServed() {
        assertEquals(book.resolve("feat.js"), assertNotNull(routes().resolve("/features/KJV/feat.js")).file)
    }

    @Test fun unsignedFeatureRefused() {
        assertNull(routes().resolve("/features/KJV/unsigned.js"))
        assertNull(routes(verify = { _, _ -> false }).resolve("/features/KJV/feat.js"))
    }

    @Test fun moduleStyleServed() {
        assertEquals(book.resolve("css/my.css"), assertNotNull(routes().resolve("/module-style/KJV/style.css")).file)
        assertEquals(book.resolve("css/extra.woff2"), assertNotNull(routes().resolve("/module-style/KJV/extra.woff2")).file)
        assertNull(routes().resolve("/module-style/KJV/../../../secret.txt"))
    }

    @Test fun epubRoutesAre404WithOneLogLine() {
        val r = routes()
        assertNull(r.resolve("/epub/KJV/x.xhtml"))
        assertNull(r.resolve("/epub/KJV/y.xhtml"))
        assertNull(r.resolve("/module-style/epub/KJV/ch1/style.css"))
        assertEquals(2, logs.size, logs.toString())
    }

    @Test fun traversalRefused() {
        val r = routes()
        for (p in listOf(
            "/module/KJV/../../secret.txt", "/module/KJV/%2e%2e/secret.txt", "/module/KJV/..\\..\\secret.txt",
            "/module/KJV//$secret", "/module/KJV/sub/../../../secret.txt", "/features/KJV/../../secret.txt",
            "/fonts/KJV/../../secret.txt", "/module/KJV/./pic.png",
        )) assertNull(r.resolve(p), p)
    }

    @Test fun traversalRefusedThroughTheRequestHandler() {
        val bundle = tmp.resolve("bundle").createDirectories()
        val h = BundleRequestHandler(bundle, pageAssets = routes())
        for (u in listOf("/module/KJV/%2e%2e/secret.txt", "/module/KJV/..%2fsecret.txt", "/module/KJV/%2e%2e%5csecret.txt", "/module/KJV/../../secret.txt"))
            assertNull(h.resolve("$BUNDLE_ORIGIN$u"), u)
        assertNotNull(h.resolve("$BUNDLE_ORIGIN/module/KJV/pic.png"))
    }

    @Test fun unknownModule404() {
        val r = routes()
        assertNull(r.resolve("/module/NOPE/pic.png"))
        assertNull(r.resolve("/features/NOPE/feat.js"))
        assertNull(r.resolve("/fonts/NOPE/a.ttf"))
        assertNull(r.resolve("/module-style/NOPE/style.css"))
        assertNull(r.resolve("/module//pic.png"))
        assertNull(r.resolve("/unknown/KJV/pic.png"))
    }

    @Test fun bundleFileWinsOverAssetRoute() {
        val bundle = tmp.resolve("bundle2").createDirectories()
        bundle.resolve("module/KJV").createDirectories()
        bundle.resolve("module/KJV/pic.png").writeText("from bundle")
        val h = BundleRequestHandler(bundle, pageAssets = routes())
        val r = assertNotNull(h.resolve("$BUNDLE_ORIGIN/module/KJV/pic.png"))
        assertTrue(r.fromBundle)
        assertEquals(bundle.resolve("module/KJV/pic.png"), r.file)
        // And an asset is not touched by document-start script injection.
        book.resolve("page.html").writeText("<html><head></head></html>")
        val asset = assertNotNull(BundleRequestHandler(bundle, listOf("window.x=1"), routes()).let { it.resolve("$BUNDLE_ORIGIN/module/KJV/page.html")?.let { res -> res to it.body(res) } })
        assertEquals("<html><head></head></html>", asset.second.decodeToString())
    }

    @Test fun backgroundAcceptsTrailingSlash() {
        assertEquals(image, assertNotNull(routes().resolve("/background/D3DBackground/")).file)
    }

    @Test fun symlinkLeavingTheBookRefused() {
        Files.createSymbolicLink(book.resolve("link.txt"), secret)
        Files.createSymbolicLink(book.resolve("linkdir"), tmp)
        Files.createSymbolicLink(book.resolve("css/style-link.css"), secret)
        val r = routes()
        assertNull(r.resolve("/module/KJV/link.txt"))
        assertNull(r.resolve("/module/KJV/linkdir/secret.txt"))
        assertNull(r.resolve("/module-style/KJV/style-link.css"))
        assertNotNull(r.resolve("/module/KJV/pic.png"))
    }

    @Test fun pageAssetsHaveNoForcedCharset() {
        val r = assertNotNull(routes().resolve("/module/KJV/css/my.css"))
        assertEquals("text/css", r.contentType)
        assertEquals("text/css; charset=utf-8", net.bible.sharedui.desktop.webengine.BundleResource(r.file, "text/css").contentType)
    }

    @Test fun koinBoundAnswersNullBeforeKoinStarts() {
        org.koin.mp.KoinPlatform.getKoinOrNull()?.let { return } // another test owns a running Koin; skip
        assertNull(DesktopPageAssetRoutes.koinBound().resolve("/module/KJV/pic.png"))
    }

    @Test fun nonePagesAssetsServesNothing() {
        assertNull(DesktopPageAssets.None.resolve("/module/KJV/pic.png"))
    }
}

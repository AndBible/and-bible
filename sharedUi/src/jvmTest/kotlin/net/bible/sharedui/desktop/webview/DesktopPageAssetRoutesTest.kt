package net.bible.sharedui.desktop.webview

import net.bible.sharedui.desktop.webengine.DesktopPageAssets
import net.bible.sharedui.desktop.webengine.kcef.BundleRequestHandler
import net.bible.sharedui.desktop.webengine.kcef.BUNDLE_ORIGIN
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.readText
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

    // Path.deleteRecursively does not follow symbolic links. java.io.File.deleteRecursively did, and the
    // `linkdir -> tmp` cycle of symlinkLeavingTheBookRefused then made its walk re-enter the directory it was
    // deleting (FileTreeWalk AssertionError "rootDir must be verified to be directory beforehand", order-dependent).
    @OptIn(ExperimentalPathApi::class)
    @AfterTest
    fun cleanUp() { tmp.deleteRecursively() }

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

    @Test fun fontFileServed() {
        assertEquals("font/woff2", assertNotNull(routes().resolve("/fonts/KJV/css/extra.woff2")).mimeType)
    }

    @Test fun fontsCssSurvivesTmpCleaning() {
        val r = routes()
        Files.delete(assertNotNull(r.resolve("/fonts/KJV/fonts.css")).file) // tmp cleaning in a long session
        assertTrue(Files.isRegularFile(assertNotNull(r.resolve("/fonts/KJV/fonts.css")).file))
    }

    @Test fun fontsCssAnswers200() {
        val r = routes()
        val css = assertNotNull(r.resolve("/fonts/KJV/fonts.css"))
        assertEquals("text/css", css.mimeType)
        assertEquals(0L, Files.size(css.file))
        assertNull(r.resolve("/fonts/NOPE/fonts.css"))
        assertEquals(emptyList(), logs, "fonts.css is not an error: $logs")
    }

    @Test fun generatedFontCssIsMaterializedAndReusesItsFile() {
        val metadata = org.crosswire.jsword.book.sword.SwordBookMetaData(
            """[DesktopAssetFont]
ModDrv=RawGenBook
AndBibleProvidesFont=Desktop Study;css/extra.woff2
""".encodeToByteArray(), "DesktopAssetFont")
        metadata.location = org.crosswire.common.util.NetUtil.fileKUri(okio.Path.Companion.run { book.toString().toPath() }, directory = true)
        val fontBook = org.crosswire.jsword.book.sword.SwordBook(metadata, org.crosswire.jsword.book.sword.NullBackend())
        val scanner = net.bible.sharedlogic.addons.SharedAddonScanner({ listOf(fontBook) })
        run {
            val r = DesktopPageAssetRoutes({ emptyMap() }, { book }, { null }, { _, _ -> false }, fonts = { scanner.fonts() })
            val first = assertNotNull(r.resolve("/fonts/DesktopAssetFont/fonts.css"))
            assertEquals("@font-face { font-family: 'Desktop Study'; src: url('css/extra.woff2'); }\n", Files.readString(first.file))
            assertEquals(first.file, assertNotNull(r.resolve("/fonts/DesktopAssetFont/fonts.css")).file)
            Files.delete(first.file)
        }
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

    /** EPUB resources need the Android-only current-book lookup and a stream body (BundleResource is a file): 404, documented. */
    @Test fun epubResourceServedOrDocumented() {
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

    private fun fontRoutes(fonts: () -> List<net.bible.sharedlogic.platform.AddonFont>) = DesktopPageAssetRoutes(
        backgrounds = { emptyMap() }, bookLocation = { if (it == "KJV") book else null }, bookStyleFile = { null },
        verify = { _, _ -> false }, fonts = fonts,
    )

    @Test fun generatedFontCssComesFromTheInjectedCatalogAndIsRewrittenOnlyWhenItChanges() {
        var installed = listOf(net.bible.sharedlogic.platform.AddonFont("KJV", "One", "css/extra.woff2"))
        var catalogCalls = 0
        val r = fontRoutes { catalogCalls++; installed }
        val first = assertNotNull(r.resolve("/fonts/KJV/fonts.css"))
        assertTrue(first.file.readText().contains("font-family: 'One'"), first.file.readText())
        Files.setLastModifiedTime(first.file, java.nio.file.attribute.FileTime.fromMillis(1_000))
        val second = assertNotNull(r.resolve("/fonts/KJV/fonts.css"))
        assertEquals(first.file, second.file)
        assertEquals(1_000, Files.getLastModifiedTime(second.file).toMillis(), "unchanged content must not be rewritten")
        installed = installed + net.bible.sharedlogic.platform.AddonFont("KJV", "Two", "css/extra.woff2")
        val third = assertNotNull(r.resolve("/fonts/KJV/fonts.css"))
        assertEquals(first.file, third.file)
        assertTrue(third.file.readText().contains("font-family: 'Two'"))
        assertEquals(3, catalogCalls)
        assertTrue(Files.list(first.file.parent).use { it.toList() }.none { it.fileName.toString().endsWith(".tmp") && it.fileName.toString().startsWith("andbible-fonts") },
            "staging files must not be left behind")
    }

    @Test fun anEmptyCatalogServesAnEmptyStylesheet() {
        assertEquals(0, fontRoutes { emptyList() }.resolve("/fonts/KJV/fonts.css")!!.file.let { Files.size(it) }.toInt())
    }
}

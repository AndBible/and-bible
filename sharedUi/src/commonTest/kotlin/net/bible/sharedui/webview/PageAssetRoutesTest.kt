package net.bible.sharedui.webview

import okio.Buffer
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.Source
import okio.buffer
import okio.use
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The BibleView page asset routes shared by desktop and iOS (#3918); Android's `BibleView` path handlers are the reference. */
class PageAssetRoutesTest {
    private val fs = FileSystem.SYSTEM
    private val tmp: Path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "page-asset-routes-${Random.nextLong().toULong()}"
    private val book: Path = (tmp / "modules/KJV").also { b ->
        fs.createDirectories(b / "sub")
        fs.createDirectories(b / "css")
        write(b / "pic.png", "png")
        write(b / "sub/a.js", "a")
        write(b / "css/my.css", "body{}")
        write(b / "css/extra.woff2", "w")
        write(b / "feat.js", "f")
        write(b / "feat.js.sign", "sig")
        write(b / "unsigned.js", "u")
    }
    private val secret = (tmp / "secret.txt").also { write(it, "secret") }
    private val image = (tmp / "bg.jpg").also { write(it, "jpg") }
    private val logs = mutableListOf<String>()

    private fun write(path: Path, text: String) = fs.write(path) { writeUtf8(text) }

    /** An EPUB source recording what it was asked for; [resources] are the files of the current / named book. */
    private inner class FakeEpub(val resources: Map<String, String> = mapOf("OEBPS/images/pic.png" to "img")) : PageAssetEpubSource {
        val asked = mutableListOf<String>()
        override fun openResource(relativePath: String): Source? {
            asked += "current:$relativePath"
            return resources[relativePath]?.let { Buffer().writeUtf8(it) }
        }
        override fun openBookResource(initials: String, relativePath: String): Source? {
            asked += "$initials:$relativePath"
            return if (initials == "Epub-X") resources[relativePath]?.let { Buffer().writeUtf8(it) } else null
        }
        override fun styleSheet(initials: String, key: String): String? {
            asked += "style:$initials:$key"
            return if (initials == "Epub-X" && key == "ch1") "body { margin: 0 } p { color: red; font-weight: bold; line-height: 2; }" else null
        }
    }

    private fun routes(
        verify: ((Path, Path) -> Boolean)? = { f, s -> f.name == "feat.js" && fs.exists(s) },
        epub: PageAssetEpubSource? = null,
    ) = PageAssetRoutes(
        backgrounds = { mapOf("D3DBackground" to image) },
        bookLocation = { if (it == "KJV") book else null },
        bookStyleFile = { if (it == "KJV") "css/my.css" else null },
        verify = verify,
        epub = epub,
        log = { logs.add(it) },
    )

    private fun PageAsset?.file(): Path = assertIs<PageAsset.File>(this).path

    private fun PageAsset?.text(): String = when (this) {
        is PageAsset.Bytes -> bytes.decodeToString()
        is PageAsset.Stream -> source.buffer().use { it.readUtf8() }
        else -> error("not a body: $this")
    }

    @AfterTest
    fun cleanUp() { fs.deleteRecursively(tmp) }

    @Test fun backgroundServed() {
        val r = routes().resolve("/background/D3DBackground")
        assertEquals(image, r.file()); assertEquals("image/jpeg", r!!.mimeType)
        assertEquals(image, routes().resolve("/background/D3DBackground/").file())
        assertNull(routes().resolve("/background/Nope"))
        assertNull(routes().resolve("/background/D3DBackground/extra"))
    }

    @Test fun moduleServed() {
        val r = routes().resolve("/module/KJV/sub/a.js")
        assertEquals(book / "sub/a.js", r.file()); assertEquals("text/javascript", r!!.mimeType)
        assertNull(routes().resolve("/module/KJV/missing.js"))
        assertNull(routes().resolve("/module/KJV/sub"))
    }

    @Test fun fontsCssIsAnEmptyStylesheetForAnInstalledBook() {
        val css = assertNotNull(routes().resolve("/fonts/KJV/fonts.css"))
        assertEquals("text/css", css.mimeType)
        assertEquals("", css.text())
        assertNull(routes().resolve("/fonts/NOPE/fonts.css"))
        assertEquals("font/woff2", routes().resolve("/fonts/KJV/css/extra.woff2")!!.mimeType)
    }

    @Test fun onlySignedFeaturesServed() {
        assertEquals(book / "feat.js", routes().resolve("/features/KJV/feat.js").file())
        assertNull(routes().resolve("/features/KJV/unsigned.js"))
        assertNull(routes(verify = { _, _ -> false }).resolve("/features/KJV/feat.js"))
        // A platform without a signature verifier serves no feature file at all.
        assertNull(routes(verify = null).resolve("/features/KJV/feat.js"))
    }

    @Test fun moduleStyleServed() {
        assertEquals(book / "css/my.css", routes().resolve("/module-style/KJV/style.css").file())
        assertEquals(book / "css/extra.woff2", routes().resolve("/module-style/KJV/extra.woff2").file())
        assertNull(routes().resolve("/module-style/KJV/../../../secret.txt"))
        assertNull(routes().resolve("/module-style/NOPE/style.css"))
    }

    @Test fun traversalRefused() {
        val epub = FakeEpub()
        val r = routes(epub = epub)
        for (p in listOf(
            "/module/KJV/../../secret.txt", "/module/KJV/..\\..\\secret.txt", "/module/KJV/sub/../../../secret.txt",
            "/module/KJV//$secret", "/module/KJV/./pic.png", "/module/KJV/pic.png\u0000.js", "/module/../secret.txt",
            "/features/KJV/../../secret.txt", "/fonts/KJV/../../secret.txt", "/background/../secret.txt",
            "/epub/../secret.txt", "/epub/OEBPS/../../secret.txt", "/epub//etc/passwd", "/epub/OEBPS\\..\\x",
            "/module-style/epub/Epub-X/../secret.txt", "/module-style/epub/../style.css",
        )) assertNull(r.resolve(p), p)
        assertEquals(emptyList(), epub.asked, "a refused path never reaches the EPUB backend")
    }

    @Test fun symlinkLeavingTheBookRefused() {
        fs.createSymlink(book / "link.txt", secret)
        fs.createSymlink(book / "linkdir", tmp)
        fs.createSymlink(book / "css/style-link.css", secret)
        val r = routes()
        assertNull(r.resolve("/module/KJV/link.txt"))
        assertNull(r.resolve("/module/KJV/linkdir/secret.txt"))
        assertNull(r.resolve("/module-style/KJV/style-link.css"))
        assertNotNull(r.resolve("/module/KJV/pic.png"))
    }

    @Test fun symlinkInsideTheBookServed() {
        fs.createSymlink(book / "alias.png", "pic.png".toPath())
        assertEquals(book / "alias.png", routes().resolve("/module/KJV/alias.png").file())
    }

    @Test fun volumeLikeSegmentRefusedAndDoubleSlashTolerated() {
        assertNull(routes().resolve("/module/KJV/C:pic.png"))
        assertEquals(book / "sub/a.js", routes().resolve("/module/KJV/sub//a.js").file())
    }

    @Test fun unknownModuleAndRoute404() {
        val r = routes()
        for (p in listOf("/module/NOPE/pic.png", "/features/NOPE/feat.js", "/fonts/NOPE/a.ttf", "/module//pic.png",
            "/unknown/KJV/pic.png", "/", "", "/module", "/module/KJV", "/module/KJV/")) assertNull(r.resolve(p), p)
    }

    @Test fun epubWithoutSourceIs404AndLoggedOncePerRoute() {
        val r = routes()
        assertNull(r.resolve("/epub/OEBPS/a.png"))
        assertNull(r.resolve("/epub/OEBPS/b.png"))
        assertNull(r.resolve("/module-style/epub/Epub-X/ch1/style.css"))
        assertEquals(2, logs.size, logs.toString())
    }

    @Test fun epubResourceOfTheCurrentDocumentServed() {
        val epub = FakeEpub()
        val r = routes(epub = epub).resolve("/epub/OEBPS/images/pic.png")
        assertEquals("image/png", r!!.mimeType)
        assertEquals("img", r.text())
        assertNull(routes(epub = epub).resolve("/epub/OEBPS/images/missing.png"))
    }

    @Test fun epubStylesheetServedWithoutColoursBodyAndLineHeight() {
        val r = routes(epub = FakeEpub()).resolve("/module-style/epub/Epub-X/ch1/style.css")
        assertEquals("text/css", r!!.mimeType)
        assertEquals(" p {  font-weight: bold;  }", r.text())
        assertNull(routes(epub = FakeEpub()).resolve("/module-style/epub/Epub-X/nokey/style.css"))
    }

    @Test fun epubBookResourceServedForModuleStyle() {
        val r = routes(epub = FakeEpub()).resolve("/module-style/epub/Epub-X/OEBPS/images/pic.png")
        assertEquals("img", r.text())
        assertNull(routes(epub = FakeEpub()).resolve("/module-style/epub/Other/OEBPS/images/pic.png"))
    }

    @Test fun mimeTypes() {
        assertEquals("text/css", pageAssetMimeType("a.CSS"))
        assertEquals("image/png", pageAssetMimeType("x/y.png"))
        assertEquals("application/octet-stream", pageAssetMimeType("noext"))
        assertTrue(pageAssetMimeType("a.xhtml").startsWith("application/xhtml"))
    }
}

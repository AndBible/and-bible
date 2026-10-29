package net.bible.desktop.webkitgtk

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BundleSchemeHandlerTest {
    private lateinit var outer: Path
    private lateinit var dir: Path
    private lateinit var handler: BundleSchemeHandler

    @BeforeTest
    fun setUp() {
        outer = Files.createTempDirectory("bundle-scheme")
        dir = Files.createDirectory(outer.resolve("bundle"))
        Files.writeString(dir.resolve("index.html"), "<html></html>")
        Files.writeString(dir.resolve("a.js"), "1")
        Files.writeString(dir.resolve("s.css"), "b{}")
        Files.createDirectory(dir.resolve("sub"))
        Files.writeString(dir.resolve("sub/x.js"), "2")
        Files.writeString(outer.resolve("secret.txt"), "secret")
        handler = BundleSchemeHandler(dir)
    }

    @AfterTest
    fun tearDown() { outer.toFile().deleteRecursively() }

    private fun mime(uri: String) = handler.resolve(uri)?.second

    @Test
    fun mimeTypes() {
        assertEquals("text/html", mime("$BUNDLE_BASE/index.html"))
        assertEquals("text/javascript", mime("$BUNDLE_BASE/a.js"))
        assertEquals("text/css", mime("$BUNDLE_BASE/s.css"))
        val r = assertNotNull(handler.resolve("$BUNDLE_BASE/sub/x.js"))
        assertEquals(dir.resolve("sub/x.js").toAbsolutePath().normalize(), r.first.toAbsolutePath().normalize())
    }

    @Test
    fun bareBaseAndSlashServeIndex() {
        assertEquals("index.html", handler.resolve(BUNDLE_BASE)?.first?.fileName?.toString())
        assertEquals("index.html", handler.resolve("$BUNDLE_BASE/")?.first?.fileName?.toString())
    }

    @Test
    fun queryAndFragmentIgnored() {
        assertEquals("text/javascript", mime("$BUNDLE_BASE/a.js?v=1#x"))
    }

    @Test
    fun refusesTraversal() {
        assertNull(handler.resolve("$BUNDLE_BASE/../secret.txt"))
        assertNull(handler.resolve("$BUNDLE_BASE/sub/../../secret.txt"))
        assertNull(handler.resolve("$BUNDLE_BASE/%2e%2e/secret.txt"))
    }

    @Test
    fun refusesOtherOrigins() {
        assertNull(handler.resolve("https://bibleview.andbible.local/index.html"))
        assertNull(handler.resolve("file://${dir.resolve("index.html")}"))
        assertNull(handler.resolve("andbible://bibleview.evil/index.html"))
        assertNull(handler.resolve("andbible://other/index.html"))
    }

    @Test
    fun missingFileIsNull() {
        assertNull(handler.resolve("$BUNDLE_BASE/nope.js"))
    }

    @Test
    fun isAtBundleBaseIsExact() {
        assertTrue(isAtBundleBase(BUNDLE_BASE))
        assertTrue(isAtBundleBase("$BUNDLE_BASE/index.html"))
        assertTrue(isAtBundleBase("$BUNDLE_BASE?x"))
        assertTrue(isAtBundleBase("$BUNDLE_BASE#x"))
        assertFalse(isAtBundleBase(null))
        assertFalse(isAtBundleBase("andbible://bibleview.evil/"))
        assertFalse(isAtBundleBase("andbible://bibleview:8080/"))
        assertFalse(isAtBundleBase("https://bibleview/"))
        assertFalse(isAtBundleBase("andbible2://bibleview/"))
    }
}

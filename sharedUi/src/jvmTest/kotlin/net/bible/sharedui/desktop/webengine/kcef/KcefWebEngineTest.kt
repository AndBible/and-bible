package net.bible.sharedui.desktop.webengine.kcef

import dev.datlag.kcef.KCEFBuilder
import net.bible.sharedui.desktop.webengine.BundleResource
import net.bible.sharedui.desktop.webengine.androidBridgeShim
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** CEF-free tests of the KCEF engine: URL scheme, bundle serving, script ordering, eval replies, natives guard. */
class KcefWebEngineTest {
    private val tmp: Path = Files.createTempDirectory("kcef-test")
    private val bundleDir: Path = tmp.resolve("bundle").createDirectories().also { dir ->
        dir.resolve("index.html").writeText(FIXTURE_HTML)
        dir.resolve("assets").createDirectories()
        dir.resolve("assets/main.js").writeText("console.log('main')")
        dir.resolve("assets/main.css").writeText("body{}")
    }

    @AfterTest
    fun cleanUp() {
        tmp.toFile().deleteRecursively()
    }

    @Test
    fun bundleUrlIsNotFileScheme() {
        val url = KcefWebEngine(tmp.resolve("natives"), bundleDir).bundleUrl("en", true)
        assertTrue(url.startsWith(BUNDLE_ORIGIN), url)
        assertTrue(url.endsWith("index.html?lang=en&night=true"), url)
        assertFalse(url.startsWith("file:"), url)
    }

    @Test
    fun bundleRequestHandlerServesFilesAndRefusesTraversal() {
        val handler = BundleRequestHandler(bundleDir)
        val index = assertNotNull(handler.resolve("$BUNDLE_ORIGIN/index.html"))
        assertEquals(bundleDir.resolve("index.html").toRealPath(), index.file.toRealPath())
        assertEquals("text/html", index.mimeType)
        assertEquals("text/javascript", handler.resolve("$BUNDLE_ORIGIN/assets/main.js")?.mimeType)
        assertEquals("text/css", handler.resolve("$BUNDLE_ORIGIN/assets/main.css")?.mimeType)

        // The query and fragment of the start URL do not change the file.
        assertEquals(index.file, handler.resolve("$BUNDLE_ORIGIN/index.html?lang=en&night=true#x")?.file)

        Files.writeString(tmp.resolve("secret"), "s")
        assertNull(handler.resolve("$BUNDLE_ORIGIN/../secret"))
        assertNull(handler.resolve("$BUNDLE_ORIGIN/assets/../../secret"))
        assertNull(handler.resolve("$BUNDLE_ORIGIN/%2e%2e/secret"))
        assertNull(handler.resolve("$BUNDLE_ORIGIN/assets/%2E%2E/%2E%2E/secret"))
        assertNull(handler.resolve("https://example.org/index.html"))
        assertNull(handler.resolve("$BUNDLE_ORIGIN.evil.org/index.html"), "a look-alike host is another origin")
        assertNull(handler.resolve("$BUNDLE_ORIGIN/missing.js"))
        assertNull(handler.resolve("$BUNDLE_ORIGIN/assets"), "a directory is not served")

        // Requests the handler refuses at its origin are still recognised as ours (served as 404, never fetched).
        assertTrue(handler.isBundleUrl("$BUNDLE_ORIGIN/../secret"))
        assertFalse(handler.isBundleUrl("$BUNDLE_ORIGIN.evil.org/x"))
    }

    @Test
    fun textResourcesDeclareUtf8() {
        val handler = BundleRequestHandler(bundleDir)
        assertEquals("text/html; charset=utf-8", handler.resolve("$BUNDLE_ORIGIN/index.html")?.contentType)
        assertEquals("text/javascript; charset=utf-8", handler.resolve("$BUNDLE_ORIGIN/assets/main.js")?.contentType)
        assertEquals("font/woff2", BundleResource(bundleDir, BundleRequestHandler.mimeTypeFor("a.woff2")).contentType)
    }

    /** The ordering pin: the shim is the first script of the served document, ahead of every page script. */
    @Test
    fun documentStartScriptsPrecedeEveryPageScript() {
        val shim = androidBridgeShim(KcefWebEngine.CEF_POST_MESSAGE_JS, "[\"en\"]")
        val handler = BundleRequestHandler(bundleDir, listOf(shim, "window.__second = 1;"))
        val html = handler.body(assertNotNull(handler.resolve("$BUNDLE_ORIGIN/index.html"))).decodeToString()
        val shimAt = html.indexOf("window.androidBridge = {")
        assertTrue(shimAt >= 0, html)
        assertTrue(shimAt < html.indexOf("window.__second"), "documentStartScripts keep their order")
        val firstPageScript = html.indexOf("<script type=\"module\"")
        assertTrue(shimAt < firstPageScript, html)
        // Only the injected element precedes the page's first script.
        assertEquals(1, Regex("<script").findAll(html.substring(0, firstPageScript)).count(), html)
        // The doctype still comes first (after the comment), so the page stays in standards mode.
        assertTrue(html.indexOf("<!DOCTYPE html>") < html.indexOf("<script"), html)
        // Non-HTML files are served untouched.
        val js = handler.resolve("$BUNDLE_ORIGIN/assets/main.js")!!
        assertEquals("console.log('main')", handler.body(js).decodeToString())
    }

    @Test
    fun injectionWorksOnTheRealBibleViewIndexHtml() {
        val source = File(System.getProperty("repoRoot") ?: ".", "app/bibleview-js/index.html").readText()
        val html = BundleRequestHandler.injectDocumentStartScripts(source, listOf("window.__first = 1;"))
        val injected = html.indexOf("window.__first")
        assertTrue(injected > html.indexOf("<head>"), html)
        val firstPageScript = Regex("<script").findAll(html).map { it.range.first }.first { it > injected }
        assertTrue(injected < firstPageScript)
        assertEquals(source, html.replace("<script>\nwindow.__first = 1;\n</script>", ""))
    }

    @Test
    fun injectedScriptCannotCloseItsOwnElement() {
        val html = BundleRequestHandler.injectDocumentStartScripts(
            "<html><head></head><body></body></html>",
            listOf("var s = '</script><img src=x>';"),
        )
        assertEquals(1, Regex("</script", RegexOption.IGNORE_CASE).findAll(html).count(), html)
        assertTrue(html.contains("<\\/script><img src=x>"), html)
    }

    @Test
    fun injectionWithoutHeadGoesAfterHtmlOrFirst() {
        assertEquals(
            "<!doctype html><html lang=\"en\"><script>\nX\n</script><body></body></html>",
            BundleRequestHandler.injectDocumentStartScripts("<!doctype html><html lang=\"en\"><body></body></html>", listOf("X")),
        )
        assertEquals("<script>\nX\n</script>plain", BundleRequestHandler.injectDocumentStartScripts("plain", listOf("X")))
        assertEquals("<head><x>", BundleRequestHandler.injectDocumentStartScripts("<head><x>", emptyList()))
    }

    @Test
    fun shutdownIsTerminalSoLaterInitFailsClearly() {
        try {
            KcefWebEngine.markShutDownForTest()
            val failure = KcefWebEngine.init(tmp.resolve("whatever")).exceptionOrNull()
            assertNotNull(failure)
            assertTrue("shut down" in failure.message!!, failure.message)
        } finally {
            KcefWebEngine.resetStateForTest()
        }
    }

    @Test
    fun activeLanguagesFollowTheStartUrl() {
        assertEquals("[\"fi\"]", KcefWebEngine.activeLanguagesJson("$BUNDLE_ORIGIN/index.html?lang=fi&night=false"))
        assertEquals("[\"en\"]", KcefWebEngine.activeLanguagesJson("$BUNDLE_ORIGIN/index.html"))
        assertEquals("[\"en\"]", KcefWebEngine.activeLanguagesJson("$BUNDLE_ORIGIN/index.html?lang=x\"]"))
    }

    @Test
    fun initFailsClearlyWithoutBundledNatives() {
        val empty = tmp.resolve("natives/lib").createDirectories()
        val result = KcefWebEngine.init(empty)
        val message = result.exceptionOrNull()?.message
        assertTrue(result.isFailure)
        assertNotNull(message)
        assertTrue(message.contains(empty.toString()), message)
        assertTrue(message.contains("prepareKcefNatives"), message)
        // KCEF's own installer would have wiped and refilled the directory from the network; it was never reached.
        assertTrue(empty.listDirectoryEntries().isEmpty())

        val missing = tmp.resolve("nowhere")
        assertTrue(KcefWebEngine.init(missing).exceptionOrNull()!!.message!!.contains(missing.toString()))
        assertFalse(Files.exists(missing))

        // A lock without the library is not enough either.
        Files.writeString(empty.resolve("install.lock"), "")
        assertTrue(KcefWebEngine.init(empty).exceptionOrNull()!!.message!!.contains("no CEF library"))
    }

    /** KCEF builds a ktor HttpClient (OkHttp + content negotiation) in KCEFBuilder; this links it against the aligned ktor. */
    @Test
    fun kcefBuilderLinksAgainstTheAlignedKtor() {
        KCEFBuilder().installDir(tmp.resolve("unused").toFile())
    }

    private companion object {
        const val FIXTURE_HTML = """<!--
  ~ licence comment as in the real bundle
  -->

<!DOCTYPE html>
<html>
  <head>
    <meta charset="utf-8">
    <title>BibleView</title>
    <script type="module" crossorigin src="./assets/main.js"></script>
    <link rel="stylesheet" crossorigin href="./assets/main.css">
  </head>
  <body>
    <script>console.log("index.html loaded");</script>
    <div id="app"></div>
  </body>
</html>
"""
    }

    @Test fun helperExecutableNameFollowsTheOs() {
        assertEquals("jcef_helper.exe", KcefWebEngine.helperExecutableName("Windows 11"))
        assertEquals("jcef_helper", KcefWebEngine.helperExecutableName("Linux"))
        assertEquals("jcef_helper", KcefWebEngine.helperExecutableName("Mac OS X"))
    }
}

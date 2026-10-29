package net.bible.sharedui.webview

import net.bible.sharedcore.bibleview.JsHost
import net.bible.sharedui.app.ports.ExternalLinks
import net.bible.sharedui.app.ports.TextShare
import platform.Foundation.NSFileManager
import platform.Foundation.NSUUID
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.stringWithContentsOfFile
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalForeignApi::class)
class IosReadingWindowSurfaceTest {
    private class Host : JsHost {
        override val isMainThread = true
        override val isAttached = true
        override val mainDispatcher = Dispatchers.Main
        override fun post(block: () -> Unit) = block()
        val evaluated = mutableListOf<String>()
        override fun evaluate(js: String, callback: ((String) -> Unit)?) { evaluated += js }
    }

    private class Links : ExternalLinks {
        override val askBeforeOpening = false
        val opened = mutableListOf<String>()
        override fun open(url: String) { opened += url }
    }

    private class Shares : TextShare {
        val shared = mutableListOf<Pair<String, String>>()
        override fun shareText(text: String, chooserTitle: String) { shared += text to chooserTitle }
    }

    @Test
    fun stopSelectionByDefaultPreservesTheEditorCaret() {
        val host = Host()
        val surface = iosReadingWindowSurface(host)

        // selectionCleared uses the default: a collapsed editor caret must stay in the DOM.
        surface.stopSelection()

        assertTrue(host.evaluated.isEmpty(), "stopping native selection must not remove the editor caret")
    }

    @Test
    fun stopSelectionFalsePreservesTheEditorCaret() {
        val host = Host()
        val surface = iosReadingWindowSurface(host)

        surface.stopSelection(removeRanges = false)

        assertTrue(host.evaluated.isEmpty(), "removeRanges=false must not evaluate a DOM selection clear")
    }

    @Test
    fun stopSelectionTrueClearsDomRangesExactlyOnce() {
        val host = Host()
        val surface = iosReadingWindowSurface(host)

        surface.stopSelection(removeRanges = true)

        assertEquals(listOf("window.getSelection().removeAllRanges()"), host.evaluated)
    }

    @Test
    fun webSearchOpensTheUrlThroughExternalLinks() {
        val links = Links()
        iosReadingWindowSurface(Host(), links = links).openWebSearch("a b")
        assertEquals(listOf("https://www.google.com/search?q=a%20b"), links.opened)
    }

    @Test
    fun shareTextGoesThroughThePort() {
        val shares = Shares()
        iosReadingWindowSurface(Host(), textShare = shares).shareText("verse", "Genesis")
        assertEquals(listOf("verse" to "Genesis"), shares.shared)
    }

    @Test
    fun toastGoesToTheToastSink() {
        val toasts = mutableListOf<String>()
        iosReadingWindowSurface(Host(), toast = { toasts += it }).toast("Copied")
        assertEquals(listOf("Copied"), toasts)
    }

    private fun freshTempDir(): String {
        val dir = NSTemporaryDirectory().trimEnd('/') + "/surface-test-" + NSUUID().UUIDString
        NSFileManager.defaultManager.createDirectoryAtPath(dir, true, null, null)
        return dir
    }

    @Test
    fun shareHtmlWritesAFile() {
        val tmp = freshTempDir()
        val shared = mutableListOf<String>()
        iosReadingWindowSurface(Host(), shareFile = { shared += it }, canShare = { true }, tempDir = { tmp }).shareHtml("<p>Grüße</p>", "My Doc")
        val path = shared.single()
        assertTrue(path.startsWith(tmp) && path.endsWith("/My Doc.html"), path)
        assertEquals("<p>Grüße</p>", NSString.stringWithContentsOfFile(path, NSUTF8StringEncoding, null))
        NSFileManager.defaultManager.removeItemAtPath(tmp, null)
    }

    @Test
    fun aSecondShareRemovesTheFirstFile() {
        val tmp = freshTempDir()
        val shared = mutableListOf<String>()
        val surface = iosReadingWindowSurface(Host(), shareFile = { shared += it }, canShare = { true }, tempDir = { tmp })
        surface.shareHtml("one", "First")
        surface.shareHtml("two", "Second")
        assertEquals(2, shared.size)
        assertFalse(NSFileManager.defaultManager.fileExistsAtPath(shared[0]), "stale file must be gone")
        assertTrue(NSFileManager.defaultManager.fileExistsAtPath(shared[1]))
        NSFileManager.defaultManager.removeItemAtPath(tmp, null)
    }

    /** A share while a sheet is up must not delete the directory (and so the file) that sheet shares. */
    @Test
    fun aShareWhileASheetIsUpTouchesNoFile() {
        val tmp = freshTempDir()
        val shared = mutableListOf<String>()
        var sheetUp = false
        val surface = iosReadingWindowSurface(Host(), shareFile = { shared += it }, canShare = { !sheetUp }, tempDir = { tmp })
        surface.shareHtml("one", "First")
        sheetUp = true
        surface.shareHtml("two", "Second")
        assertEquals(1, shared.size, "the second share is skipped")
        assertTrue(NSFileManager.defaultManager.fileExistsAtPath(shared[0]), "the file of the open sheet is still there")
        assertFalse(NSFileManager.defaultManager.fileExistsAtPath("$tmp/shared-html/Second.html"))
        NSFileManager.defaultManager.removeItemAtPath(tmp, null)
    }

    @Test
    fun fileNameIsSanitisedWithAFallback() {
        assertEquals("a b c", sharedFileName("a/b:c"))
        assertEquals("shared", sharedFileName("  /// "))
        assertEquals("shared", sharedFileName(""))
        assertEquals(60, sharedFileName("x".repeat(100)).length)
    }
}

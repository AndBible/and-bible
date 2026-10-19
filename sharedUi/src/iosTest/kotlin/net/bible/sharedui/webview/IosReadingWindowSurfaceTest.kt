package net.bible.sharedui.webview

import net.bible.sharedcore.bibleview.JsHost
import net.bible.sharedui.app.ports.ExternalLinks
import net.bible.sharedui.app.ports.TextShare
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.stringWithContentsOfFile
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalForeignApi::class)
class IosReadingWindowSurfaceTest {
    private class Host : JsHost {
        override val isMainThread = true
        override val isAttached = true
        override val mainDispatcher = Dispatchers.Main
        override fun post(block: () -> Unit) = block()
        override fun evaluate(js: String, callback: ((String) -> Unit)?) {}
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
    fun webSearchOpensTheUrlThroughExternalLinks() {
        val links = Links()
        IosReadingWindowSurface(Host(), links = links).openWebSearch("a b")
        assertEquals(listOf("https://www.google.com/search?q=a%20b"), links.opened)
    }

    @Test
    fun shareTextGoesThroughThePort() {
        val shares = Shares()
        IosReadingWindowSurface(Host(), textShare = shares).shareText("verse", "Genesis")
        assertEquals(listOf("verse" to "Genesis"), shares.shared)
    }

    @Test
    fun toastGoesToTheToastSink() {
        val toasts = mutableListOf<String>()
        IosReadingWindowSurface(Host(), toast = { toasts += it }).toast("Copied")
        assertEquals(listOf("Copied"), toasts)
    }

    @Test
    fun shareHtmlWritesAFile() {
        val shared = mutableListOf<Pair<String, String>>()
        IosReadingWindowSurface(Host(), shareFile = { path, title -> shared += path to title })
            .shareHtml("<p>Grüße</p>", "My Doc")
        assertEquals(1, shared.size)
        val (path, title) = shared.single()
        assertEquals("My Doc", title)
        assertTrue(path.startsWith(NSTemporaryDirectory()) && path.endsWith(".html"), path)
        val text = NSString.stringWithContentsOfFile(path, NSUTF8StringEncoding, null)
        assertEquals("<p>Grüße</p>", text)
        NSFileManager.defaultManager.removeItemAtPath(path, null)
    }
}

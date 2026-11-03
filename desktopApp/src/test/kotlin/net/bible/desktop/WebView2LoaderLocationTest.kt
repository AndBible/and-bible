package net.bible.desktop

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WebView2LoaderLocationTest {
    private fun loaderDir(parent: Path? = null): Path {
        val dir = if (parent == null) Files.createTempDirectory("webview2") else Files.createDirectories(parent)
        Files.createFile(dir.resolve("WebView2Loader.dll"))
        return dir
    }

    private fun props(vararg pairs: Pair<String, String>): (String) -> String? = mapOf(*pairs)::get

    @Test
    fun devPropertyWins() {
        val dev = loaderDir()
        val resources = Files.createTempDirectory("res")
        loaderDir(resources.resolve("webview2"))
        val result = WebView2LoaderLocation.resolve(
            props("webview2.loaderDir" to dev.toString(), "compose.application.resources.dir" to resources.toString()),
        )
        assertEquals(dev, result.getOrThrow())
    }

    @Test
    fun packagedResourcesDirUsed() {
        val resources = Files.createTempDirectory("res")
        val dir = loaderDir(resources.resolve("webview2"))
        assertEquals(dir, WebView2LoaderLocation.resolve(props("compose.application.resources.dir" to resources.toString())).getOrThrow())
    }

    @Test
    fun missingBothFailsNamingProperties() {
        val result = WebView2LoaderLocation.resolve(props())
        assertTrue(result.isFailure)
        val message = result.exceptionOrNull()!!.message!!
        assertTrue("webview2.loaderDir" in message, message)
        assertTrue("compose.application.resources.dir" in message, message)
    }

    @Test
    fun dirWithoutLoaderFailsNamingDir() {
        val empty = Files.createTempDirectory("empty")
        val result = WebView2LoaderLocation.resolve(props("webview2.loaderDir" to empty.toString()))
        assertTrue(result.isFailure)
        val message = result.exceptionOrNull()!!.message!!
        assertTrue(empty.toString() in message, message)
        assertTrue("WebView2Loader.dll" in message, message)
    }

    @Test
    fun loaderThatIsADirectoryIsRejected() {
        val dir = Files.createTempDirectory("odd")
        Files.createDirectory(dir.resolve("WebView2Loader.dll"))
        assertTrue(WebView2LoaderLocation.resolve(props("webview2.loaderDir" to dir.toString())).isFailure)
    }
}

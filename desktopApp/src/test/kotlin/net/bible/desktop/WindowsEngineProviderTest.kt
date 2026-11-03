package net.bible.desktop

import kotlinx.coroutines.runBlocking
import net.bible.sharedui.desktop.webengine.DesktopWebEngine
import net.bible.sharedui.desktop.webengine.DesktopWebPane
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WindowsEngineProviderTest {
    private class FakeEngine(private val name: String) : DesktopWebEngine {
        var languages: (() -> String)? = null
        var shutdowns = 0
        override fun bundleUrl(lang: String, dark: Boolean) = name
        override fun createPane(id: String, url: String, documentStartScripts: List<String>, onMessage: (String) -> Unit): DesktopWebPane =
            error("No pane expected")
        override fun setActiveLanguages(source: () -> String) { languages = source }
    }

    @Test
    fun `failed webview startup initializes cef once and forwards pending languages`() = runBlocking {
        val cef = FakeEngine("cef")
        var attempts = 0
        val deferred = DeferredWindowsEngine(
            property = "auto",
            startWebView2 = { Result.failure(IllegalStateException("runtime unavailable")) },
            startKcef = { attempts++; cef },
            stopWebView2 = { (it as FakeEngine).shutdowns++ },
            stopKcef = { (it as FakeEngine).shutdowns++ },
        )
        deferred.setActiveLanguages { "[\"fi\"]" }
        deferred.initialize("DIRECT3D")
        assertEquals("cef", deferred.bundleUrl("en", false))
        assertEquals(1, attempts)
        assertEquals("[\"fi\"]", cef.languages?.invoke())
        deferred.shutdown()
        deferred.shutdown()
        assertEquals(1, cef.shutdowns)
    }

    @Test
    fun `closing during blocked startup disposes late engine without starting cef`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val webview = FakeEngine("webview2")
        var cefAttempts = 0
        val deferred = DeferredWindowsEngine(
            property = "auto",
            startWebView2 = {
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS))
                Result.success(webview)
            },
            startKcef = { cefAttempts++; FakeEngine("cef") },
            stopWebView2 = { (it as FakeEngine).shutdowns++ },
            stopKcef = { (it as FakeEngine).shutdowns++ },
        )
        val worker = thread { runCatching { runBlocking { deferred.initialize("DIRECT3D") } } }
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            deferred.shutdown()
        } finally {
            release.countDown()
            worker.join(5000)
        }
        assertTrue(!worker.isAlive)
        assertEquals(1, webview.shutdowns)
        assertEquals(0, cefAttempts)
        assertFailsWith<IllegalStateException> { deferred.bundleUrl("en", false) }
    }

    @Test
    fun `successful webview initialization never starts cef`() = runBlocking {
        val webview = FakeEngine("webview2")
        var cefAttempts = 0
        val deferred = DeferredWindowsEngine(
            property = "auto",
            startWebView2 = { Result.success(webview) },
            startKcef = { cefAttempts++; FakeEngine("cef") },
            stopWebView2 = { (it as FakeEngine).shutdowns++ },
            stopKcef = { (it as FakeEngine).shutdowns++ },
        )
        deferred.initialize("DIRECT3D")
        assertEquals("webview2", deferred.bundleUrl("en", false))
        assertEquals(0, cefAttempts)
        deferred.shutdown()
        assertEquals(1, webview.shutdowns)
    }
    @Test
    fun `only direct3d with an available runtime selects webview2`() {
        assertEquals(WindowsEngineChoice.WEBVIEW2, selectWindowsEngine("auto", "DIRECT3D", true))
        for (renderer in listOf("SOFTWARE", "OPENGL", "UNKNOWN", null)) {
            assertEquals(WindowsEngineChoice.KCEF, selectWindowsEngine("auto", renderer, true))
        }
        assertEquals(WindowsEngineChoice.KCEF, selectWindowsEngine("auto", "DIRECT3D", false))
    }

    @Test
    fun `force kcef overrides direct3d and invalid property is rejected`() {
        assertEquals(WindowsEngineChoice.KCEF, selectWindowsEngine("kcef", "DIRECT3D", true))
        assertFailsWith<IllegalArgumentException> { selectWindowsEngine("other", "DIRECT3D", true) }
    }
}

package net.bible.desktop

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.withTimeout
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
        @Volatile var shutdowns = 0
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
        // The caller is released at shutdown(); the late engine is disposed on the IO worker after release.
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (webview.shutdowns == 0 && System.nanoTime() < deadline) Thread.sleep(10)
        Thread.sleep(50) // leave room for an erroneous second dispose
        assertEquals(1, webview.shutdowns)
        assertEquals(0, cefAttempts)
        assertFailsWith<IllegalStateException> { deferred.bundleUrl("en", false) }
    }

    @Test
    fun `cancelling startup prevents late publication and fallback`() = runBlocking {
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
        val job = launch(Dispatchers.Default) { deferred.initialize("DIRECT3D") }
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            withTimeout(1000) { job.cancelAndJoin() }
        } finally {
            release.countDown()
        }
        withTimeout(5000) { while (webview.shutdowns == 0) kotlinx.coroutines.delay(10) }
        assertEquals(1, webview.shutdowns)
        assertEquals(0, cefAttempts)
        assertFailsWith<IllegalStateException> { deferred.bundleUrl("en", false) }
        Unit
    }

    @Test
    fun `windows honors configured windowed cef for fallback overlays`() {
        val previous = System.getProperty("andbible.kcef.rendering")
        // The real KCEF mode is process-cached; isolate this test and restore the exact previous resolution.
        val mode = Class.forName("net.bible.sharedui.desktop.webengine.kcef.KcefRenderingMode")
            .getDeclaredField("resolved").apply { isAccessible = true }
        val previousMode = mode.get(null)
        try {
            mode.set(null, null)
            System.setProperty("andbible.kcef.rendering", "windowed")
            assertEquals("WINDOW", desktopLayersType(WindowsEngineProvider()))
        } finally {
            mode.set(null, previousMode)
            if (previous == null) System.clearProperty("andbible.kcef.rendering")
            else System.setProperty("andbible.kcef.rendering", previous)
        }
    }

    @Test
    fun `close before initialization prevents either engine starting`() = runBlocking {
        val deferred = DeferredWindowsEngine(
            property = "auto",
            startWebView2 = { error("WebView2 started after close") },
            startKcef = { error("KCEF started after close") },
            stopWebView2 = {},
            stopKcef = {},
        )
        deferred.shutdown()
        assertFailsWith<kotlinx.coroutines.CancellationException> { deferred.initialize("DIRECT3D") }
        Unit
    }

    @Test
    fun `close after webview failure prevents fallback reservation`() = runBlocking {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val cefAttempts = java.util.concurrent.atomic.AtomicInteger()
        val deferred = DeferredWindowsEngine(
            property = "auto",
            startWebView2 = {
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS))
                finished.countDown()
                Result.failure(IllegalStateException("runtime missing"))
            },
            startKcef = { cefAttempts.incrementAndGet(); FakeEngine("cef") },
            stopWebView2 = {},
            stopKcef = {},
        )
        val job = launch(Dispatchers.Default) { deferred.initialize("DIRECT3D") }
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            deferred.shutdown()
            withTimeout(1000) { job.join() }
        } finally {
            release.countDown()
        }
        assertTrue(finished.await(5, TimeUnit.SECONDS))
        assertEquals(0, cefAttempts.get())
        assertFailsWith<IllegalStateException> { deferred.bundleUrl("en", false) }
        Unit
    }

    @Test
    fun `cancelling reserved cef startup cleans up late engine once`() = runBlocking {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val stopped = CountDownLatch(1)
        val cef = FakeEngine("cef")
        val deferred = DeferredWindowsEngine(
            property = "kcef",
            startWebView2 = { error("Forced KCEF must not start WebView2") },
            startKcef = {
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS))
                cef
            },
            stopWebView2 = { error("Wrong engine stopped") },
            stopKcef = { cef.shutdowns++; stopped.countDown() },
        )
        val job = launch(Dispatchers.Default) { deferred.initialize("DIRECT3D") }
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            withTimeout(1000) { job.cancelAndJoin() }
            deferred.shutdown()
        } finally {
            release.countDown()
        }
        assertTrue(stopped.await(5, TimeUnit.SECONDS))
        assertEquals(1, cef.shutdowns)
        assertFailsWith<IllegalStateException> { deferred.bundleUrl("en", false) }
        Unit
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
    fun `fallback failure propagates to readiness caller`() = runBlocking {
        val deferred = DeferredWindowsEngine(
            property = "auto",
            startWebView2 = { Result.failure(IllegalStateException("runtime missing")) },
            startKcef = { throw IllegalStateException("cef missing") },
            stopWebView2 = {},
            stopKcef = {},
        )
        val error = assertFailsWith<IllegalStateException> { deferred.initialize("DIRECT3D") }
        assertEquals("cef missing", error.message)
        deferred.shutdown()
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

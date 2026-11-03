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

    @Test
    fun `loader location failure skips webview start and falls back to cef once`() = runBlocking {
        var webviewStarts = 0
        var searchPaths = 0
        val cef = FakeEngine("cef")
        var cefAttempts = 0
        val logs = mutableListOf<String>()
        val deferred = DeferredWindowsEngine(
            property = "auto",
            startWebView2 = {
                startWebView2WithLoader(
                    locate = { Result.failure(IllegalStateException("loader dir unknown")) },
                    addSearchPath = { _, _ -> searchPaths++ },
                    start = { webviewStarts++; Result.success(FakeEngine("webview2")) },
                    log = { logs += it },
                )
            },
            startKcef = { cefAttempts++; cef },
            stopWebView2 = {},
            stopKcef = {},
        )
        deferred.initialize("DIRECT3D")
        assertEquals(0, webviewStarts)
        assertEquals(0, searchPaths)
        assertEquals(1, cefAttempts)
        assertEquals("cef", deferred.bundleUrl("en", false))
        assertTrue(logs.any { "loader dir unknown" in it && "KCEF" in it }, logs.toString())
    }

    @Test
    fun `loader dir is registered under the jna library name before webview start`() {
        val order = mutableListOf<String>()
        val dir = java.nio.file.Path.of("loader-dir")
        val engine = FakeEngine("webview2")
        val result = startWebView2WithLoader(
            locate = { Result.success(dir) },
            addSearchPath = { lib, path -> order += "path:$lib:$path" },
            start = { order += "start:$it"; Result.success(engine) },
            log = {},
        )
        assertEquals(engine, result.getOrThrow())
        assertEquals(listOf("path:WebView2Loader:$dir", "start:$dir"), order)
    }

    @Test
    fun `search path failure becomes a failed start without starting webview`() {
        var starts = 0
        val result = startWebView2WithLoader(
            locate = { Result.success(java.nio.file.Path.of("x")) },
            addSearchPath = { _, _ -> throw UnsatisfiedLinkError("no jna") },
            start = { starts++; Result.success(FakeEngine("w")) },
            log = {},
        )
        assertTrue(result.isFailure)
        assertEquals(0, starts)
    }

    @Test
    fun `both engines failing presents one dialog naming both causes and log path then rethrows`() = runBlocking {
        val deferred = DeferredWindowsEngine(
            property = "auto",
            startWebView2 = { Result.failure(IllegalStateException("runtime missing")) },
            startKcef = { throw IllegalStateException("cef missing") },
            stopWebView2 = {},
            stopKcef = {},
        )
        val shown = mutableListOf<Pair<String, String>>()
        val logPath = java.nio.file.Path.of("data", "logs", "andbible.log")
        val error = assertFailsWith<IllegalStateException> {
            deferred.initializeReportingFailure("DIRECT3D", logPath, "Error title") { t, m -> shown += t to m }
        }
        assertEquals("cef missing", error.message)
        assertEquals(1, shown.size)
        assertEquals("Error title", shown[0].first)
        assertTrue("runtime missing" in shown[0].second, shown[0].second)
        assertTrue("cef missing" in shown[0].second, shown[0].second)
        assertTrue(logPath.toString() in shown[0].second, shown[0].second)
    }

    @Test
    fun `webview failure with working cef presents no dialog`() = runBlocking {
        val deferred = DeferredWindowsEngine(
            property = "auto",
            startWebView2 = { Result.failure(IllegalStateException("runtime missing")) },
            startKcef = { FakeEngine("cef") },
            stopWebView2 = {},
            stopKcef = {},
        )
        var shown = 0
        deferred.initializeReportingFailure("DIRECT3D", java.nio.file.Path.of("l"), "t") { _, _ -> shown++ }
        assertEquals(0, shown)
        assertEquals("cef", deferred.bundleUrl("en", false))
    }

    @Test
    fun `cancellation and close during init present no dialog`() = runBlocking {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val deferred = DeferredWindowsEngine(
            property = "auto",
            startWebView2 = {
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS))
                Result.failure(IllegalStateException("late failure"))
            },
            startKcef = { throw IllegalStateException("cef late failure") },
            stopWebView2 = {},
            stopKcef = {},
        )
        var shown = 0
        val job = launch(Dispatchers.Default) {
            deferred.initializeReportingFailure("DIRECT3D", java.nio.file.Path.of("l"), "t") { _, _ -> shown++ }
        }
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            withTimeout(1000) { job.cancelAndJoin() }
        } finally {
            release.countDown()
        }
        Thread.sleep(100)
        assertEquals(0, shown)

        val closed = DeferredWindowsEngine("auto", { error("no") }, { error("no") }, {}, {})
        closed.shutdown()
        assertFailsWith<kotlinx.coroutines.CancellationException> {
            closed.initializeReportingFailure("DIRECT3D", java.nio.file.Path.of("l"), "t") { _, _ -> shown++ }
        }
        assertEquals(0, shown)
    }

    @Test
    fun `cancellation exception thrown by the engine itself is reported`() = runBlocking {
        val deferred = DeferredWindowsEngine(
            property = "kcef",
            startWebView2 = { error("no") },
            startKcef = { throw kotlinx.coroutines.CancellationException("engine gave up") },
            stopWebView2 = {},
            stopKcef = {},
        )
        val shown = mutableListOf<String>()
        assertFailsWith<kotlinx.coroutines.CancellationException> {
            deferred.initializeReportingFailure("DIRECT3D", java.nio.file.Path.of("l"), "t") { _, m -> shown += m }
        }
        assertEquals(1, shown.size)
        assertTrue("engine gave up" in shown[0], shown[0])
    }
}

package net.bible.desktop

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.bible.sharedui.assembly.hostStringTable
import net.bible.sharedui.desktop.webengine.DesktopWebEngine
import net.bible.sharedui.desktop.webengine.DesktopWebPane
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WindowsEngineProviderTest {
    private class FakeEngine : DesktopWebEngine {
        var languages: (() -> String)? = null
        @Volatile var shutdowns = 0
        override fun bundleUrl(lang: String, dark: Boolean) = "webview2"
        override fun createPane(id: String, url: String, documentStartScripts: List<String>, onMessage: (String) -> Unit): DesktopWebPane = error("No pane expected")
        override fun setActiveLanguages(source: () -> String) { languages = source }
    }

    @Test
    fun `webview startup failure is reported with runtime link and log path`() = runBlocking {
        val deferred = DeferredWindowsEngine({ Result.failure(IllegalStateException("runtime missing")) }, {})
        val shown = mutableListOf<String>()
        assertFailsWith<IllegalStateException> {
            deferred.initializeReportingFailure("DIRECT3D", Path.of("test.log"), hostStringTable("en")) { _, message -> shown += message }
        }
        assertEquals(1, shown.size)
        assertTrue("https://developer.microsoft.com/microsoft-edge/webview2/" in shown.single())
        assertTrue("test.log" in shown.single())
        assertTrue("WebView2" in shown.single())
    }

    @Test
    fun `unsupported renderers show graphics error without starting webview or suggesting download`() = runBlocking {
        for (renderer in listOf("SOFTWARE", "OPENGL", "UNKNOWN", null)) {
            var starts = 0
            val deferred = DeferredWindowsEngine({ starts++; Result.success(FakeEngine()) }, {})
            val shown = mutableListOf<String>()
            assertFailsWith<UnsupportedWindowsRendererException> {
                deferred.initializeReportingFailure(renderer, Path.of("test.log"), hostStringTable("en")) { _, message -> shown += message }
            }
            assertEquals(0, starts)
            assertTrue("Direct3D" in shown.single())
            assertTrue("test.log" in shown.single())
            assertFalse("https://" in shown.single())
        }
    }

    @Test
    fun `successful startup forwards languages and disposes once`() = runBlocking {
        val engine = FakeEngine()
        val deferred = DeferredWindowsEngine({ Result.success(engine) }, { (it as FakeEngine).shutdowns++ })
        deferred.setActiveLanguages { "[\"fi\"]" }
        deferred.initialize("DIRECT3D")
        assertEquals("webview2", deferred.bundleUrl("en", false))
        assertEquals("[\"fi\"]", engine.languages?.invoke())
        deferred.shutdown()
        deferred.shutdown()
        assertEquals(1, engine.shutdowns)
    }

    @Test
    fun `closing or cancelling startup disposes late engine and suppresses dialog`() = runBlocking {
        for (close in listOf(false, true)) {
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            val stopped = CountDownLatch(1)
            val engine = FakeEngine()
            val deferred = DeferredWindowsEngine({
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS))
                Result.success(engine)
            }, { (it as FakeEngine).shutdowns++; stopped.countDown() })
            var shown = 0
            val job = launch(Dispatchers.Default) {
                runCatching { deferred.initializeReportingFailure("DIRECT3D", Path.of("test.log"), hostStringTable("en")) { _, _ -> shown++ } }
            }
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                if (close) { deferred.shutdown(); withTimeout(1000) { job.join() } }
                else withTimeout(1000) { job.cancelAndJoin() }
            } finally { release.countDown() }
            assertTrue(stopped.await(5, TimeUnit.SECONDS))
            assertEquals(1, engine.shutdowns)
            assertEquals(0, shown)
            assertFailsWith<IllegalStateException> { deferred.bundleUrl("en", false) }
        }
    }

    @Test
    fun `engine cancellation exception is reported but close before start is silent`() = runBlocking {
        val shown = mutableListOf<String>()
        val failed = DeferredWindowsEngine({ Result.failure(CancellationException("engine gave up")) }, {})
        assertFailsWith<CancellationException> {
            failed.initializeReportingFailure("DIRECT3D", Path.of("test.log"), hostStringTable("en")) { _, message -> shown += message }
        }
        assertEquals(1, shown.size)
        val closed = DeferredWindowsEngine({ error("must not start") }, {})
        closed.shutdown()
        assertFailsWith<CancellationException> {
            closed.initializeReportingFailure("DIRECT3D", Path.of("test.log"), hostStringTable("en")) { _, message -> shown += message }
        }
        assertEquals(1, shown.size)
    }

    @Test
    fun `loader registers search path before startup and logs failures without fallback`() {
        val order = mutableListOf<String>()
        val engine = FakeEngine()
        val dir = Path.of("loader-dir")
        val result = startWebView2WithLoader(
            locate = { Result.success(dir) },
            addSearchPath = { lib, path -> order += "path:$lib:$path" },
            start = { order += "start:$it"; Result.success(engine) },
            log = {},
        )
        assertEquals(engine, result.getOrThrow())
        assertEquals(listOf("path:WebView2Loader:$dir", "start:$dir"), order)
        val logs = mutableListOf<String>()
        val failure = startWebView2WithLoader(
            locate = { Result.failure(IllegalStateException("loader missing")) },
            addSearchPath = { _, _ -> error("unexpected") },
            start = { error("unexpected") },
            log = { logs += it },
        )
        assertTrue(failure.isFailure)
        assertTrue("loader missing" in logs.single())
        assertFalse("falling back" in logs.single())
    }
}

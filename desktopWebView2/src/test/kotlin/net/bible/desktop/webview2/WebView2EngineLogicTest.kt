package net.bible.desktop.webview2

import net.bible.sharedui.desktop.webengine.BundleResource
import net.bible.sharedui.desktop.webengine.DesktopPageAssets
import net.bible.sharedui.desktop.webengine.kcef.BundleRequestHandler
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The engine's platform-free rules, with fakes for the loader, the COM creation and the Win32 pump. */
class WebView2EngineLogicTest {
    private val cleanup = ArrayList<() -> Unit>()

    @AfterTest
    fun tearDown() = cleanup.forEach { it() }

    // ---- Review Focus 1: missing or old runtime ----

    @Test
    fun missingRuntimeReportsUnavailable() {
        val none = RuntimeCheck.check { null }
        assertTrue(none.exceptionOrNull() is WebView2UnavailableException)
        assertTrue(none.exceptionOrNull()!!.message!!.contains("no WebView2 runtime"))

        val noLoader = RuntimeCheck.check { throw UnsatisfiedLinkError("WebView2Loader.dll not found") }
        assertTrue(noLoader.exceptionOrNull() is WebView2UnavailableException)
        assertTrue(noLoader.exceptionOrNull()!!.message!!.contains("loader"))

        val old = RuntimeCheck.check { "109.0.1518.140" } // the last runtime for Windows 7/8.1
        assertTrue(old.exceptionOrNull()!!.message!!.contains("older than ${RuntimeCheck.MIN_MAJOR}"))

        val garbage = RuntimeCheck.check { "unknown" }
        assertTrue(garbage.isFailure)

        assertEquals("152.0.4191.66", RuntimeCheck.check { "152.0.4191.66" }.getOrThrow())
        assertEquals("${RuntimeCheck.MIN_MAJOR}.0.1.2", RuntimeCheck.check { "${RuntimeCheck.MIN_MAJOR}.0.1.2" }.getOrThrow())
    }

    // ---- Review Focus 2: user data folder ----

    @Test
    fun userDataFolderLockedFailsSoftly() {
        val primary = Paths.get("C:/Users/u/AppData/Local/AndBible/cache/webview2")
        val fallback = Paths.get("C:/Users/u/AppData/Local/AndBible/cache/webview2-42")
        val inUse = 0x800700AA.toInt() // HRESULT_FROM_WIN32(ERROR_BUSY)

        // The primary folder is held (asynchronous failure): the per-process folder is used.
        val tried = ArrayList<Path>()
        var result: Result<Pair<String, Path>>? = null
        EnvironmentStartup<String>(primary, fallback) { folder, done ->
            tried.add(folder)
            if (folder == primary) done(inUse, null) else done(0, "env")
            0
        }.start { result = it }
        assertEquals(listOf(primary, fallback), tried)
        assertEquals("env" to fallback, result!!.getOrThrow())

        // Both refused, one synchronously and one by throwing: a soft failure naming both, answered once.
        val answers = ArrayList<Result<Pair<String, Path>>>()
        EnvironmentStartup<String>(primary, fallback) { folder, _ ->
            if (folder == primary) inUse else throw IllegalStateException("access denied")
        }.start { answers += it }
        assertEquals(1, answers.size)
        val error = answers.single().exceptionOrNull()
        assertTrue(error is WebView2UnavailableException, "$error")
        assertTrue(error.message!!.contains(hresultString(inUse)) && error.message!!.contains("access denied"), error.message)

        // A creator that reports twice (failed synchronously, then the callback still fires) is answered once.
        val twice = ArrayList<Result<Pair<String, Path>>>()
        EnvironmentStartup<String>(primary, fallback) { folder, done ->
            if (folder == primary) { done(inUse, null); inUse } else { done(0, "late"); 0 }
        }.start { twice += it }
        assertEquals(listOf("late" to fallback), twice.map { it.getOrThrow() })

        // The primary folder works: no fallback is tried.
        val first = ArrayList<Path>()
        EnvironmentStartup<String>(primary, fallback) { folder, done -> first.add(folder); done(0, "ok"); 0 }.start {}
        assertEquals(listOf(primary), first)
    }

    // ---- Review Focus 5: threading ----

    /** A queue loop standing in for the Win32 message pump. */
    private class FakePump : MessagePump {
        private val wakes = LinkedBlockingQueue<Boolean>()
        @Volatile var thread: Thread? = null

        override fun run(drain: () -> Unit) {
            thread = Thread.currentThread()
            drain()
            while (true) {
                if (!wakes.take()) return
                drain()
            }
        }

        override fun wake() { wakes.add(true) }
        override fun quit() { wakes.add(false) }
    }

    @Test
    fun callsFromOtherThreadsAreMarshalled() {
        val pump = FakePump()
        val sta = StaThread("test-sta", pump).also { cleanup += { it.close() } }
        assertTrue(sta.awaitStarted(5_000))
        val staThread = pump.thread!!

        // From several foreign threads at once: every task runs on the STA thread, and call() returns its value.
        val ran = CopyOnWriteArrayList<Thread>()
        val pool = Executors.newFixedThreadPool(4).also { p -> cleanup += { p.shutdownNow() } }
        val futures = (1..20).map { n -> pool.submit<Int> { sta.call { ran += Thread.currentThread(); n * 2 } } }
        assertEquals((1..20).map { it * 2 }, futures.map { it.get(5, TimeUnit.SECONDS) })
        assertTrue(ran.all { it === staThread })
        assertFalse(sta.isCurrent)

        // post() is asynchronous and ordered; a call() from the STA thread itself runs inline (no self-deadlock).
        val order = CopyOnWriteArrayList<Int>()
        val done = CountDownLatch(1)
        sta.post { order += 1 }
        sta.post { order += sta.call { 2 }; done.countDown() }
        assertTrue(done.await(5, TimeUnit.SECONDS))
        assertEquals(listOf(1, 2), order)

        // A failing task's exception reaches the caller; the thread keeps pumping.
        val failure = runCatching { sta.call { error("boom") } }.exceptionOrNull()
        assertEquals("boom", failure?.message)
        assertEquals(7, sta.call { 7 })

        // After close nothing more runs.
        sta.close()
        val late = CountDownLatch(1)
        sta.post { late.countDown() }
        assertFalse(late.await(300, TimeUnit.MILLISECONDS))
    }

    // ---- Trust ----

    @Test
    fun foreignOriginMessagesRefused() {
        assertTrue(Wv2Trust.acceptMessage("https://bibleview.andbible.local/index.html?lang=en&night=false"))
        assertTrue(Wv2Trust.acceptMessage("https://bibleview.andbible.local"))
        listOf(
            null, "",
            "https://bibleview.andbible.local.evil.org/index.html",
            "http://bibleview.andbible.local/index.html",
            "https://bibleview.andbible.local:8443/index.html",
            "https://evil.org/?https://bibleview.andbible.local/",
            "file:///C:/bundle/index.html",
            "about:blank",
        ).forEach { assertFalse(Wv2Trust.acceptMessage(it), "accepted $it") }
        assertTrue(Wv2Trust.allowNavigation("https://bibleview.andbible.local/index.html"))
        assertFalse(Wv2Trust.allowNavigation("https://www.example.org/"))
        assertFalse(Wv2Trust.allowNavigation("https://bibleview.andbible.local.evil.org/"))
    }

    // ---- Evaluate ----

    @Test
    fun evaluateAnsweredExactlyOnce() {
        val delivered = ArrayList<Runnable>()
        val timeouts = ArrayList<Runnable>()
        val replies = EvalReplies(deliver = { delivered += it }, scheduleTimeout = { _, task -> timeouts += task })
        val answers = HashMap<String, MutableList<String>>()
        fun cb(name: String): (String) -> Unit = { answers.getOrPut(name) { ArrayList() } += it }
        fun flush() { delivered.toList().also { delivered.clear() }.forEach(Runnable::run) }

        val ok = replies.register(cb("ok"))
        val failed = replies.register(cb("failed"))
        val nullJson = replies.register(cb("null"))
        val timedOut = replies.register(cb("timedOut"))
        val pendingAtNavigation = replies.register(cb("navigation"))

        replies.complete(ok, S_OK, "{\"a\":1}")
        replies.complete(ok, S_OK, "2") // a second completion is ignored
        replies.complete(failed, 0x80004005.toInt(), "\"ignored\"") // E_FAIL: script threw
        replies.complete(nullJson, S_OK, null)
        timeouts[3].run() // timedOut's timer
        replies.complete(timedOut, S_OK, "\"late\"") // the late real answer is ignored
        replies.failAll() // new document / dispose
        timeouts.forEach(Runnable::run) // all timers fire later: nothing more
        flush()

        assertEquals(
            mapOf(
                "ok" to listOf("{\"a\":1}"),
                "failed" to listOf(EvalReplies.NO_RESULT),
                "null" to listOf(EvalReplies.NO_RESULT),
                "timedOut" to listOf(EvalReplies.NO_RESULT),
                "navigation" to listOf(EvalReplies.NO_RESULT),
            ),
            answers,
        )
        assertEquals(0, replies.pendingCount)
        assertNotNull(pendingAtNavigation)

        // Dispose racing an evaluate: after close() a registration is answered at once, without a timer.
        val timersBefore = timeouts.size
        val beforeClose = replies.register(cb("beforeClose"))
        replies.close()
        replies.register(cb("afterClose"))
        replies.complete(beforeClose, S_OK, "1")
        flush()
        assertEquals<List<String>?>(listOf(EvalReplies.NO_RESULT), answers["beforeClose"])
        assertEquals<List<String>?>(listOf(EvalReplies.NO_RESULT), answers["afterClose"])
        assertEquals(timersBefore + 1, timeouts.size)

        // A timer that cannot be scheduled (cancelled) answers instead of throwing to the caller.
        val broken = EvalReplies(deliver = { it.run() }, scheduleTimeout = { _, _ -> throw IllegalStateException("Timer already cancelled.") })
        var got: String? = null
        broken.register { got = it }
        assertEquals(EvalReplies.NO_RESULT, got)
    }

    // ---- COM objects implemented in Kotlin (JNA callbacks work on Linux too) ----

    @Test
    fun comCallbackAnswersOnlyItsInterfacesAndLivesUntilReleased() {
        val iid = Iid("57213f19-00e6-49fa-8e07-898ea01ecbd2")
        val seen = ArrayList<Long>()
        val callback = ComCallback(iid) { a, b ->
            seen += com.sun.jna.Pointer.nativeValue(a)
            seen += ComCallback.hresult(b).toLong()
            S_OK
        }
        val obj = ComPtr(callback.pointer)

        // QueryInterface: IUnknown and its own IID (each adds a reference), nothing else.
        val asUnknown = obj.query(Iid.IUnknown)
        val asOwn = obj.query(iid)
        assertNotNull(asUnknown)
        assertNotNull(asOwn)
        assertEquals(com.sun.jna.Pointer.nativeValue(callback.pointer), com.sun.jna.Pointer.nativeValue(asOwn.ptr))
        assertNull(obj.query(Iid("00000003-0000-0000-C000-000000000046"))) // IMarshal: never
        assertEquals(S_OK, obj.call(3, com.sun.jna.Pointer(1234), com.sun.jna.Pointer(0x80070005L)))
        assertEquals(listOf(1234L, 0x80070005.toInt().toLong()), seen)

        // Creator's reference + 2 from QueryInterface + AddRef: alive until the last release.
        callback.use { }
        obj.addRef()
        repeat(2) { obj.release(); assertTrue(ComCallback.isLive(callback)) }
        obj.release()
        assertFalse(ComCallback.isLive(callback))
    }

    // ---- Resources ----

    @Test
    fun pageAssetsRoutedThroughDesktopPageAssets() {
        val bundle = Files.createTempDirectory("w2-bundle").also { d -> cleanup += { d.toFile().deleteRecursively() } }
        Files.writeString(bundle.resolve("index.html"), "<!doctype html><html><head><title>t</title></head><body></body></html>")
        Files.writeString(bundle.resolve("app.js"), "console.log(1)")
        val assets = Files.createTempDirectory("w2-assets").also { d -> cleanup += { d.toFile().deleteRecursively() } }
        val paper = assets.resolve("paper.png").also { Files.write(it, byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)) }
        val asked = ArrayList<String>()
        val pageAssets = DesktopPageAssets { path ->
            asked += path
            if (path == "/background/D2CBackground") BundleResource(paper, "image/png", fromBundle = false) else null
        }
        val router = Wv2ResourceRouter(BundleRequestHandler(bundle, listOf("window.__shim = 1;"), pageAssets))

        // A bundle file wins and HTML gets the document-start scripts.
        val html = router.respond("https://bibleview.andbible.local/index.html?lang=en&night=false")!!
        assertEquals(200, html.status)
        assertTrue(html.headers.contains("Content-Type: text/html; charset=utf-8"))
        assertTrue(html.body.decodeToString().contains("<head><script>\nwindow.__shim = 1;\n</script>"))
        assertEquals(200, router.respond("https://bibleview.andbible.local/app.js")!!.status)
        assertTrue(asked.isEmpty())

        // A path no bundle file serves goes to the page assets, with the bare MIME type.
        val image = router.respond("https://bibleview.andbible.local/background/D2CBackground")!!
        assertEquals(200, image.status)
        assertTrue(image.headers.startsWith("Content-Type: image/png\r\n"))
        assertEquals(4, image.body.size)
        assertEquals(listOf("/background/D2CBackground"), asked)

        // Unknown and traversing paths at the origin are 404, never the network; other origins are not ours.
        assertEquals(404, router.respond("https://bibleview.andbible.local/fonts/KJV/fonts.css")!!.status)
        assertEquals(404, router.respond("https://bibleview.andbible.local/../secret.txt")!!.status)
        assertNull(router.respond("https://www.example.org/app.js"))
        assertNull(router.respond("https://bibleview.andbible.local.evil.org/index.html"))
    }
}

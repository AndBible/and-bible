package net.bible.desktop.webkitgtk

import org.junit.AfterClass
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import java.awt.GraphicsEnvironment
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import javax.swing.JFrame
import javax.swing.SwingUtilities
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Live WebKitGTK tests (mirror D1's KcefLiveTest). They need GTK 4 + WebKitGTK 6.0, an X11 `DISPLAY` and
 * `-Pd5.live=true` (system property `d5.live`); otherwise every test is skipped through JUnit 4's `Assume` (so the plain
 * `:desktopWebkitGtk:test` run only compiles them).
 */
class WebKitGtkLiveTest {
    @Test
    fun evaluateCallbackRoundTrips() {
        assertEquals("2", evaluate(a, "1+1"))
        assertEquals("\"ab\"", evaluate(a, "'a'+'b'"))
        assertEquals("\"a\\\"b\"", evaluate(a, "'a\"b'"))
        assertEquals("null", evaluate(a, "undefined"))
        assertEquals("null", evaluate(a, "throw new Error()"))
    }

    @Test
    fun evaluateFromANonUiNonGtkThreadCompletes() {
        val thread = Thread.currentThread()
        assertTrue(!SwingUtilities.isEventDispatchThread() && !thread.name.startsWith("gtk-main"))
        assertEquals("3", evaluate(a, "1+2"))
    }

    @Test
    fun bridgeExistsWhenThePagesFirstScriptRuns() {
        assertEquals("\"object\"", evaluate(a, "window.__bridgeAtFirstScript"))
        assertEquals("\"function\"", evaluate(a, "typeof window.androidBridge.getActiveLanguages"))
        assertEquals("\"[\\\"en\\\"]\"", evaluate(a, "window.androidBridge.getActiveLanguages()"))
    }

    @Test
    fun messagesRouteToTheirOwnPane() {
        messagesA.clear()
        messagesB.clear()
        evaluate(a, "window.androidBridge.postMessage('to-a'); 0")
        evaluate(b, "window.androidBridge.postMessage('to-b'); 0")
        assertEquals("to-a" to true, assertNotNull(messagesA.poll(10, TimeUnit.SECONDS)))
        assertEquals("to-b" to true, assertNotNull(messagesB.poll(10, TimeUnit.SECONDS)))
        assertNull(messagesA.poll(500, TimeUnit.MILLISECONDS))
        assertNull(messagesB.poll(500, TimeUnit.MILLISECONDS))
    }

    private fun evaluate(pane: WebKitGtkPane?, js: String): String {
        val result = ArrayBlockingQueue<String>(1)
        pane!!.evaluate(js) { result.offer(it) } // deliberately from this (non-EDT, non-GTK) thread
        return assertNotNull(result.poll(15, TimeUnit.SECONDS), "no evaluate callback for $js within 15 s")
    }

    companion object {
        private var engine: WebKitGtkEngine? = null
        private var a: WebKitGtkPane? = null
        private var b: WebKitGtkPane? = null
        private val frames = mutableListOf<JFrame>()
        private var tmp: Path? = null
        private val messagesA = ArrayBlockingQueue<Pair<String, Boolean>>(16)
        private val messagesB = ArrayBlockingQueue<Pair<String, Boolean>>(16)

        @BeforeClass
        @JvmStatic
        fun setUp() {
            assumeTrue("d5.live not set", System.getProperty("d5.live") == "true")
            assumeTrue("no DISPLAY", !System.getenv("DISPLAY").isNullOrEmpty())
            assumeTrue("headless AWT", !GraphicsEnvironment.isHeadless())

            val dir = Files.createTempDirectory("webkitgtk-live").also { tmp = it }
            dir.resolve("index.html").writeText(
                "<!DOCTYPE html><html><head><script>window.__bridgeAtFirstScript = typeof window.androidBridge;</script>" +
                    "</head><body>live</body></html>",
            )
            val e = WebKitGtkEngine(dir).also { engine = it }
            // The worker JVM's own exit would run WebKit's C exit handlers on the VM thread, where WebKit aborts
            // (SIGABRT, exit 134, in 3 of 5 runs). By shutdown-hook time the results are reported: end the process from
            // the GTK thread instead (see WebKitGtkEngine.exitProcess).
            Runtime.getRuntime().addShutdownHook(Thread { e.exitProcess(0) })
            SwingUtilities.invokeAndWait {
                a = e.createPane("a", e.bundleUrl("en", false), emptyList()) { messagesA.offer(it to SwingUtilities.isEventDispatchThread()) } as WebKitGtkPane
                b = e.createPane("b", e.bundleUrl("en", true), emptyList()) { messagesB.offer(it to SwingUtilities.isEventDispatchThread()) } as WebKitGtkPane
                // One visible frame per pane: the canvas needs a displayable window for the X reparenting.
                for ((i, p) in listOf(a!!, b!!).withIndex()) {
                    frames += JFrame("webkitgtk-live-$i").apply {
                        contentPane.add(p.component)
                        setSize(400, 300)
                        setLocation(50 + 450 * i, 50)
                        isVisible = true
                    }
                }
            }
            for (p in listOf(a!!, b!!)) {
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
                var loaded = false
                while (!loaded && System.nanoTime() < deadline) {
                    val state = ArrayBlockingQueue<String>(1)
                    p.evaluate("document.readyState") { state.offer(it) }
                    loaded = state.poll(1, TimeUnit.SECONDS) == "\"complete\""
                }
                assertTrue(loaded, "the live page did not load within 30 s")
            }
        }

        @AfterClass
        @JvmStatic
        fun tearDown() {
            // Skipped in setUp: nothing to release, and AWT must not be touched (it may have no display).
            if (engine == null && frames.isEmpty() && tmp == null) return
            SwingUtilities.invokeAndWait {
                a?.dispose()
                b?.dispose()
                frames.forEach { it.dispose() }
            }
            // The engine (GTK thread) stays up for the shutdown hook.
            tmp?.toFile()?.deleteRecursively()
        }
    }
}

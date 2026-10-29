package net.bible.sharedui.desktop.webengine.kcef

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
import kotlin.test.assertTrue

/**
 * Live KCEF tests: they need the CEF natives (system property `kcef.installDir`, set by `:sharedUi:jvmKcefTest` when
 * `desktopApp/build/kcef-natives` exists) and a display (`DISPLAY`, not headless). Skipped otherwise, so the headless
 * `jvmTest` run only compiles them.
 */
class KcefLiveTest {
    @Test
    fun evaluateCallbackRoundTrips() {
        assertEquals("2", evaluate("1+1"))
        assertEquals("\"a\\\"b\"", evaluate("'a\"b'"))
        assertEquals("null", evaluate("throw 1"))
    }

    /** The live half of the ordering pin: the bridge exists when the page's first own script runs. */
    @Test
    fun bridgeExistsWhenThePagesFirstScriptRuns() {
        assertEquals("\"object\"", evaluate("window.__bridgeAtFirstScript"))
        assertEquals("\"function\"", evaluate("typeof window.androidBridge.getActiveLanguages"))
        assertEquals("\"[\\\"en\\\"]\"", evaluate("window.androidBridge.getActiveLanguages()"))
    }

    @Test
    fun postMessageReachesOnMessageOnTheUiThread() {
        messages.clear()
        evaluate("window.androidBridge.postMessage('hello'); 0")
        val got = assertNotNull(messages.poll(10, TimeUnit.SECONDS))
        assertEquals("hello" to true, got)
    }

    private fun evaluate(js: String): String {
        val result = ArrayBlockingQueue<String>(1)
        SwingUtilities.invokeAndWait { pane!!.evaluate(js) { result.offer(it) } }
        return assertNotNull(result.poll(10, TimeUnit.SECONDS), "no evaluate callback for $js within 10 s")
    }

    companion object {
        private var pane: KcefPane? = null
        private var frame: JFrame? = null
        private var tmp: Path? = null
        private val messages = ArrayBlockingQueue<Pair<String, Boolean>>(16)

        @BeforeClass
        @JvmStatic
        fun setUp() {
            val installDir = System.getProperty("kcef.installDir")?.let(Path::of)
            assumeTrue("kcef.installDir not set", installDir != null)
            assumeTrue("no CEF natives in $installDir", KcefWebEngine.nativesProblem(installDir!!) == null)
            assumeTrue("no DISPLAY", !System.getenv("DISPLAY").isNullOrEmpty())
            assumeTrue("headless AWT", !GraphicsEnvironment.isHeadless())

            val dir = Files.createTempDirectory("kcef-live").also { tmp = it }
            dir.resolve("index.html").writeText(
                "<!DOCTYPE html><html><head><script>window.__bridgeAtFirstScript = typeof window.androidBridge;</script>" +
                    "</head><body>live</body></html>",
            )
            val engine = KcefWebEngine(installDir, dir)
            KcefWebEngine.init(installDir).getOrThrow()
            SwingUtilities.invokeAndWait {
                val p = engine.createPane("live", engine.bundleUrl("en", false), emptyList()) { text ->
                    messages.offer(text to SwingUtilities.isEventDispatchThread())
                } as KcefPane
                pane = p
                frame = JFrame("kcef-live").apply {
                    contentPane.add(p.component)
                    setSize(400, 300)
                    isVisible = true
                }
            }
            // Wait for the document to finish loading before the tests evaluate in it.
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
            while (System.nanoTime() < deadline) {
                val state = ArrayBlockingQueue<String>(1)
                SwingUtilities.invokeAndWait { pane!!.evaluate("document.readyState") { state.offer(it) } }
                if (state.poll(1, TimeUnit.SECONDS) == "\"complete\"") return
            }
            assertTrue(false, "the live page did not load within 30 s")
        }

        @AfterClass
        @JvmStatic
        fun tearDown() {
            // Skipped in setUp: nothing to release, and AWT must not be touched (it may have no display).
            if (pane == null && frame == null && tmp == null) return
            SwingUtilities.invokeAndWait {
                pane?.dispose()
                frame?.dispose()
            }
            tmp?.toFile()?.deleteRecursively()
        }
    }
}

package net.bible.desktop.webkitgtk

import net.bible.desktop.spikeLog
import net.bible.sharedui.desktop.webengine.DesktopWebEngine
import net.bible.sharedui.desktop.webengine.DesktopWebPane
import javax.swing.Timer

/**
 * D5 measurement only (`-Dandbible.d5.frameProbe=<dump ms>`): the D1 scroll-pacing method without CDP. When a pane
 * sends `setClientReady`, a `requestAnimationFrame` loop is installed that records `[performance.now(), scrollY]` once per
 * rendered frame; [dumpAtMs] after JVM start every pane's record is printed as `[d5frames] <pane> <json>`.
 */
class FrameProbeEngine(private val inner: DesktopWebEngine, private val dumpAtMs: Long) : DesktopWebEngine by inner {
    override fun createPane(id: String, url: String, documentStartScripts: List<String>, onMessage: (String) -> Unit): DesktopWebPane {
        var pane: DesktopWebPane? = null
        val result = inner.createPane(id, url, documentStartScripts) { text ->
            onMessage(text)
            if (text.contains("setClientReady")) pane?.let { install(id, it) }
        }
        pane = result
        return result
    }

    private fun install(id: String, pane: DesktopWebPane) {
        pane.evaluate(RECORDER, null)
        val delay = (dumpAtMs - java.lang.management.ManagementFactory.getRuntimeMXBean().uptime).coerceAtLeast(0)
        Timer(delay.toInt(), null).apply {
            isRepeats = false
            addActionListener { pane.evaluate("JSON.stringify(window.__d5frames)") { json -> println("[d5frames] $id $json") } }
            start()
        }
        spikeLog("frame probe installed in $id")
    }

    private companion object {
        const val RECORDER = "(function () { if (window.__d5frames) return; window.__d5frames = [];" +
            " function f(t) { window.__d5frames.push([t, window.scrollY]); requestAnimationFrame(f); }" +
            " requestAnimationFrame(f); })();"
    }
}

/**
 * D5 measurement only (`-Dandbible.d5.eval=<js>`): every 1 s after `setClientReady`, prints `[d5eval] <pane> <ms> <json>`
 * with the result of evaluating the expression in each pane (selection, scroll position, focus after real X input).
 */
class EvalProbeEngine(private val inner: DesktopWebEngine, private val js: String) : DesktopWebEngine by inner {
    override fun createPane(id: String, url: String, documentStartScripts: List<String>, onMessage: (String) -> Unit): DesktopWebPane {
        var pane: DesktopWebPane? = null
        val result = inner.createPane(id, url, documentStartScripts) { text ->
            onMessage(text)
            if (text.contains("setClientReady")) pane?.let { p ->
                Timer(1000) {
                    p.evaluate(js) { v -> println("[d5eval] $id ${java.lang.management.ManagementFactory.getRuntimeMXBean().uptime} $v") }
                }.start()
            }
            if (text.contains("selection", ignoreCase = true)) println("[d5bridge] $id ${text.take(160)}")
        }
        pane = result
        return result
    }
}

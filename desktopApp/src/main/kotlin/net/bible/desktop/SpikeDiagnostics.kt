package net.bible.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.Popup
import net.bible.sharedui.desktop.webengine.DesktopWebEngine
import net.bible.sharedui.desktop.webengine.DesktopWebPane
import java.lang.management.ManagementFactory
import javax.swing.Timer

/** Milliseconds since the JVM started (the spike's time base). */
private fun sinceJvmStart(): Long = ManagementFactory.getRuntimeMXBean().uptime

/** Prints a `[spike]` line with the time since JVM start. */
fun spikeLog(message: String) = println("[spike] ${sinceJvmStart()} ms $message")

/**
 * D1 spike only: wraps an engine so every pane creation, the first bridge message of each method, and the
 * moment the first verses exist in the DOM (polled after `setClientReady`) are logged with [spikeLog].
 */
class SpikeTimingEngine(private val inner: DesktopWebEngine) : DesktopWebEngine by inner {
    private var created = 0

    override fun createPane(id: String, url: String, documentStartScripts: List<String>, onMessage: (String) -> Unit): DesktopWebPane {
        created++
        spikeLog("createPane #$created id=$id")
        val seen = mutableSetOf<String>()
        var pane: DesktopWebPane? = null
        val result = inner.createPane(id, url, documentStartScripts) { text ->
            val method = Regex("\"(?:method|name)\"\\s*:\\s*\"(\\w+)\"").find(text)?.groupValues?.get(1) ?: text.take(40)
            if (seen.add(method)) spikeLog("pane $id first bridge message $method")
            onMessage(text)
            if (method == "setClientReady") pane?.let { pollForVerses(id, it) }
        }
        pane = result
        return result
    }

    private fun pollForVerses(id: String, pane: DesktopWebPane) {
        val timer = Timer(25, null)
        timer.addActionListener {
            pane.evaluate("document.querySelectorAll('.verse').length") { count ->
                if (timer.isRunning && (count.toIntOrNull() ?: 0) > 0) {
                    timer.stop()
                    spikeLog("pane $id first verses in DOM ($count)")
                }
            }
        }
        timer.start()
    }
}

/** D1 spike only: a Compose popup or dialog over the panes, to see whether the heavyweight CEF panes cover it. */
@Composable
fun SpikeOverlay(kind: String) {
    val content = @Composable {
        Box(Modifier.size(420.dp, 160.dp).background(Color(0xFFD03030)).padding(16.dp)) {
            BasicText("Compose $kind over the panes", Modifier.align(Alignment.Center))
        }
    }
    when (kind) {
        "popup" -> Popup(alignment = Alignment.Center) { content() }
        "dialog" -> Dialog(onDismissRequest = {}) { content() }
    }
}

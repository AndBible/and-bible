package net.bible.desktop.webview2

import net.bible.sharedui.desktop.webengine.DesktopWebEngine
import net.bible.sharedui.desktop.webengine.DesktopWebPane
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import javax.swing.SwingUtilities

/**
 * Live-check helper (`andbible.w2.evalDir=<dir>`): every second, each `<name>.<paneId>.js` file in the directory is
 * evaluated in that pane, deleted, and its result printed as `[eval] <name> -> <json>`. The W2 counterpart of the CDP reads
 * of the D2c/D3a checks; never used by the app.
 */
class EvalProbeEngine(private val inner: DesktopWebEngine, private val dir: Path) : DesktopWebEngine by inner {
    private val panes = ConcurrentHashMap<String, DesktopWebPane>()

    init {
        Files.createDirectories(dir)
        Thread({
            while (true) {
                Thread.sleep(1000)
                runCatching { poll() }.onFailure { println("[eval] poll failed: $it") }
            }
        }, "w2-eval-probe").apply { isDaemon = true }.start()
    }

    override fun createPane(id: String, url: String, documentStartScripts: List<String>, onMessage: (String) -> Unit): DesktopWebPane =
        inner.createPane(id, url, documentStartScripts, onMessage).also { panes[id] = it }

    private fun poll() {
        Files.list(dir).use { files -> files.filter { it.fileName.toString().endsWith(".js") }.sorted().toList() }.forEach { file ->
            val parts = file.fileName.toString().removeSuffix(".js").split('.')
            val js = Files.readString(file)
            Files.delete(file)
            val pane = panes[parts.getOrNull(1) ?: ""]
            if (pane == null) {
                println("[eval] ${parts[0]} -> no pane ${parts.getOrNull(1)}")
            } else {
                SwingUtilities.invokeLater { pane.evaluate(js) { println("[eval] ${parts[0]} -> $it") } }
            }
        }
    }
}

/**
 * Live-check helper (`andbible.w2.inputScript=true`): adds the D3a reading-input page script, which the PoC root does not
 * pass, and prints every bridge message it posts (`[input] <pane> <message>`), so the D3a contract can be checked on the PoC.
 */
class InputScriptEngine(private val inner: DesktopWebEngine) : DesktopWebEngine by inner {
    override fun createPane(id: String, url: String, documentStartScripts: List<String>, onMessage: (String) -> Unit): DesktopWebPane =
        inner.createPane(id, url, documentStartScripts + net.bible.sharedui.desktop.reading.DesktopInputScript.compose()) { text ->
            if (text.contains("desktop")) println("[input] $id ${text.take(200)}")
            onMessage(text)
        }
}

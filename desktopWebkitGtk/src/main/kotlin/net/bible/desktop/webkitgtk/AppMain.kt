package net.bible.desktop.webkitgtk

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.SwingUtilities
import net.bible.desktop.DesktopEngineProvider
import net.bible.desktop.runDesktopApp
import net.bible.sharedcore.platform.desktop.DesktopPaths
import net.bible.sharedui.desktop.webengine.DesktopWebEngine
import net.bible.sharedui.desktop.webengine.DesktopWebPane
import net.bible.sharedui.desktop.webview.DesktopPageAssetRoutes

/** JVM main class of the real desktop app on WebKitGTK: `net.bible.desktop.webkitgtk.AppMainKt`. */
fun main(args: Array<String>) = appMain(args)

/**
 * Starts the real desktop app ([runDesktopApp]) on the WebKitGTK engine.
 *
 * AWT must stay off GTK 3 in the process that loads GTK 4 (their symbols clash). Before any AWT use: the default Metal
 * look and feel, and `sun.awt.disableGtkFileDialogs` so `java.awt.FileDialog` (the fallback of the shared file
 * dialogs) uses the plain X11 peer instead of the JDK's GTK 3 file chooser. Needs an X11 `DISPLAY` (Xwayland under
 * Wayland); the GTK side restricts GDK to X11 itself (see [GtkMainLoopDriver]).
 */
fun appMain(args: Array<String>) {
    System.setProperty("sun.awt.disableGtkFileDialogs", "true")
    System.setProperty("swing.defaultlaf", "javax.swing.plaf.metal.MetalLookAndFeel")
    if (System.getenv("DISPLAY").isNullOrEmpty()) {
        System.err.println("AndBible desktop (WebKitGTK): DISPLAY is not set: the WebKitGTK engine needs an X11 display (Xwayland under Wayland)")
        kotlin.system.exitProcess(1)
    }
    runDesktopApp(args, WebKitGtkEngineProvider())
}

/**
 * [DesktopEngineProvider] of the WebKitGTK engine. Windowed rendering (the web view is its own X window).
 *
 * The GTK thread outlives [shutdown]: WebKit's C exit handlers must run on it, so the process ends through
 * [exitProcess] (libc `exit` on the GTK thread), never through a JVM exit.
 */
internal class WebKitGtkEngineProvider(
    private val engineFactory: (Path, (String) -> Unit) -> ExitableEngine = { bundleDir, log ->
        System.getProperty(EVAL_DIR_PROPERTY)?.let { dir ->
            val real = WebKitGtkEngine(bundleDir, log, DesktopPageAssetRoutes.koinBound())
            DirEvalProbeEngine(real, Paths.get(dir), real)
        } ?: WebKitGtkEngine(bundleDir, log, DesktopPageAssetRoutes.koinBound())
    },
    private val fallbackExit: (Int) -> Nothing = { kotlin.system.exitProcess(it) },
) : DesktopEngineProvider {
    @Volatile
    private var engine: ExitableEngine? = null
    private val shutDown = AtomicBoolean(false)

    override val windowedRendering: Boolean get() = true

    override fun create(bundleDir: Path, paths: DesktopPaths, log: (String) -> Unit): DesktopWebEngine =
        engineFactory(bundleDir, log).also { engine = it }

    /** Panes are already disposed. The GTK loop deliberately keeps running until [exitProcess]. Idempotent. */
    override fun shutdown() {
        shutDown.set(true)
    }

    override fun exitProcess(status: Int): Nothing {
        val e = engine ?: fallbackExit(status)
        e.exitProcess(status)
    }

    internal val isShutDown: Boolean get() = shutDown.get()

    internal companion object {
        const val EVAL_DIR_PROPERTY = "andbible.d5.evalDir"
    }
}

/**
 * Opt-in live-check helper (`andbible.d5.evalDir=<dir>`, never active otherwise): every second each
 * `<name>.<paneId>.js` file in [dir] is evaluated in that pane, deleted, and its result printed as
 * `[eval] <name> -> <json>`. Lets a developer read DOM state of the real panes.
 */
internal class DirEvalProbeEngine(
    private val inner: DesktopWebEngine,
    private val dir: Path,
    private val exit: ExitableEngine,
) : DesktopWebEngine by inner, ExitableEngine {
    private val panes = ConcurrentHashMap<String, DesktopWebPane>()

    init {
        Files.createDirectories(dir)
        Thread({
            while (true) {
                Thread.sleep(1000)
                runCatching { poll() }.onFailure { println("[eval] poll failed: $it") }
            }
        }, "d5-eval-probe").apply { isDaemon = true }.start()
    }

    override fun createPane(id: String, url: String, documentStartScripts: List<String>, onMessage: (String) -> Unit): DesktopWebPane =
        inner.createPane(id, url, documentStartScripts, onMessage).also { panes[id] = it }

    override fun exitProcess(status: Int): Nothing = exit.exitProcess(status)

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

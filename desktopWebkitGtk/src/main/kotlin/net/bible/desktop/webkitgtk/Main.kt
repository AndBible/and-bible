package net.bible.desktop.webkitgtk

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import kotlinx.coroutines.delay
import net.bible.desktop.BibleViewBundle
import net.bible.desktop.DesktopPocRoot
import net.bible.desktop.DesktopWebSession
import net.bible.desktop.PocLaunchOptions
import net.bible.desktop.SpikeOverlay
import net.bible.desktop.SpikeTimingEngine
import net.bible.desktop.spikeLog
import java.nio.file.Files
import javax.swing.SwingUtilities
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.system.exitProcess

/**
 * Starts the desktop PoC on the WebKitGTK engine (D5 experiment). The scenario, theme and spike diagnostics come from
 * the `andbible.poc.*` properties ([PocLaunchOptions]), as in `:desktopApp`.
 *
 * AWT must not load GTK 3 into the process that loads GTK 4 (their symbols clash): keep AWT off GTK, i.e. no GTK
 * look-and-feel and no `FileDialog` or JDK GTK file chooser. Needs an X11 `DISPLAY` (Xwayland under Wayland).
 */
fun main() {
    if (System.getenv("DISPLAY").isNullOrEmpty()) fail("DISPLAY is not set: the WebKitGTK engine needs an X11 display (Xwayland under Wayland)")
    val options = runCatching { PocLaunchOptions.from(System::getProperty) }.getOrElse { fail(it.message ?: it.toString()) }

    val bundleRoot = Files.createTempDirectory("andbible-bibleview")
    val index = BibleViewBundle.extractTo(bundleRoot)
    val engine = WebKitGtkEngine(index.parent)
    val session = DesktopWebSession(
        engine = if (options.spikeLog) SpikeTimingEngine(engine) else engine,
        shutdownEngine = engine::close,
        cleanUp = { bundleRoot.toFile().deleteRecursively() },
    )
    val documentJson = BibleViewBundle.pocDocumentJson()

    application {
        // Two-phase close, as in :desktopApp: take the panes out of the composition first (their heavyweight canvases
        // must not be removed from an already-disposed window), then close the session and exit.
        var closing by remember { mutableStateOf(false) }
        if (closing) {
            LaunchedEffect(Unit) {
                withFrameNanos { }
                withFrameNanos { }
                repeat(2) { suspendCoroutine<Unit> { c -> SwingUtilities.invokeLater { c.resume(Unit) } } }
                session.close()
                exitApplication()
            }
        }
        Window(
            title = "AndBible (WebKitGTK)",
            onCloseRequest = { closing = true },
        ) {
            var dark by remember { mutableStateOf(options.dark) }
            options.themeFlipMs?.let { period ->
                LaunchedEffect(Unit) {
                    while (true) {
                        delay(period)
                        dark = !dark
                        if (options.spikeLog) spikeLog("theme flip -> dark=$dark")
                    }
                }
            }
            Box {
                if (!closing) {
                    DesktopPocRoot(documentJson, session.webViews.factory, options.scenario, dark)
                    options.overlay?.let { SpikeOverlay(it) }
                }
            }
        }
    }
}

private fun fail(message: String): Nothing {
    System.err.println("AndBible desktop (WebKitGTK): $message")
    exitProcess(1)
}

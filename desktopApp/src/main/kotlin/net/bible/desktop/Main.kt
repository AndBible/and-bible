package net.bible.desktop

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
import net.bible.sharedui.desktop.webengine.kcef.KcefWebEngine
import java.nio.file.Files
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.system.exitProcess
import javax.swing.SwingUtilities

/**
 * Starts the desktop PoC on the KCEF engine. The CEF natives come from [CefNativesLocation]: `kcef.installDir` (set by
 * `./gradlew :desktopApp:run`, built by `:desktopApp:prepareKcefNatives`) or the packaged app's resources dir;
 * `kcef.args` optionally adds Chromium switches (space-separated).
 * The scenario, theme and spike diagnostics come from the `andbible.poc.*` properties ([PocLaunchOptions]).
 */
fun main() {
    val options = runCatching { PocLaunchOptions.from(System::getProperty) }.getOrElse { fail(it.message ?: it.toString()) }
    val installDir = CefNativesLocation.resolve(System::getProperty).getOrElse { fail(it.message ?: it.toString()) }
    val args = System.getProperty("kcef.args").orEmpty().split(' ').filter { it.isNotBlank() }
    KcefWebEngine.init(installDir, args).onFailure { fail(it.message ?: it.toString()) }

    val bundleRoot = Files.createTempDirectory("andbible-bibleview")
    val index = BibleViewBundle.extractTo(bundleRoot)
    val session = DesktopWebSession(
        // Spike-only (D1 measurements), remove in D2: the SpikeTimingEngine wrapper.
        engine = KcefWebEngine(installDir, index.parent).let { if (options.spikeLog) SpikeTimingEngine(it) else it },
        shutdownEngine = KcefWebEngine::shutdown,
        cleanUp = { bundleRoot.toFile().deleteRecursively() },
    )
    val documentJson = BibleViewBundle.pocDocumentJson()

    application {
        // Closing is two-phase. Disposing the window while the browsers' SwingPanels are still in the composition
        // makes the AWT remove the CEF canvases from an already-disposed SkiaLayer ("SkiaLayer is disposed" crash,
        // process left hanging). So onCloseRequest only flips [closing], which takes the panes out of the
        // composition; the engine is then shut down and the application exits.
        var closing by remember { mutableStateOf(false) }
        if (closing) {
            LaunchedEffect(Unit) {
                withFrameNanos { } // the frame that applies the removal of the panes
                withFrameNanos { }
                repeat(2) { suspendCoroutine<Unit> { c -> SwingUtilities.invokeLater { c.resume(Unit) } } } // AWT's own queued removals
                session.close()
                exitApplication()
            }
        }
        Window(
            title = "AndBible",
            onCloseRequest = { closing = true },
        ) {
            // Spike-only (D1 measurements), remove in D2: the theme-flip timer and SpikeOverlay. With no
            // andbible.poc.* properties this is the pre-switch behaviour: SPLIT2, light theme, no overlay.
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
    System.err.println("AndBible desktop: $message")
    exitProcess(1)
}

package net.bible.desktop.webview2

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
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
import java.nio.file.Path
import java.nio.file.Paths
import javax.swing.SwingUtilities
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.system.exitProcess

/**
 * Starts the desktop PoC on the WebView2 engine (W2 experiment, Windows only). The scenario, theme and spike diagnostics come
 * from the `andbible.poc.*` properties ([PocLaunchOptions]), as in `:desktopApp`. Extra W2 switches:
 * - `andbible.w2.app=true`: the real app instead of the PoC ([appMain]);
 * - `andbible.w2.drawer=true`: an in-window Compose "drawer" over the left pane, with a text field and a click counter
 *   (the overlay-input check);
 * - `andbible.w2.userData=<dir>`: the user data folder (default `%LOCALAPPDATA%\AndBible\cache\webview2`);
 * - `andbible.webview2.devtools=true`: Edge DevTools allowed (F12 is off anyway: browser accelerator keys are disabled).
 *
 * `WebView2Loader.dll` must be on the JNA library path (`-Djna.library.path=<dir>`).
 */
fun main() {
    if (System.getProperty("andbible.w2.app") == "true") return appMain()
    if (!System.getProperty("os.name").startsWith("Windows")) fail("the WebView2 engine runs on Windows only")
    // Compose renders its canvas with alpha, so the cleared pane areas show the WebView2 visuals below it.
    System.setProperty("compose.interop.blending", "true")
    val options = runCatching { PocLaunchOptions.from(System::getProperty) }.getOrElse { fail(it.message ?: it.toString()) }

    val bundleRoot = Files.createTempDirectory("andbible-bibleview")
    val index = BibleViewBundle.extractTo(bundleRoot)
    val engine = WebView2Engine.start(index.parent, userDataFolder()).getOrElse {
        fail("WebView2 unavailable (the app would fall back to KCEF): ${it.message}")
    }
    if (options.spikeLog) spikeLog("webview2 environment ready, runtime ${engine.version}")
    val session = DesktopWebSession(
        engine = (if (options.spikeLog) SpikeTimingEngine(engine) else engine)
            .let { e -> if (System.getProperty("andbible.w2.inputScript") == "true") InputScriptEngine(e) else e }
            .let { e -> System.getProperty("andbible.w2.evalDir")?.let { EvalProbeEngine(e, Paths.get(it)) } ?: e },
        shutdownEngine = engine::shutdown,
        cleanUp = { bundleRoot.toFile().deleteRecursively() },
    )
    val documentJson = BibleViewBundle.pocDocumentJson()
    val drawer = System.getProperty("andbible.w2.drawer") == "true"

    application {
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
        Window(title = "AndBible (WebView2)", onCloseRequest = { closing = true }) {
            engine.window = window
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
                    if (drawer) TestDrawer()
                }
            }
        }
    }
}

/** A drawer-like Compose surface over the page: it must cover the page and take the clicks and keys over it. */
@Composable
private fun TestDrawer() {
    var clicks by remember { mutableStateOf(0) }
    var text by remember { mutableStateOf("") }
    Column(Modifier.width(260.dp).fillMaxHeight().background(Color(0xFF2E7D32)).padding(16.dp)) {
        BasicText("Compose drawer over the page", Modifier.padding(bottom = 12.dp))
        BasicText(
            "Clicked $clicks times",
            Modifier.background(Color(0xFFFFF59D)).clickable { clicks++; println("[w2] drawer click $clicks") }.padding(8.dp),
        )
        BasicTextField(
            value = text,
            onValueChange = { text = it; println("[w2] drawer field '$it'") },
            modifier = Modifier.padding(top = 12.dp).background(Color.White).width(220.dp).padding(6.dp),
        )
    }
}

private fun userDataFolder(): Path = System.getProperty("andbible.w2.userData")?.let { Paths.get(it) }
    ?: Paths.get(System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home"), "AndBible", "cache", "webview2")

private fun fail(message: String): Nothing {
    System.err.println("AndBible desktop (WebView2): $message")
    exitProcess(1)
}

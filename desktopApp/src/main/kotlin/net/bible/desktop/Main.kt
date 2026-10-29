package net.bible.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import kotlinx.coroutines.delay
import net.bible.sharedui.desktop.webengine.kcef.KcefWebEngine
import java.nio.file.Files
import java.nio.file.Path
import kotlin.system.exitProcess

/**
 * Starts the desktop PoC on the KCEF engine. `kcef.installDir` (set by `./gradlew :desktopApp:run`) names the CEF
 * natives built by `:desktopApp:prepareKcefNatives`; `kcef.args` optionally adds Chromium switches (space-separated).
 * That absolute build-dir path is a D1 arrangement; packaging the natives inside the distribution is D4a.
 * The scenario, theme and spike diagnostics come from the `andbible.poc.*` properties ([PocLaunchOptions]).
 */
fun main() {
    val options = runCatching { PocLaunchOptions.from(System::getProperty) }.getOrElse { fail(it.message ?: it.toString()) }
    val installDir = System.getProperty("kcef.installDir")?.let { Path.of(it) }
        ?: fail("System property kcef.installDir is not set; start the app with ./gradlew :desktopApp:run")
    val args = System.getProperty("kcef.args").orEmpty().split(' ').filter { it.isNotBlank() }
    KcefWebEngine.init(installDir, args).onFailure { fail(it.message ?: it.toString()) }

    val bundleRoot = Files.createTempDirectory("andbible-bibleview")
    val index = BibleViewBundle.extractTo(bundleRoot)
    val session = DesktopWebSession(
        engine = KcefWebEngine(installDir, index.parent).let { if (options.spikeLog) SpikeTimingEngine(it) else it },
        shutdownEngine = KcefWebEngine::shutdown,
        cleanUp = { bundleRoot.toFile().deleteRecursively() },
    )
    val documentJson = BibleViewBundle.pocDocumentJson()

    application {
        Window(
            title = "AndBible",
            onCloseRequest = {
                session.close()
                exitApplication()
            },
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
                DesktopPocRoot(documentJson, session.webViews.factory, options.scenario, dark)
                options.overlay?.let { SpikeOverlay(it) }
            }
        }
    }
}

private fun fail(message: String): Nothing {
    System.err.println("AndBible desktop: $message")
    exitProcess(1)
}

package net.bible.desktop

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import net.bible.sharedui.desktop.webengine.kcef.KcefWebEngine
import java.nio.file.Files
import java.nio.file.Path
import kotlin.system.exitProcess

/**
 * Starts the desktop PoC on the KCEF engine. `kcef.installDir` (set by `./gradlew :desktopApp:run`) names the CEF
 * natives built by `:desktopApp:prepareKcefNatives`; `kcef.args` optionally adds Chromium switches (space-separated).
 */
fun main() {
    val installDir = System.getProperty("kcef.installDir")?.let { Path.of(it) }
        ?: fail("System property kcef.installDir is not set; start the app with ./gradlew :desktopApp:run")
    val args = System.getProperty("kcef.args").orEmpty().split(' ').filter { it.isNotBlank() }
    KcefWebEngine.init(installDir, args).onFailure { fail(it.message ?: it.toString()) }

    val bundleRoot = Files.createTempDirectory("andbible-bibleview")
    val index = BibleViewBundle.extractTo(bundleRoot)
    val session = DesktopWebSession(
        engine = KcefWebEngine(installDir, index.parent),
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
            DesktopPocRoot(documentJson, session.webViews.factory)
        }
    }
}

private fun fail(message: String): Nothing {
    System.err.println("AndBible desktop: $message")
    exitProcess(1)
}

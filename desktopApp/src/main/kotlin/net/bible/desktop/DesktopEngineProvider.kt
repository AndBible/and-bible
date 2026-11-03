package net.bible.desktop

import androidx.compose.ui.awt.ComposeWindow
import net.bible.sharedcore.platform.desktop.DesktopPaths
import net.bible.sharedui.desktop.webengine.DesktopWebEngine
import net.bible.sharedui.desktop.webengine.kcef.KcefWebEngine
import net.bible.sharedui.desktop.webview.DesktopPageAssetRoutes
import java.nio.file.Files
import java.nio.file.Path

interface DesktopEngineProvider {
    val windowedRendering: Boolean
    fun create(bundleDir: Path, paths: DesktopPaths, log: (String) -> Unit): DesktopWebEngine
    /** Called once the main window has rendered its first frame, before the first web pane is created; an engine whose choice depends on the window (render API) decides here. */
    suspend fun awaitWindowReady(window: ComposeWindow) {}
    fun shutdown()
    fun exitProcess(status: Int) { kotlin.system.exitProcess(status) }
}

class KcefEngineProvider : DesktopEngineProvider {
    override val windowedRendering get() = KcefWebEngine.isWindowedRendering()
    override fun create(bundleDir: Path, paths: DesktopPaths, log: (String) -> Unit): DesktopWebEngine {
        val cache = paths.cefCache
        Files.createDirectories(cache)
        check(Files.isDirectory(cache) && Files.isWritable(cache)) { "the cache directory $cache cannot be used" }
        val installDir = CefNativesLocation.resolve(System::getProperty).getOrThrow()
        val args = System.getProperty("kcef.args").orEmpty().split(' ').filter { it.isNotBlank() }
        KcefWebEngine.init(installDir, args, cacheDir = cache).getOrThrow()
        return KcefWebEngine(installDir, bundleDir, log, pageAssets = DesktopPageAssetRoutes.koinBound())
    }
    override fun shutdown() = KcefWebEngine.shutdown()
}

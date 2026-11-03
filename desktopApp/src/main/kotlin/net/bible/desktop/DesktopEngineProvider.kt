package net.bible.desktop

import androidx.compose.ui.awt.ComposeWindow
import java.nio.file.Files
import java.nio.file.Path
import net.bible.sharedcore.platform.desktop.DesktopPaths
import net.bible.sharedui.desktop.webengine.DesktopWebEngine
import net.bible.sharedui.desktop.webengine.kcef.KcefWebEngine
import net.bible.sharedui.desktop.webview.DesktopPageAssetRoutes

interface DesktopEngineProvider {
    val windowedRendering: Boolean
    fun create(bundleDir: Path, paths: DesktopPaths, log: (String) -> Unit): DesktopWebEngine
    /** Called once the main window has rendered its first frame, before the first web pane is created; an engine whose choice depends on the window (render API) decides here. */
    suspend fun awaitWindowReady(window: ComposeWindow) {}
    fun shutdown()
    fun exitProcess(status: Int): Nothing = kotlin.system.exitProcess(status)
}

class KcefEngineProvider(private val onFailure: (String) -> Nothing = ::fail) : DesktopEngineProvider {
    override val windowedRendering: Boolean get() = KcefWebEngine.isWindowedRendering()

    override fun create(bundleDir: Path, paths: DesktopPaths, log: (String) -> Unit): DesktopWebEngine {
        val cache = paths.cefCache
        val problem = try {
            Files.createDirectories(cache)
            if (Files.isDirectory(cache) && Files.isWritable(cache)) null else "not writable"
        } catch (e: Exception) {
            e.toString()
        }
        if (problem != null) onFailure("the cache directory $cache cannot be used ($problem); check the cache location (XDG_CACHE_HOME on Linux) and its permissions")
        val installDir = CefNativesLocation.resolve(System::getProperty).getOrElse { onFailure(it.message ?: it.toString()) }
        val args = System.getProperty("kcef.args").orEmpty().split(' ').filter { it.isNotBlank() }
        KcefWebEngine.init(installDir, args, cacheDir = cache).onFailure { onFailure(it.message ?: it.toString()) }
        return KcefWebEngine(installDir, bundleDir, log, pageAssets = DesktopPageAssetRoutes.koinBound())
    }
    override fun shutdown() = KcefWebEngine.shutdown()
}

internal fun defaultDesktopEngineProvider(): DesktopEngineProvider =
    if (System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)) WindowsEngineProvider() else KcefEngineProvider()
internal fun desktopLayersType(provider: DesktopEngineProvider): String? = composeLayersTypeFor(provider.windowedRendering)

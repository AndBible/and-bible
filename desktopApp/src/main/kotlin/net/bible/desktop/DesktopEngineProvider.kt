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
    /**
     * Called after the first Compose frame and before any pane is created. Implementations must suspend without
     * blocking the UI thread, be cooperatively cancellable, and never publish an engine after cancellation.
     * Shutdown follows through the normal ordered close sequence.
     */
    suspend fun awaitWindowReady(window: ComposeWindow) {}
    fun shutdown()
    fun exitProcess(status: Int): Nothing
}

class KcefEngineProvider : DesktopEngineProvider {
    override val windowedRendering: Boolean get() = KcefWebEngine.isWindowedRendering()

    override fun create(bundleDir: Path, paths: DesktopPaths, log: (String) -> Unit): DesktopWebEngine {
        val cache = paths.cefCache
        val problem = try {
            Files.createDirectories(cache)
            if (Files.isDirectory(cache) && Files.isWritable(cache)) null else "not writable"
        } catch (e: Exception) {
            e.toString()
        }
        if (problem != null) fail("the cache directory $cache cannot be used ($problem); check the cache location (XDG_CACHE_HOME on Linux) and its permissions")
        val installDir = CefNativesLocation.resolve(System::getProperty).getOrElse { fail(it.message ?: it.toString()) }
        val args = System.getProperty("kcef.args").orEmpty().split(' ').filter { it.isNotBlank() }
        KcefWebEngine.init(installDir, args, cacheDir = cache).onFailure { fail(it.message ?: it.toString()) }
        return KcefWebEngine(installDir, bundleDir, log, pageAssets = DesktopPageAssetRoutes.koinBound())
    }

    override fun shutdown() = KcefWebEngine.shutdown()
    override fun exitProcess(status: Int): Nothing = kotlin.system.exitProcess(status)
}

internal fun defaultDesktopEngineProvider(): DesktopEngineProvider = KcefEngineProvider()
internal fun desktopLayersType(provider: DesktopEngineProvider): String? = composeLayersTypeFor(provider.windowedRendering)

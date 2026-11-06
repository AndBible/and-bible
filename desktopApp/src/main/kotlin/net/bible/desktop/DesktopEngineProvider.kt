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
    /** After the bounded JVM shutdown save; native engines may need to exit on their own thread. Never call JVM exit. */
    fun afterJvmShutdown() {}
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
        val args = System.getProperty("kcef.args").orEmpty().split(' ').filter { it.isNotBlank() } +
            windowedScaleArgs(windowedRendering, DesktopDisplayScale.scale)
        KcefWebEngine.init(installDir, args, cacheDir = cache).onFailure { onFailure(it.message ?: it.toString()) }
        return KcefWebEngine(installDir, bundleDir, log, pageAssets = DesktopPageAssetRoutes.koinBound())
    }
    override fun shutdown() = KcefWebEngine.shutdown()
}

/**
 * Windowed CEF panes are child X windows that pick their own device scale; give them the app's (#4006). Off-screen panes
 * follow the Compose density instead.
 */
internal fun windowedScaleArgs(windowed: Boolean, scale: Double): List<String> =
    if (windowed && scale != 1.0) listOf("--force-device-scale-factor=$scale") else emptyList()

internal fun defaultDesktopEngineProvider(): DesktopEngineProvider =
    if (System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)) WindowsEngineProvider() else KcefEngineProvider()
internal fun desktopLayersType(provider: DesktopEngineProvider): String? = composeLayersTypeFor(provider.windowedRendering)

package net.bible.sharedui.desktop.files

import java.awt.Desktop
import java.net.URI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import net.bible.sharedcore.platform.desktop.dbus.PortalOpenUri
import net.bible.sharedcore.platform.desktop.dbus.PortalOpenResult
import net.bible.sharedui.app.ports.ExternalLinks

class DesktopExternalLinks(
    private val portal: suspend (URI) -> PortalOpenResult = { PortalOpenUri().open(it) },
    private val desktop: (URI) -> Boolean = { uri ->
        try {
            val d = Desktop.getDesktop()
            if (uri.scheme.equals("mailto", true)) d.mail(uri) else d.browse(uri)
            true
        } catch (_: Exception) { false }
    },
    private val xdg: (URI) -> Boolean = { uri ->
        try { ProcessBuilder("xdg-open", uri.toASCIIString()).start(); true } catch (_: Exception) { false }
    },
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    osName: String = System.getProperty("os.name").orEmpty(),
) : ExternalLinks {
    /** The freedesktop portal and `xdg-open` exist on Linux/Unix only; Windows and macOS go straight to `Desktop`. */
    private val freedesktop = osName.lowercase().let { !it.startsWith("windows") && !it.startsWith("mac") }

    override val askBeforeOpening: Boolean get() = false
    override fun open(url: String) {
        val uri = try { URI(url) } catch (_: Exception) { return }
        if (uri.scheme?.lowercase() !in setOf("http", "https", "mailto")) return
        scope.launch {
            if (!freedesktop) { desktop(uri); return@launch }
            val result = try { portal(uri) } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (_: Exception) { PortalOpenResult.UNAVAILABLE } catch (_: LinkageError) { PortalOpenResult.UNAVAILABLE }
            if (result == PortalOpenResult.UNAVAILABLE && !desktop(uri)) xdg(uri)
        }
    }
}

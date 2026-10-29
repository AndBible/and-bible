package net.bible.sharedui.desktop.files

import java.awt.Desktop
import java.net.URI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import net.bible.sharedcore.platform.desktop.dbus.PortalOpenUri
import net.bible.sharedui.app.ports.ExternalLinks

class DesktopExternalLinks(
    private val portal: suspend (URI) -> Boolean = { PortalOpenUri().open(it) },
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
) : ExternalLinks {
    override val askBeforeOpening: Boolean get() = false
    override fun open(url: String) {
        val uri = try { URI(url) } catch (_: Exception) { return }
        if (uri.scheme?.lowercase() !in setOf("http", "https", "mailto")) return
        scope.launch {
            val opened = try { portal(uri) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { false }
            if (!opened && !desktop(uri)) xdg(uri)
        }
    }
}

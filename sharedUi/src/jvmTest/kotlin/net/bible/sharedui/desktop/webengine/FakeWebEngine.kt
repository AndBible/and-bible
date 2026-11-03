package net.bible.sharedui.desktop.webengine

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/** Records what the factory asks of an engine, and lets a test push page messages. */
class FakeWebEngine : DesktopWebEngine {
    val events = mutableListOf<String>()
    val panes = mutableListOf<FakePane>()

    override fun bundleUrl(lang: String, dark: Boolean) = "fake://bundle/index.html?lang=$lang&night=$dark"

    override fun createPane(id: String, url: String, documentStartScripts: List<String>, onMessage: (String) -> Unit): DesktopWebPane {
        events += "scripts:$id"
        events += "load:$id"
        return FakePane(id, url, documentStartScripts, onMessage).also { panes += it }
    }

    fun pane(id: String) = panes.single { it.id == id }

    /** The newest pane of [id] that is not disposed (a crashed pane is replaced, not reused). */
    fun live(id: String) = panes.last { it.id == id && !it.disposed }

    var languagesSource: (() -> String)? = null

    override fun setActiveLanguages(source: () -> String) {
        languagesSource = source
    }
}

class FakePane(
    override val id: String,
    val url: String,
    val scripts: List<String>,
    val onMessage: (String) -> Unit,
) : DesktopWebPane {
    val evaluated = mutableListOf<String>()
    var disposed = false
    val backgrounds = mutableListOf<Int>()
    private var rendererGone: (() -> Unit)? = null
    private var linkNavigation: ((String) -> Unit)? = null

    override fun setBackground(argb: Int) { backgrounds += argb }
    override fun setOnRendererGone(listener: () -> Unit) { rendererGone = listener }

    override fun setOnLinkNavigation(listener: (String) -> Unit) { linkNavigation = listener }

    /** Simulates the page navigating to [url] (a link click), which the engine cancels and reports. */
    fun navigate(url: String) { linkNavigation?.invoke(url) }

    /** Simulates the renderer process dying. */
    fun crash() { rendererGone?.invoke() }

    override val isMainThread = true
    override val isAttached = true
    override val mainDispatcher: CoroutineDispatcher = Dispatchers.Unconfined
    override fun post(block: () -> Unit) = block()
    override fun evaluate(js: String, callback: ((String) -> Unit)?) { evaluated += js }

    @Composable
    override fun Content(modifier: Modifier) { Box(modifier) }

    override fun dispose() { disposed = true }
}

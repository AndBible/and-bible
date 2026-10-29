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
}

class FakePane(
    override val id: String,
    val url: String,
    val scripts: List<String>,
    val onMessage: (String) -> Unit,
) : DesktopWebPane {
    val evaluated = mutableListOf<String>()
    var disposed = false

    override val isMainThread = true
    override val isAttached = true
    override val mainDispatcher: CoroutineDispatcher = Dispatchers.Unconfined
    override fun post(block: () -> Unit) = block()
    override fun evaluate(js: String, callback: ((String) -> Unit)?) { evaluated += js }

    @Composable
    override fun Content(modifier: Modifier) { Box(modifier) }

    override fun dispose() { disposed = true }
}

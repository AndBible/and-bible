package net.bible.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import net.bible.sharedui.desktop.webengine.DesktopWebEngine
import net.bible.sharedui.desktop.webengine.DesktopWebPane
import net.bible.sharedui.poc.PocScenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class DesktopWebSessionTest {
    private class Pane(override val id: String) : DesktopWebPane {
        var disposed = 0
        override val isMainThread = true
        override val isAttached = true
        override val mainDispatcher: CoroutineDispatcher = Dispatchers.Unconfined
        override fun post(block: () -> Unit) = block()
        override fun evaluate(js: String, callback: ((String) -> Unit)?) {}

        @Composable
        override fun Content(modifier: Modifier) = Box(modifier)
        override fun dispose() {
            disposed++
        }
    }

    private class Engine : DesktopWebEngine {
        val panes = mutableListOf<Pane>()
        override fun createPane(id: String, url: String, documentStartScripts: List<String>, onMessage: (String) -> Unit) =
            Pane(id).also { panes += it }
        override fun bundleUrl(lang: String, dark: Boolean) = "fake://index.html"
    }

    @Test
    fun closeDisposesEveryPaneThenTheEngineOnce() = runComposeUiTest {
        val engine = Engine()
        val order = mutableListOf<String>()
        val session = DesktopWebSession(engine, shutdownEngine = {
            order += "engine:" + engine.panes.joinToString { "${it.id}=${it.disposed}" }
        }, cleanUp = { order += "cleanUp" }, log = {})
        setContent { DesktopPocRoot("{}", session.webViews.factory, PocScenario.SPLIT2) }
        onNodeWithTag("pane-w1").assertExists()
        onNodeWithTag("pane-w2").assertExists()

        session.close()
        session.close()

        assertEquals(listOf("w1", "w2"), engine.panes.map { it.id })
        assertTrue(engine.panes.all { it.disposed == 1 })
        // Every pane was already disposed when the engine shut down; cleanup ran last.
        assertEquals(listOf("engine:w1=1, w2=1", "cleanUp"), order)
    }
}

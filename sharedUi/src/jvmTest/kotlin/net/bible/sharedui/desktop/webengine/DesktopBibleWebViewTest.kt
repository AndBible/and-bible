package net.bible.sharedui.desktop.webengine

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import net.bible.sharedui.poc.POC_FIRST_VERSE_ORDINAL
import net.bible.sharedui.poc.PocBibleViewController
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class DesktopBibleWebViewTest {
    private val engine = FakeWebEngine()
    private val factory = DesktopBibleWebViews(engine) {}.factory

    @Test
    fun clientReadyProducesStartupSequence() = runComposeUiTest {
        val c = PocBibleViewController("w1", "{\"a\":1}", false)
        setContent { factory(c, androidx.compose.ui.Modifier) }
        engine.pane("w1").onMessage("""{"method":"setClientReady","args":[]}""")
        val js = engine.pane("w1").evaluated.joinToString("\n")
        val i = listOf("set_config", "clear_document", "add_documents").map { js.indexOf(it) }
        assertTrue(i.all { it >= 0 }, js)
        assertEquals(i.sorted(), i)
    }

    @Test
    fun messagesRouteToTheirOwnPane() = runComposeUiTest {
        val w1 = PocBibleViewController("w1", "{}", false)
        val w2 = PocBibleViewController("w2", "{}", false)
        setContent {
            factory(w1, androidx.compose.ui.Modifier)
            factory(w2, androidx.compose.ui.Modifier)
        }
        engine.pane("w2").onMessage(
            """{"method":"scrolledToOrdinal","args":["k",${POC_FIRST_VERSE_ORDINAL + 4}]}""",
        )
        assertEquals(5, w2.currentVerse.value)
        assertNull(w1.currentVerse.value)
    }

    @Test
    fun paneIsReusedAcrossRecomposition() = runComposeUiTest {
        val c = PocBibleViewController("w1", "{}", false)
        var show by mutableStateOf(true)
        setContent { if (show) factory(c, androidx.compose.ui.Modifier) }
        onNodeWithTag("pane-w1").assertExists()
        show = false
        waitForIdle()
        onNodeWithTag("pane-w1").assertDoesNotExist()
        show = true
        waitForIdle()
        onNodeWithTag("pane-w1").assertExists()
        assertEquals(1, engine.panes.size)
        assertTrue(!engine.pane("w1").disposed)
    }

    @Test
    fun bridgeScriptRunsBeforePageScripts() = runComposeUiTest {
        val c = PocBibleViewController("w1", "{}", true)
        setContent { factory(c, androidx.compose.ui.Modifier) }
        val p = engine.pane("w1")
        // Real document-start ordering is the engine's contract (KDoc) and is pinned in the KCEF engine tests.
        assertEquals(listOf("scripts:w1", "load:w1"), engine.events)
        assertTrue(p.scripts.isEmpty())
        assertEquals("fake://bundle/index.html?lang=en&night=true", p.url)
    }

    @Test
    fun messageDuringCreatePaneIsNotLost() = runComposeUiTest {
        val early = object : DesktopWebEngine by engine {
            override fun createPane(id: String, url: String, documentStartScripts: List<String>, onMessage: (String) -> Unit): DesktopWebPane {
                onMessage("""{"method":"setClientReady","args":[]}""")
                return engine.createPane(id, url, documentStartScripts, onMessage)
            }
        }
        val c = PocBibleViewController("w1", "{}", false)
        val earlyFactory = DesktopBibleWebViews(early) {}.factory
        setContent { earlyFactory(c, androidx.compose.ui.Modifier) }
        assertTrue(engine.pane("w1").evaluated.any { "set_config" in it })
    }

    @Test
    fun shimDefinesBothMembers() {
        val s = androidBridgeShim("window.__post", "[\"en\",\"fi\"]")
        assertTrue("postMessage" in s && "window.__post" in s)
        assertTrue("getActiveLanguages" in s && "[\"en\",\"fi\"]" in s)
        assertTrue("it's" in androidBridgeShim("p", "[\"it's\"]").replace("\\'", "'"))
    }

    @Test
    fun malformedMessageDoesNotThrow() = runComposeUiTest {
        val c = PocBibleViewController("w1", "{}", false)
        setContent { factory(c, androidx.compose.ui.Modifier) }
        engine.pane("w1").onMessage("not json")
    }
}

package net.bible.sharedui.desktop.webview

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlinx.coroutines.test.TestScope
import net.bible.sharedcore.bibleview.BibleViewJsChannel
import net.bible.sharedcore.bibleview.UnimplementedBibleBridge
import net.bible.sharedcore.window.WindowLayoutState
import net.bible.sharedcore.window.WindowSnapshot
import net.bible.sharedcore.window.WindowStateValue
import net.bible.sharedui.desktop.webengine.DesktopWebEngine
import net.bible.sharedui.desktop.webengine.DesktopWebPane
import net.bible.sharedui.desktop.webengine.FakeWebEngine
import net.bible.sharedui.desktop.webengine.NativePaneAttachment
import net.bible.sharedui.reading.SplitContent
import net.bible.sharedui.reading.bridge.ReadingNavigator
import net.bible.sharedui.reading.bridge.ReadingWindowSurface
import net.bible.sharedui.reading.session.BibleWindowSession
import java.util.ArrayDeque
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class WorkspaceNativeAttachOrderTest {
    private data class Mount(val serial: Int)

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun reusedPaneRemainsAttachedAcrossWorkspaceSwitches() = runComposeUiTest {
        val events = mutableListOf<String>()
        val queue = ArrayDeque<Pair<String, () -> Unit>>()
        val attached = mutableMapOf<String, Boolean>()
        var nextMount = 0
        fun post(event: String, action: () -> Unit) {
            events += "enqueue $event"
            queue.addLast(event to action)
        }
        fun drain(stage: String, expected: Set<String>) {
            while (queue.isNotEmpty()) {
                val (event, action) = queue.removeFirst()
                events += "execute $event"
                action()
            }
            events += "$stage attached=$attached expected=$expected"
            assertEquals(expected, attached.filterValues { it }.keys.toSet(), events.joinToString("\n"))
        }

        val fake = FakeWebEngine()
        val nativeEngine = object : DesktopWebEngine by fake {
            override fun createPane(id: String, url: String, documentStartScripts: List<String>, onMessage: (String) -> Unit): DesktopWebPane {
                val delegate = fake.createPane(id, url, documentStartScripts, onMessage)
                return object : DesktopWebPane by delegate {
                    private val attachment = NativePaneAttachment { action -> post("native $id", action) }
                    override fun dispose() {
                        attachment.close { attached[id] = false }
                        delegate.dispose()
                    }

                    @Composable
                    override fun Content(modifier: Modifier) {
                        val mount = remember { Mount(++nextMount) }
                        DisposableEffect(Unit) {
                            events += "attach $id mount=$mount"
                            attachment.attach(mount) { attached[id] = true }
                            onDispose {
                                events += "detach $id mount=$mount"
                                attachment.detach(mount) { attached[id] = false }
                            }
                        }
                        Box(modifier.onGloballyPositioned { coordinates ->
                            val position = coordinates.positionInWindow()
                            val size = coordinates.size
                            attachment.update(mount) { events += "bounds $id mount=$mount position=$position size=$size" }
                        })
                    }
                }
            }
        }
        val engine = DesktopReadingPaneEngine(nativeEngine, {})
        val bridge = object : UnimplementedBibleBridge({}, {}) {}
        val surface = object : ReadingWindowSurface {
            override fun copyText(label: String?, text: String) {}
            override fun shareText(text: String, subject: String?) {}
            override fun shareHtml(html: String, title: String) {}
            override fun stopSelection(removeRanges: Boolean) {}
            override fun toast(text: String) {}
            override fun openWebSearch(query: String) {}
        }
        val navigator = java.lang.reflect.Proxy.newProxyInstance(
            ReadingNavigator::class.java.classLoader, arrayOf(ReadingNavigator::class.java),
        ) { _, _, _ -> null } as ReadingNavigator
        val sessions = mutableMapOf<String, BibleWindowSession>()
        val factory = desktopReadingPaneFactory(engine, { id ->
            sessions.getOrPut(id) {
                BibleWindowSession(null, BibleViewJsChannel(engine.jsHostFor(id)) {}, { TestScope() }, surface, navigator)
            }
        }, { bridge })
        val ids = mutableStateOf(listOf("w1-study", "w1-bible"))
        setContent {
            val windows = ids.value.map { id ->
                WindowSnapshot(id = id, state = WindowStateValue.VISIBLE, weight = 1f, isVisible = true,
                    isPinMode = true, isSynchronised = false, syncGroup = 0, isLinksWindow = false)
            }
            SplitContent(WindowLayoutState(windows, windows.first().id, null, false, true), {}, { _, _, _, _ -> },
                pane = { factory(it, Modifier.fillMaxSize()) })
        }
        try {
            waitForIdle()
            onNodeWithTag("pane-w1-study").assertExists()
            onNodeWithTag("pane-w1-bible").assertExists()
            val p1 = engine.paneFor("w1-study")
            val p2 = engine.paneFor("w1-bible")
            assertTrue(p1 != null && p2 != null && p1 !== p2)
            runOnIdle { drain("W1 initial", setOf("w1-study", "w1-bible")) }

            runOnIdle {
                engine.release("w1-bible")
                engine.rekey("w1-study", "w2-study")
                sessions["w2-study"] = sessions.getValue("w1-study")
                ids.value = listOf("w2-study")
            }
            waitForIdle()
            onNodeWithTag("pane-w2-study").assertExists()
            onNodeWithTag("pane-w1-study").assertDoesNotExist()
            onNodeWithTag("pane-w1-bible").assertDoesNotExist()
            assertSame(p1, engine.paneFor("w2-study"))
            runOnIdle { drain("W2", setOf("w1-study")) }

            runOnIdle {
                engine.rekey("w2-study", "w1-study")
                sessions["w1-study"] = sessions.getValue("w2-study")
                ids.value = listOf("w1-study", "w1-bible")
            }
            waitForIdle()
            onNodeWithTag("pane-w1-study").assertExists()
            onNodeWithTag("pane-w1-bible").assertExists()
            onNodeWithTag("pane-w2-study").assertDoesNotExist()
            assertSame(p1, engine.paneFor("w1-study"))
            val p3 = engine.paneFor("w1-bible")
            assertTrue(p3 != null && p3 !== p1 && p3 !== p2)
            runOnIdle { drain("W1 return", setOf("w1-study", "w1-bible")) }
        } finally {
            runOnIdle {
                ids.value.forEach(engine::release)
                sessions.values.toSet().forEach(BibleWindowSession::dispose)
            }
            println("Workspace native attach order:\n${events.joinToString("\n")}")
        }
    }
}

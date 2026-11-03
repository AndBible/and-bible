package net.bible.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import java.nio.file.Path
import java.nio.file.Paths
import kotlinx.coroutines.CompletableDeferred
import net.bible.sharedcore.platform.desktop.DesktopPaths
import net.bible.sharedui.desktop.webengine.DesktopWebEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DesktopEngineProviderTest {
    private class FakeProvider(override val windowedRendering: Boolean) : DesktopEngineProvider {
        override fun create(bundleDir: Path, paths: DesktopPaths, log: (String) -> Unit): DesktopWebEngine =
            error("Engine creation must not be needed to select layers")

        override fun shutdown() = error("Engine shutdown must not be needed to select layers")
        override fun exitProcess(status: Int): Nothing = error("Process exit must not be needed to select layers")
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun paneContentWaitsForWindowReadiness() = runComposeUiTest {
        val ready = CompletableDeferred<Unit>()
        setContent {
            DesktopEngineReadyGate(awaitReady = { ready.await() }) {
                Box(Modifier.testTag("pane"))
            }
        }

        onNodeWithTag("pane").assertDoesNotExist()
        ready.complete(Unit)
        waitForIdle()
        onNodeWithTag("pane").assertExists()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun readinessFailureReportsErrorWithoutShowingPane() = runComposeUiTest {
        val failure = IllegalStateException("not ready")
        val failures = mutableListOf<Throwable>()
        setContent {
            DesktopEngineReadyGate(awaitReady = { throw failure }, onFailure = { failures += it }) {
                Box(Modifier.testTag("pane"))
            }
        }
        waitForIdle()
        onNodeWithTag("pane").assertDoesNotExist()
        assertEquals(1, failures.size)
        assertSame(failure, failures.single())
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun closingDuringReadinessCancelsItBeforeEngineShutdown() = runComposeUiTest {
        var visible by mutableStateOf(true)
        val started = CompletableDeferred<Unit>()
        val ready = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        setContent {
            if (visible) DesktopEngineReadyGate(
                awaitReady = {
                    try {
                        started.complete(Unit)
                        ready.await()
                        events += "ready"
                    } finally {
                        events += "cancelled"
                    }
                },
                onFailure = { events += "failure" },
                onCancelled = { events += "shutdown" },
            ) { Box(Modifier.testTag("pane")) }
        }
        waitForIdle()
        assertTrue(started.isCompleted)
        runOnIdle { visible = false }
        waitForIdle()
        ready.complete(Unit)
        waitForIdle()
        assertEquals(listOf("cancelled", "shutdown"), events)
        onNodeWithTag("pane").assertDoesNotExist()
    }

    @Test
    fun engineConstructionUsesInjectedProviderAndArguments() {
        val bundle = Paths.get("/bundle")
        val expectedPaths = DesktopPaths({ null }, "Linux", Paths.get("/home/test"))
        val expectedLog: (String) -> Unit = {}
        val engine = object : DesktopWebEngine {
            override fun createPane(id: String, url: String, documentStartScripts: List<String>, onMessage: (String) -> Unit) =
                error("No pane needed")
            override fun bundleUrl(lang: String, dark: Boolean) = "fake://index.html"
        }
        var calls = 0
        val provider = object : DesktopEngineProvider {
            override val windowedRendering = false
            override fun create(bundleDir: Path, paths: DesktopPaths, log: (String) -> Unit): DesktopWebEngine {
                assertEquals(bundle, bundleDir)
                assertSame(expectedPaths, paths)
                assertSame(expectedLog, log)
                calls++
                return engine
            }
            override fun shutdown() = Unit
            override fun exitProcess(status: Int): Nothing = error("Unexpected exit")
        }
        assertSame(engine, createDesktopEngine(provider, bundle, expectedPaths, expectedLog))
        assertEquals(1, calls)
    }

    @Test
    fun defaultLauncherUsesKcefProvider() {
        assertIs<KcefEngineProvider>(defaultDesktopEngineProvider())
    }

    @Test
    fun heavyweightProviderSelectsWindowLayersWithoutCreatingEngine() {
        assertEquals("WINDOW", desktopLayersType(FakeProvider(windowedRendering = true)))
    }

    @Test
    fun offscreenProviderLeavesComposeLayersAtDefaultWithoutCreatingEngine() {
        assertNull(desktopLayersType(FakeProvider(windowedRendering = false)))
    }
}

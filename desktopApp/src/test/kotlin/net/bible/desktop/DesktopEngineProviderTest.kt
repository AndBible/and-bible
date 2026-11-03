package net.bible.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import java.nio.file.Path
import kotlinx.coroutines.CompletableDeferred
import net.bible.sharedcore.platform.desktop.DesktopPaths
import net.bible.sharedui.desktop.webengine.DesktopWebEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

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

    @Test
    fun cefCacheFailureUsesInjectedHandler() {
        val file = java.nio.file.Files.createTempFile("desktop-cache", ".tmp")
        try {
            val paths = DesktopPaths({ if (it == "XDG_CACHE_HOME") file.toString() else null }, "Linux", file.parent)
            val failure = kotlin.test.assertFailsWith<IllegalStateException> {
                KcefEngineProvider { throw IllegalStateException("handled: $it") }.create(file.parent, paths) {}
            }
            kotlin.test.assertTrue(failure.message.orEmpty().startsWith("handled: the cache directory"))
        } finally {
            java.nio.file.Files.deleteIfExists(file)
        }
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

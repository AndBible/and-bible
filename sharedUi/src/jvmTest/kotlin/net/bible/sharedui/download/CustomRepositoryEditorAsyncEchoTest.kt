package net.bible.sharedui.download

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import net.bible.sharedcore.download.EditorState
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.iosStrings
import net.bible.sharedui.theme.AbTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The controller echoes a field back through `collectAsState`, a dispatch after the keystroke; these tests hold that echo
 * in a queue on the real editor screen. The controller's own writes (paste, validation result) must still show up.
 */
@OptIn(ExperimentalTestApi::class)
class CustomRepositoryEditorAsyncEchoTest {
    private val strings = iosStrings("en")

    private class Harness(val asyncEcho: Boolean) {
        var shown by mutableStateOf(EditorState())
        val queue = ArrayDeque<EditorState.() -> EditorState>()
        val received = mutableListOf<String>()
        fun write(change: EditorState.() -> EditorState) { shown = shown.change() }
        fun echo(sent: String, change: EditorState.() -> EditorState) {
            received += sent
            if (asyncEcho) queue.addLast(change) else write(change)
        }
        fun release() { while (queue.isNotEmpty()) write(queue.removeFirst()) }
        fun releaseOne() { if (queue.isNotEmpty()) write(queue.removeFirst()) }
    }

    private fun androidx.compose.ui.test.ComposeUiTest.show(h: Harness) = setContent {
        CompositionLocalProvider(LocalStrings provides strings) {
            AbTheme(darkTheme = false) {
                CustomRepositoryEditorScreen(
                    state = h.shown,
                    onUrlChange = { v -> h.echo(v) { copy(url = v) } },
                    onPaste = {},
                    onPackageDirChange = { v -> h.echo(v) { copy(packageDirectory = v) } },
                    onSave = {}, onDelete = {}, onUp = {},
                )
            }
        }
    }

    private fun androidx.compose.ui.test.ComposeUiTest.field(label: String) = onNode(hasSetTextAction() and hasText(label))
    private fun SemanticsNodeInteraction.editable() = fetchSemanticsNode().config[SemanticsProperties.EditableText].text

    private fun androidx.compose.ui.test.ComposeUiTest.typeTwo(label: String, first: String, second: String, h: Harness): String {
        val node = field(label)
        node.performClick()
        node.performTextInput(first)
        waitForIdle() // recomposition while the controller echo is still in flight
        node.performTextInput(second)
        runOnIdle { h.release() }
        waitForIdle()
        return node.editable()
    }

    @Test fun urlKeepsBothKeystrokesWhileTheEchoIsDelayed() = runComposeUiTest {
        val h = Harness(asyncEcho = true); show(h)
        assertEquals("ht", typeTwo(strings.repositorySpecification, "h", "t", h))
        assertEquals("ht", h.received.last())
        assertEquals("ht", h.shown.url)
    }

    @Test fun packageDirectoryKeepsBothKeystrokesWhileTheEchoIsDelayed() = runComposeUiTest {
        val h = Harness(asyncEcho = true); show(h)
        assertEquals("/d", typeTwo(strings.packagesDir, "/", "d", h))
        assertEquals("/d", h.received.last())
    }

    @Test fun immediateEchoControlKeepsBothKeystrokes() = runComposeUiTest {
        val h = Harness(asyncEcho = false); show(h)
        assertEquals("ht", typeTwo(strings.repositorySpecification, "h", "t", h))
        assertEquals("/d", typeTwo(strings.packagesDir, "/", "d", h))
    }

    @Test fun controllerWritesStillReachTheFields() = runComposeUiTest {
        val h = Harness(asyncEcho = true); show(h)
        typeTwo(strings.packagesDir, "/", "d", h)
        // Paste writes the URL and validation later resolves the package directory, neither typed in the fields.
        runOnIdle { h.write { copy(url = "https://pasted/repo") } }
        waitForIdle()
        assertEquals("https://pasted/repo", field(strings.repositorySpecification).editable())
        runOnIdle { h.write { copy(packageDirectory = "/resolved") } }
        waitForIdle()
        assertEquals("/resolved", field(strings.packagesDir).editable())
    }

    @Test fun aLateEchoDeliveredAloneNeverOverwritesNewerInput() = runComposeUiTest {
        val h = Harness(asyncEcho = true); show(h)
        val node = field(strings.packagesDir)
        node.performClick()
        node.performTextInput("/")
        waitForIdle()
        node.performTextInput("d")
        waitForIdle()
        // The controller's echoes arrive one at a time, each followed by a recomposition: the first one is stale.
        runOnIdle { h.releaseOne() }
        waitForIdle()
        assertEquals("/d", node.editable())
        runOnIdle { h.releaseOne() }
        waitForIdle()
        assertEquals("/d", node.editable())
        assertEquals("/d", h.shown.packageDirectory)
    }

    @Test fun anOutsideWriteArrivingWhileAnEchoIsStillHeldWinsOnceTheEchoesDrain() = runComposeUiTest {
        val h = Harness(asyncEcho = true); show(h)
        val node = field(strings.packagesDir)
        node.performClick()
        node.performTextInput("/")
        waitForIdle()
        node.performTextInput("d")
        waitForIdle()
        runOnIdle { h.releaseOne() } // "/" echoed; "/d" still held
        waitForIdle()
        assertEquals("/d", node.editable())
        // Validation resolves a directory now. The controller's final value is the resolved one, so the held stale echo
        // never reaches the screen (a StateFlow does not go backwards).
        runOnIdle { h.queue.clear(); h.write { copy(packageDirectory = "/resolved") } }
        waitForIdle()
        assertEquals("/resolved", node.editable())
    }
}

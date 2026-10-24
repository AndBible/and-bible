package net.bible.sharedui.bookmark

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.iosStrings
import net.bible.sharedui.theme.AbTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/** The label name comes back from the bookmark controller through `collectAsState`; the echo is held on the real sheet content. */
@OptIn(ExperimentalTestApi::class)
class LabelIdentitySheetTextTest {
    private val strings = iosStrings("en")

    private class Harness {
        var shown by mutableStateOf("")
        val queue = ArrayDeque<String>()
        val received = mutableListOf<String>()
        fun onName(v: String) { received += v; queue.addLast(v) }
        fun releaseOne() { if (queue.isNotEmpty()) shown = queue.removeFirst() }
        fun releaseAll() { while (queue.isNotEmpty()) shown = queue.removeFirst() }
    }

    private fun androidx.compose.ui.test.ComposeUiTest.show(h: Harness) = setContent {
        CompositionLocalProvider(LocalStrings provides strings) {
            AbTheme(darkTheme = false) {
                LabelIdentitySheetContent(
                    name = h.shown, nameEditable = true, colorArgb = 0xFF2196F3.toInt(), customIcon = null,
                    iconKeys = emptyList(), iconVisible = false,
                    onName = h::onName, onColor = {}, onCustomIcon = {},
                    iconSlot = { _, _ -> },
                )
            }
        }
    }

    private fun androidx.compose.ui.test.ComposeUiTest.field() = onNode(hasSetTextAction() and hasText(strings.labelNameHint))
    private fun androidx.compose.ui.test.SemanticsNodeInteraction.text() = fetchSemanticsNode().config[SemanticsProperties.EditableText].text

    @Test fun nameKeepsBothKeystrokesWhileTheEchoIsDelayed() = runComposeUiTest {
        val h = Harness(); show(h)
        val node = field()
        node.performClick()
        node.performTextInput("a")
        waitForIdle()
        node.performTextInput("b")
        waitForIdle()
        assertEquals("ab", node.text())
        runOnIdle { h.releaseAll() }
        waitForIdle()
        assertEquals(listOf("a", "ab"), h.received)
        assertEquals("ab", node.text())
    }

    @Test fun aStaleEchoDeliveredAloneNeverOverwritesNewerInput() = runComposeUiTest {
        val h = Harness(); show(h)
        val node = field()
        node.performClick()
        node.performTextInput("a")
        waitForIdle()
        node.performTextInput("b")
        waitForIdle()
        runOnIdle { h.releaseOne() }
        waitForIdle()
        assertEquals("ab", node.text())
        runOnIdle { h.releaseOne() }
        waitForIdle()
        assertEquals("ab", node.text())
    }

    @Test fun anOutsideWriteStillReachesTheField() = runComposeUiTest {
        val h = Harness(); show(h)
        val node = field()
        node.performClick()
        node.performTextInput("a")
        waitForIdle()
        runOnIdle { h.releaseAll() }
        waitForIdle()
        runOnIdle { h.shown = "Loaded name" }
        waitForIdle()
        assertEquals("Loaded name", node.text())
    }
}

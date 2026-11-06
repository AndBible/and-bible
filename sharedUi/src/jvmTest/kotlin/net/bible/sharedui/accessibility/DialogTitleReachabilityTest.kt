package net.bible.sharedui.accessibility

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import kotlin.test.Test
import kotlin.test.assertTrue
import net.bible.sharedui.components.AbAlertDialog
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.iosStrings
import net.bible.sharedui.theme.AbTheme

/** A tall M3 title must never consume the message viewport; buttons remain outside both scroll areas. */
@OptIn(ExperimentalTestApi::class)
class DialogTitleReachabilityTest {
    @Test fun tallTitleLeavesScrollableBodyAndFixedAnswersAtAx5() = checkDialog(53f / 17f)
    @Test fun normalFontKeepsTitleBodyAndAnswersReachable() = checkDialog(1f)

    private fun checkDialog(scale: Float) {
        for (dark in listOf(false, true)) for (monochrome in listOf(false, true)) {
            checkDialog(scale, dark, monochrome)
        }
    }

    private fun checkDialog(scale: Float, dark: Boolean, monochrome: Boolean) = runSkikoComposeUiTest(size = Size(390f, 844f)) {
        var confirmed = false
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, scale), LocalStrings provides iosStrings("en")) {
                AbTheme(darkTheme = dark, disableAnimations = true,
                    colorMode = if (monochrome) net.bible.service.common.DisplayColorMode.MONOCHROME else net.bible.service.common.DisplayColorMode.NORMAL) {
                    AbAlertDialog(
                        onDismissRequest = {},
                        title = { Text("Do you want to proceed to download documents?") },
                        text = {
                            Column(Modifier.verticalScroll(rememberScrollState())) {
                                Text("Long message. ".repeat(30))
                                Text("End of message")
                            }
                        },
                        confirmButton = { TextButton(onClick = { confirmed = true }) { Text("Confirm") } },
                        dismissButton = { TextButton(onClick = {}) { Text("Cancel") } },
                    )
                }
            }
        }
        val title = onNodeWithText("Do you want to proceed to download documents?")
        title.assertIsDisplayed()
        onNodeWithText("Cancel").assertIsDisplayed()
        onNodeWithText("Confirm").assertIsDisplayed()
        onNodeWithText("End of message").performScrollTo().assertIsDisplayed()
        val body = onNodeWithText("End of message").fetchSemanticsNode().boundsInRoot
        assertTrue(body.height > 0f && body.top >= 0f && body.bottom <= 844f, "body has a nonzero viewport at scale $scale: $body")
        title.assertIsDisplayed()
        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        title.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
        val fullTitleHeight = layouts.single().size.height.toFloat()
        if (scale > 1f) assertTrue(title.fetchSemanticsNode().boundsInRoot.height < fullTitleHeight,
            "AX5 title is actually bounded, not merely wrapped in a no-op scroll")
        onNodeWithText("Confirm").performClick()
        assertTrue(confirmed)
    }
}

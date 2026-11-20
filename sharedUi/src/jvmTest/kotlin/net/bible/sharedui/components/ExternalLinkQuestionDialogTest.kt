package net.bible.sharedui.components

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import net.bible.sharedcore.ui.dialog.AppDialogResult
import net.bible.sharedcore.ui.dialog.ShownDialog
import net.bible.sharedui.app.ports.EXTERNAL_LINK_OPEN
import net.bible.sharedui.app.ports.externalLinkQuestion
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.iosStrings
import kotlin.test.Test
import kotlin.test.assertEquals

/** #4155: the queued "open external link?" question shows "Don't ask again for <host>" and reports it. */
@OptIn(ExperimentalTestApi::class)
class ExternalLinkQuestionDialogTest {
    private val strings = iosStrings("en")
    private val url = "https://www.youtube.com/watch?v=abc"

    private fun answer(tick: Boolean): List<AppDialogResult> {
        val answers = mutableListOf<AppDialogResult>()
        runComposeUiTest {
            setContent {
                CompositionLocalProvider(LocalStrings provides strings) {
                    AppDialogHost(
                        shown = ShownDialog(1, externalLinkQuestion(strings, url, "www.youtube.com")),
                        permission = null,
                        onRespond = { _, r -> answers += r },
                        onPermissionChoice = {}, onPermissionDismiss = {}, onOpenExternal = {},
                    )
                }
            }
            onNodeWithText(strings.externalLinkOpenQuestion).assertExists()
            onNodeWithText(url).assertExists()
            val box = onNode(isToggleable())
            box.assertIsOff()
            onNodeWithText(strings.externalLinkDontAskAgain("www.youtube.com")).assertExists()
            if (tick) { box.performClick(); box.assertIsOn() }
            onNodeWithText(strings.openAction).performClick()
        }
        return answers
    }

    @Test fun tickedCheckboxIsReportedWithOk() =
        assertEquals(listOf<AppDialogResult>(AppDialogResult.SelectedWithCheckbox(EXTERNAL_LINK_OPEN, true)), answer(tick = true))

    @Test fun untickedOkStillOpens() =
        assertEquals(listOf<AppDialogResult>(AppDialogResult.SelectedWithCheckbox(EXTERNAL_LINK_OPEN, false)), answer(tick = false))

    @Test fun withoutAHostItIsThePlainQuestion() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalStrings provides strings) {
                AppDialogHost(
                    shown = ShownDialog(1, externalLinkQuestion(strings, "mailto:a@b.org", null)),
                    permission = null, onRespond = { _, _ -> },
                    onPermissionChoice = {}, onPermissionDismiss = {}, onOpenExternal = {},
                )
            }
        }
        onNodeWithText(strings.externalLinkOpenQuestion).assertExists()
        onNodeWithText("mailto:a@b.org").assertExists()
        onNodeWithText(strings.openAction).assertExists()
        onNode(isToggleable()).assertDoesNotExist()
    }
}

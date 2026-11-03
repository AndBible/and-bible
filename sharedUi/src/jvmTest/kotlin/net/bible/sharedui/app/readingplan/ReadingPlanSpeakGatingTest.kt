package net.bible.sharedui.app.readingplan

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.runComposeUiTest
import net.bible.sharedcore.readingplan.DailyReadingUi
import net.bible.sharedcore.readingplan.ReadingItem
import net.bible.sharedcore.readingplan.SpeakState
import net.bible.sharedui.readingplan.DailyReadingScreen
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.iosStrings
import kotlin.test.Test

/** The reading plan's Speak buttons exist only where the platform can speak. */
@OptIn(ExperimentalTestApi::class)
class ReadingPlanSpeakGatingTest {
    private fun ComposeUiTestScope_(speakSupported: Boolean) = DailyReadingUi(
        "Plan", "Day 1", "19 Oct",
        listOf(ReadingItem(1, "Gen 1", false), ReadingItem(2, "Matt 1", false)),
        showSpeakAll = true, allRead = false, isDateBasedPlan = false, speakSupported = speakSupported,
    )

    private fun androidx.compose.ui.test.ComposeUiTest.show(ui: DailyReadingUi) = setContent {
        CompositionLocalProvider(LocalStrings provides iosStrings("en")) {
            DailyReadingScreen(
                ui = ui, speakState = SpeakState.NONE, error = null, confirm = null, startDatePick = null,
                onToggleRead = {}, onRead = {}, onSpeak = {}, onSpeakAll = {}, onDone = {},
                onPauseSpeak = {}, onStopSpeak = {}, onChangePlan = {}, onChangeDay = {},
                onSetCurrentDay = {}, onSetStartDate = {}, onReset = {}, onImportPlan = {},
                onConfirm = {}, onDismissConfirm = {}, onDismissError = {},
                onConfirmStartDatePicker = { _, _, _ -> }, onDismissStartDatePicker = {}, onNavigateUp = {},
            )
        }
    }

    @Test fun speakButtonsAreShownWhenSupported() = runComposeUiTest {
        show(ComposeUiTestScope_(true))
        // one per reading plus the "all" row
        onAllNodesWithText(iosStrings("en").speak).assertCountEquals(3)
    }

    @Test fun speakButtonsAreAbsentWhenUnsupported() = runComposeUiTest {
        show(ComposeUiTestScope_(false))
        onAllNodesWithText(iosStrings("en").speak).assertCountEquals(0)
    }
}

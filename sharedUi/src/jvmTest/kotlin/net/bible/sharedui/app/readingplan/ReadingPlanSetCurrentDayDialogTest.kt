package net.bible.sharedui.app.readingplan

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import net.bible.sharedcore.readingplan.ConfirmKind
import net.bible.sharedcore.readingplan.DailyReadingUi
import net.bible.sharedcore.readingplan.ReadingItem
import net.bible.sharedcore.readingplan.SpeakState
import net.bible.sharedui.readingplan.DailyReadingScreen
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.iosStrings
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** #4097: "Set as current day…" sets the VIEWED day, so its confirmation must name that day, not "today". */
@OptIn(ExperimentalTestApi::class)
class ReadingPlanSetCurrentDayDialogTest {
    private fun ui(dayDesc: String) = DailyReadingUi(
        "Plan", dayDesc, "",
        listOf(ReadingItem(1, "Gen 1", false)),
        showSpeakAll = false, allRead = false, isDateBasedPlan = false,
    )

    private fun androidx.compose.ui.test.ComposeUiTest.show(ui: DailyReadingUi) = setContent {
        CompositionLocalProvider(LocalStrings provides iosStrings("en")) {
            DailyReadingScreen(
                ui = ui, speakState = SpeakState.NONE, error = null,
                confirm = ConfirmKind.SET_CURRENT_DAY, startDatePick = null,
                onToggleRead = {}, onRead = {}, onSpeak = {}, onSpeakAll = {}, onDone = {},
                onPauseSpeak = {}, onStopSpeak = {}, onChangePlan = {}, onChangeDay = {},
                onSetCurrentDay = {}, onSetStartDate = {}, onReset = {}, onImportPlan = {},
                onConfirm = {}, onDismissConfirm = {}, onDismissError = {},
                onConfirmStartDatePicker = { _, _, _ -> }, onDismissStartDatePicker = {}, onNavigateUp = {},
            )
        }
    }

    @Test fun confirmationNamesTheViewedDay() = runComposeUiTest {
        show(ui("Day 10"))
        val message = iosStrings("en").setCurrentDayQuestion("Day 10")
        assertTrue(message.contains("Day 10"), message)
        assertFalse(message.contains("today"), message)
        onAllNodesWithText(message).assertCountEquals(1)
    }
}

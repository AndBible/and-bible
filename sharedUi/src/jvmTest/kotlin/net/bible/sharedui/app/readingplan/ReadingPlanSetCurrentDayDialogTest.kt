package net.bible.sharedui.app.readingplan

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import net.bible.sharedcore.readingplan.ConfirmKind
import net.bible.sharedcore.readingplan.DailyReadingUi
import net.bible.sharedcore.readingplan.ReadingItem
import net.bible.sharedcore.readingplan.SpeakState
import net.bible.sharedui.components.dialog.DialogButtonRole
import net.bible.sharedui.components.dialog.DialogButtonRoleKey
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

    private fun androidx.compose.ui.test.ComposeUiTest.show(ui: DailyReadingUi, kind: ConfirmKind = ConfirmKind.SET_CURRENT_DAY) = setContent {
        CompositionLocalProvider(LocalStrings provides iosStrings("en")) {
            DailyReadingScreen(
                ui = ui, speakState = SpeakState.NONE, error = null,
                confirm = kind, startDatePick = null,
                onToggleRead = {}, onRead = {}, onSpeak = {}, onSpeakAll = {}, onDone = {},
                onPauseSpeak = {}, onStopSpeak = {}, onChangePlan = {}, onChangeDay = {},
                onSetCurrentDay = {}, onSetStartDate = {}, onReset = {}, onImportPlan = {},
                onConfirm = {}, onDismissConfirm = {}, onDismissError = {},
                onConfirmStartDatePicker = { _, _, _ -> }, onDismissStartDatePicker = {}, onNavigateUp = {},
            )
        }
    }

    private val strings = iosStrings("en")
    private fun androidx.compose.ui.test.ComposeUiTest.assertRole(role: DialogButtonRole) =
        onNodeWithTag("dialog-confirm").assert(
            SemanticsMatcher("role is $role") { it.config.getOrNull(DialogButtonRoleKey) == role },
        )

    @Test fun setCurrentDayConfirmationNamesTheViewedDayWithDetailAndPlainConfirm() = runComposeUiTest {
        show(ui("Day 10"))
        val lead = strings.setCurrentDayLead("Day 10")
        assertTrue(lead.contains("Day 10"), lead)
        assertFalse(lead.contains("today"), lead)
        onAllNodesWithText(lead).assertCountEquals(1)
        onNodeWithTag("dialog-detail").assertTextEquals(strings.setCurrentDayDetail)
        onNodeWithTag("dialog-confirm").assertTextEquals(strings.setCurrentDayAction)
        assertRole(DialogButtonRole.Confirm)
    }

    @Test fun resetConfirmationHasItsOwnLeadDetailAndDestructiveResetButton() = runComposeUiTest {
        show(ui("Day 10"), ConfirmKind.RESET)
        onAllNodesWithText(strings.resetPlanLead).assertCountEquals(1)
        onNodeWithTag("dialog-detail").assertTextEquals(strings.resetPlanDetail)
        onNodeWithTag("dialog-confirm").assertTextEquals(strings.resetGeneric)
        assertRole(DialogButtonRole.Destructive)
    }
}

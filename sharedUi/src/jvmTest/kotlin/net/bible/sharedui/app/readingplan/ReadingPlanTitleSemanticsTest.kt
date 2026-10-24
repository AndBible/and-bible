package net.bible.sharedui.app.readingplan

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import net.bible.sharedcore.readingplan.DailyReadingUi
import net.bible.sharedcore.readingplan.SpeakState
import net.bible.sharedui.readingplan.DailyReadingScreen
import net.bible.sharedui.readingplan.LocalExplicitReadingPlanTitle
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.iosStrings
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ReadingPlanTitleSemanticsTest {
    @Test
    fun optedInActualClickableTitleRefreshesInTheSameComposition() = runComposeUiTest {
        val title = mutableStateOf("1-Year through Bible")
        val explicitTitle = mutableStateOf(false)
        var changes = 0
        setContent {
            CompositionLocalProvider(LocalExplicitReadingPlanTitle provides explicitTitle.value, LocalStrings provides iosStrings("en")) {
                DailyReadingScreen(
                    ui = DailyReadingUi(title.value, "Day 1", "19 Oct", emptyList(),
                        showSpeakAll = false, allRead = false, isDateBasedPlan = false),
                    speakState = SpeakState.NONE, error = null, confirm = null, startDatePick = null,
                    onToggleRead = {}, onRead = {}, onSpeak = {}, onSpeakAll = {}, onDone = {},
                    onPauseSpeak = {}, onStopSpeak = {}, onChangePlan = { changes++ }, onChangeDay = {},
                    onSetCurrentDay = {}, onSetStartDate = {}, onReset = {}, onImportPlan = {},
                    onConfirm = {}, onDismissConfirm = {}, onDismissError = {},
                    onConfirmStartDatePicker = { _, _, _ -> }, onDismissStartDatePicker = {}, onNavigateUp = {},
                )
            }
        }
        // Android/default path has no added description. Only the iOS opt-in labels this actual click node.
        onAllNodesWithContentDescription("1-Year through Bible", useUnmergedTree = true).assertCountEquals(0)
        runOnIdle { explicitTitle.value = true }
        val english = onNodeWithContentDescription("1-Year through Bible", useUnmergedTree = true)
        val titleNodeId = english.fetchSemanticsNode().id
        english.assertHasClickAction().performClick()
        runOnIdle { assertEquals(1, changes); title.value = "Vuodessa läpi Raamatun" }
        onAllNodesWithContentDescription("1-Year through Bible", useUnmergedTree = true).assertCountEquals(0)
        val finnish = onNodeWithContentDescription("Vuodessa läpi Raamatun", useUnmergedTree = true)
        assertEquals(titleNodeId, finnish.fetchSemanticsNode().id, "Locale refresh must update the same actual title node")
        finnish.assertHasClickAction().performClick()
        runOnIdle { assertEquals(2, changes) }
    }
}

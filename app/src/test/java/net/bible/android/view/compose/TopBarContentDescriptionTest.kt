package net.bible.android.view.compose

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import net.bible.android.TEST_SDK
import net.bible.sharedcore.readingplan.DailyReadingUi
import net.bible.sharedcore.readingplan.ReadingItem
import net.bible.sharedcore.readingplan.SpeakState
import net.bible.sharedui.ProvideAppLocals
import net.bible.sharedui.components.AbOverflowMenu
import net.bible.sharedui.components.AbTopAppBar
import net.bible.sharedui.readingplan.DailyReadingScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * F84: the shared top bar's back arrow and overflow had no content description on ~41 sub-screens
 * (uiautomator saw two blank Buttons; TalkBack reads "Button"). These nodes are what the emulator
 * sweep found blank; this test sees them through the same semantics tree.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class TopBarContentDescriptionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun theBackArrowAndTheOverflowAreLabelled() {
        compose.setContent {
            ProvideAppLocals {
                AbTopAppBar(
                    title = { Text("Bookmarks") },
                    onNavigateUp = {},
                    actions = { AbOverflowMenu(contentDescription = null) { } },
                )
            }
        }
        compose.onNodeWithContentDescription("Back").assertExists()
        compose.onNodeWithContentDescription("Menu").assertExists()
    }

    @Test fun theReadingPlanCheckboxIsLabelledWithItsPassage() {
        val ui = DailyReadingUi(
            "Bible in a year", "Day 3", "Thu 9 Jan",
            listOf(ReadingItem(1, "Genesis 1-2", true), ReadingItem(2, "Psalm 1", false)),
            showSpeakAll = true, allRead = false, isDateBasedPlan = false,
        )
        compose.setContent {
            ProvideAppLocals {
                DailyReadingScreen(
                    ui = ui, speakState = SpeakState.NONE, error = null, confirm = null, startDatePick = null,
                    onToggleRead = {}, onRead = {}, onSpeak = {}, onSpeakAll = {}, onDone = {},
                    onPauseSpeak = {}, onStopSpeak = {}, onChangePlan = {}, onChangeDay = {}, onSetCurrentDay = {},
                    onSetStartDate = {}, onReset = {}, onImportPlan = {}, onConfirm = {}, onDismissConfirm = {},
                    onDismissError = {}, onConfirmStartDatePicker = { _, _, _ -> }, onDismissStartDatePicker = {},
                    onNavigateUp = {},
                )
            }
        }
        // Matches contentDescription only; the passage Text's own text does not count.
        compose.onAllNodesWithContentDescription("Psalm 1").assertCountEquals(1)
    }
}

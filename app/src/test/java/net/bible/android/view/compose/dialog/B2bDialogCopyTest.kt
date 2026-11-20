package net.bible.android.view.compose.dialog

import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onChild
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import net.bible.android.TEST_SDK
import net.bible.sharedcore.readingplan.PlanEntry
import net.bible.sharedui.ProvideAppLocals
import net.bible.sharedui.components.dialog.DialogButtonRole
import net.bible.sharedui.components.dialog.DialogButtonRoleKey
import net.bible.sharedui.progress.NewCycleConfirmDialog
import net.bible.sharedui.readingplan.ReadingPlanSelectorScreen
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.theme.AbTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** B2b dialog copy: the rewritten new-cycle and plan-reset confirmations, rendered from the real strings. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class B2bDialogCopyTest {
    @get:Rule val compose = createComposeRule()

    @Test fun newCycleConfirm_hasLeadDetailAndVerbButtons() {
        compose.setContent {
            ProvideAppLocals {
                AbTheme { NewCycleConfirmDialog(LocalStrings.current, onConfirm = {}, onDismiss = {}) }
            }
        }
        compose.onNodeWithTag("dialog-lead", useUnmergedTree = true).onChild().onChild()
            .assertTextEquals("Start a new reading cycle?")
        compose.onNodeWithTag("dialog-detail")
            .assertTextEquals("Your progress is tracked from scratch. Your previous cycle’s data is kept.")
        compose.onNodeWithTag("dialog-confirm").assertTextEquals("Start")
        compose.onNodeWithTag("dialog-confirm").assert(
            SemanticsMatcher("role is Confirm") { it.config.getOrNull(DialogButtonRoleKey) == DialogButtonRole.Confirm },
        )
        compose.onNodeWithText("Cancel").assert(
            SemanticsMatcher("role is Cancel") { it.config.getOrNull(DialogButtonRoleKey) == DialogButtonRole.Cancel },
        )
    }

    @Test fun planResetConfirm_isDestructive_andDoesNotRepeatTheLead() {
        val plans = listOf(PlanEntry("mcheyne", "M'Cheyne one-year", "Robert Murray M'Cheyne's classic plan"))
        compose.setContent {
            ProvideAppLocals {
                AbTheme {
                    ReadingPlanSelectorScreen(
                        title = "Choose a Reading Plan", plans = plans, duplicateWarning = false, error = null,
                        onSelect = {}, onReset = {}, onDismissError = {}, onDismissDuplicate = {}, onNavigateUp = {},
                    )
                }
            }
        }
        compose.onNodeWithText("M'Cheyne one-year").performTouchInput { longClick() }
        compose.onAllNodesWithText("Reset this plan?").assertCountEquals(1)
        compose.onNodeWithTag("dialog-detail").assertTextEquals("Your start date and progress in this plan are cleared.")
        compose.onNodeWithTag("dialog-confirm").assertTextEquals("Reset")
        compose.onNodeWithTag("dialog-confirm").assert(
            SemanticsMatcher("role is Destructive") { it.config.getOrNull(DialogButtonRoleKey) == DialogButtonRole.Destructive },
        )
    }
}

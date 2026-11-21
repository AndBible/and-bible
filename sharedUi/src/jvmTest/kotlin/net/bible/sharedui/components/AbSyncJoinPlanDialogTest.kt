package net.bible.sharedui.components

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import net.bible.sharedcore.ui.dialog.AppDialogRequest
import net.bible.sharedcore.ui.dialog.AppDialogResult
import net.bible.sharedcore.ui.dialog.DialogAnswer
import kotlin.test.Test
import kotlin.test.assertEquals

/** The join plan dialog: defaults, the confirmation step with its destructive variant, Back and Cancel. */
@OptIn(ExperimentalTestApi::class)
class AbSyncJoinPlanDialogTest {
    private fun plan(rows: List<AppDialogRequest.SyncJoinPlan.Row> = listOf(
        AppDialogRequest.SyncJoinPlan.Row("BOOKMARKS", "Bookmarks"),
        AppDialogRequest.SyncJoinPlan.Row("WORKSPACES", "Workspaces"),
    )) = AppDialogRequest.SyncJoinPlan(
        title = "Join the cloud", lead = "The cloud already has data.", rows = rows,
        uploadNotes = listOf("Labels will be uploaded."),
        choices = listOf(
            DialogAnswer("merge", "Merge both", "Combine them."),
            DialogAnswer("fetch", "Copy from cloud", "Replaces this device.", destructive = true),
        ),
        defaultChoice = "merge", confirmTitle = "Confirm sync", confirmLead = "This will happen:",
        outcomeLine = { key, choice -> "$key -> $choice" }, createOutcomes = listOf("Creates labels"),
        warning = "Data will be overwritten.", nextText = "Next", backText = "Back", cancelText = "Cancel",
        startText = "Start sync", replaceText = "Replace and start",
    )

    private fun run(request: AppDialogRequest.SyncJoinPlan, block: androidx.compose.ui.test.ComposeUiTest.() -> Unit): List<AppDialogResult> {
        val results = mutableListOf<AppDialogResult>()
        runComposeUiTest {
            setContent { AbSyncJoinPlanDialog(request, onResult = { results += it }) }
            block()
        }
        return results
    }

    @Test fun defaultsToMergeAndConfirmReturnsMap() {
        val results = run(plan()) {
            onNodeWithText("Next").performClick()
            onNodeWithText("Confirm sync").assertIsDisplayed()
            onNodeWithTag("join-outcome-BOOKMARKS").assertIsDisplayed()
            onNodeWithTag("join-warning").assertDoesNotExist()
            onNodeWithText("Start sync").performClick()
        }
        assertEquals(listOf<AppDialogResult>(AppDialogResult.SyncJoinChoices(mapOf("BOOKMARKS" to "merge", "WORKSPACES" to "merge"))), results)
    }

    @Test fun destructiveChoiceShowsReplaceAndWarning() {
        val results = run(plan()) {
            onNodeWithTag("join-row-WORKSPACES").performClick()
            onNodeWithTag("join-choice-fetch").performClick()
            onNodeWithText("Next").performClick()
            onNodeWithTag("join-warning").assertIsDisplayed()
            onNodeWithText("Start sync").assertDoesNotExist()
            onNodeWithText("Replace and start").performClick()
        }
        assertEquals(listOf<AppDialogResult>(AppDialogResult.SyncJoinChoices(mapOf("BOOKMARKS" to "merge", "WORKSPACES" to "fetch"))), results)
    }

    @Test fun backKeepsChoices() {
        val results = run(plan()) {
            onNodeWithTag("join-row-BOOKMARKS").performClick()
            onNodeWithTag("join-choice-fetch").performClick()
            onNodeWithText("Next").performClick()
            onNodeWithText("Back").performClick()
            onNodeWithText("The cloud already has data.").assertIsDisplayed()
            onNodeWithText("Next").performClick()
            onNodeWithText("Replace and start").performClick()
        }
        assertEquals(listOf<AppDialogResult>(AppDialogResult.SyncJoinChoices(mapOf("BOOKMARKS" to "fetch", "WORKSPACES" to "merge"))), results)
    }

    @Test fun eachDropdownIsLinkedToItsCategoryLabelForScreenReaders() {
        run(plan()) {
            onNodeWithTag("join-row-BOOKMARKS").assertContentDescriptionContains("Bookmarks")
            onNodeWithTag("join-row-WORKSPACES").assertContentDescriptionContains("Workspaces")
        }
    }

    @Test fun cancelReturnsCancel() {
        assertEquals(listOf<AppDialogResult>(AppDialogResult.Cancel), run(plan()) { onNodeWithText("Cancel").performClick() })
    }

    @Test fun backWithoutRowsCancels() {
        val results = run(plan(rows = emptyList())) {
            onNodeWithText("Next").performClick()
            onNodeWithText("Back").performClick()
        }
        assertEquals(listOf<AppDialogResult>(AppDialogResult.Cancel), results)
    }
}

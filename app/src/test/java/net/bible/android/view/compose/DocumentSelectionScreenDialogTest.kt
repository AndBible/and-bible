/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */

package net.bible.android.view.compose

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import net.bible.android.TEST_SDK
import net.bible.service.common.DisplayColorMode
import net.bible.sharedcore.navigation.ChooserError
import net.bible.sharedcore.navigation.DocGroupBy
import net.bible.sharedcore.navigation.DocTypeFilter
import net.bible.sharedcore.navigation.DocumentSelectionDialog
import net.bible.sharedcore.navigation.ProceedAnswer
import net.bible.sharedcore.navigation.defaultArrangement
import net.bible.sharedui.ProvideAppLocals
import net.bible.sharedui.navigation.DocumentSelectionScreen
import net.bible.sharedui.theme.AbTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Task 16 (run-2 plan, appendix rows 7954/8004/8034/8234/9306/9333): `manageDownload`/
 * `handleDownloadDelete`/`handleDownloadDeleteIndex`/`showDownloadErrors` (and their ChooseDocument
 * counterparts) moved their `AlertDialog.Builder` confirm/error questions into
 * `DocumentSelectionController.dialog` (a `DocumentSelectionDialog`). This proves
 * [DocumentSelectionScreen] renders each variant's text and answers the right callback -- the
 * controller-level request/confirm/dismiss sequencing itself is `DocumentSelectionControllerTest`'s
 * job, including the D8-3 one-at-a-time queue.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class DocumentSelectionScreenDialogTest {
    @get:Rule val compose = createComposeRule()

    private var confirmCalls = 0
    private var dismissCalls = 0
    private var confirmProceedAnswer: ProceedAnswer? = null
    private var dismissProceedCalls = 0

    private fun show(dialog: DocumentSelectionDialog) = compose.setContent {
        ProvideAppLocals {
            AbTheme(darkTheme = false, colorMode = DisplayColorMode.NORMAL, disableAnimations = true) {
                DocumentSelectionScreen(
                    title = "Documents",
                    downloadMode = true,
                    loading = false,
                    isRefreshing = false,
                    onRefresh = {},
                    grouped = emptyList(),
                    languages = emptyList(),
                    selectedLanguage = null,
                    typeFilters = emptyList(),
                    selectedTypeFilter = DocTypeFilter.ALL,
                    query = "",
                    resultCount = "0 documents",
                    selectionMode = false,
                    selectedIds = emptySet(),
                    error = null as ChooserError?,
                    dialog = dialog,
                    topBarActions = {},
                    onQueryChange = {},
                    searchModeActive = false,
                    onOpenSearch = {},
                    onCloseSearch = {},
                    onLanguageChange = {},
                    onTypeFilterChange = {},
                    arrangement = defaultArrangement(emptySet()),
                    groupKeys = listOf(DocGroupBy.NONE),
                    repositories = emptyList(),
                    rememberArrangement = false,
                    arrangementIsDefault = true,
                    onMoveSort = { _, _ -> },
                    onToggleSortDirection = {},
                    onGroupByChange = {},
                    onRepositoryChange = {},
                    onRememberChange = {},
                    onResetArrangement = {},
                    onRowClick = {},
                    onRowLongClick = {},
                    onDownload = {},
                    onCancel = {},
                    onSelectionAbout = {},
                    onSelectionDelete = {},
                    onSelectionDeleteIndex = {},
                    onSelectionUnlock = {},
                    unlockVisible = false,
                    deleteVisible = false,
                    onDismissError = {},
                    onConfirmDialog = { confirmCalls++ },
                    onDismissDialog = { dismissCalls++ },
                    onConfirmProceed = { confirmProceedAnswer = it },
                    onDismissProceed = { dismissProceedCalls++ },
                    onNavigateUp = {},
                    onExitSelection = {},
                )
            }
        }
    }

    @Test fun noneShowsNoDialog() {
        show(DocumentSelectionDialog.None)
        compose.onNodeWithText("Download King James Version").assertDoesNotExist()
    }

    @Test fun confirmDownload_showsTheMessage_okAnswersConfirm() {
        show(DocumentSelectionDialog.ConfirmDownload("Download King James Version"))
        compose.onNodeWithText("Download King James Version").assertExists()
        compose.onNodeWithText("OK").performClick()
        assertEquals(1, confirmCalls)
        assertEquals(0, dismissCalls)
    }

    @Test fun confirmDownload_cancelAnswersDismiss_notConfirm() {
        show(DocumentSelectionDialog.ConfirmDownload("Download King James Version"))
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(0, confirmCalls)
        assertEquals(1, dismissCalls)
    }

    @Test fun confirmDelete_showsTheMessage_yesAnswersConfirm() {
        show(DocumentSelectionDialog.ConfirmDelete("Delete King James Version?"))
        compose.onNodeWithText("Delete King James Version?").assertExists()
        compose.onNodeWithText("Yes").performClick()
        assertEquals(1, confirmCalls)
        assertEquals(0, dismissCalls)
    }

    @Test fun confirmDelete_noAnswersDismiss() {
        show(DocumentSelectionDialog.ConfirmDelete("Delete these documents?\n\nKJV\nESV"))
        compose.onNodeWithText("No").performClick()
        assertEquals(0, confirmCalls)
        assertEquals(1, dismissCalls)
    }

    // Strings.deleteSearchIndexDoc already existed before this task (R.string.delete_search_index_doc,
    // "Delete index of %s?") -- the SCREEN formats it from the bare docName the controller carries.
    @Test fun confirmDeleteIndex_showsTheFormattedMessage_okAnswersConfirm() {
        show(DocumentSelectionDialog.ConfirmDeleteIndex("King James Version"))
        compose.onNodeWithText("Delete index of King James Version?").assertExists()
        compose.onNodeWithText("OK").performClick()
        assertEquals(1, confirmCalls)
        assertEquals(0, dismissCalls)
    }

    @Test fun confirmDeleteIndex_cancelAnswersDismiss() {
        show(DocumentSelectionDialog.ConfirmDeleteIndex("King James Version"))
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(0, confirmCalls)
        assertEquals(1, dismissCalls)
    }

    @Test fun errors_showsTitleAndMessage_okAnswersDismiss() {
        show(DocumentSelectionDialog.Errors("Download errors", "Could not connect to the repository."))
        compose.onNodeWithText("Download errors").assertExists()
        compose.onNodeWithText("Could not connect to the repository.").assertExists()
        compose.onNodeWithText("OK").performClick()
        assertEquals(1, dismissCalls)
        assertEquals(0, confirmCalls)
    }

    // Task 23: classic askIfWantToProceed() (NH:7605-7623) -- title/message resolved through
    // ApplicationProvider the same way DocumentFilterBarTest's strings.all test does, rather than
    // hardcoding the English copy.
    private fun androidString(id: Int): String =
        androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>().getString(id)

    @Test fun proceedWithDownload_showsTitleAndOptions_yesAnswersConfirmProceedYes() {
        show(DocumentSelectionDialog.ProceedWithDownload)
        compose.onNodeWithText(androidString(net.bible.android.activity.R.string.download_question_title)).assertExists()
        compose.onNodeWithText("Yes").performClick()
        assertEquals(ProceedAnswer.YES, confirmProceedAnswer)
        assertEquals(0, dismissProceedCalls)
    }

    @Test fun proceedWithDownload_doNotAskAgainAnswersConfirmProceedDontAskAgain() {
        show(DocumentSelectionDialog.ProceedWithDownload)
        compose.onNodeWithText(androidString(net.bible.android.activity.R.string.do_not_ask_again)).performClick()
        assertEquals(ProceedAnswer.DONT_ASK_AGAIN, confirmProceedAnswer)
        assertEquals(0, dismissProceedCalls)
    }

    @Test fun proceedWithDownload_cancelAnswersDismissProceed_notConfirm() {
        show(DocumentSelectionDialog.ProceedWithDownload)
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(null, confirmProceedAnswer)
        assertEquals(1, dismissProceedCalls)
    }

    // Task 23: classic warnUserBooksNotDownloaded() (NH:8100-8114) -- one "OK" button, wired to the
    // same generic onDismissDialog every other single-button dialog in this screen uses (see
    // DocumentSelectionScreen's own comment on that branch for why confirm/dismiss are the same call).
    @Test fun booksNotDownloaded_showsTheMessage_okAnswersDismiss() {
        // "<br>" (HtmlRuns' own escaping/joining format, see NavHostComposeActivity
        // .warnUserBooksNotDownloaded) renders as a newline (HtmlRuns.brBreak).
        show(DocumentSelectionDialog.BooksNotDownloaded("KJV<br>ESV"))
        compose.onNodeWithText("KJV\nESV").assertExists()
        compose.onNodeWithText("OK").performClick()
        assertEquals(1, dismissCalls)
        assertEquals(0, confirmCalls)
    }
}

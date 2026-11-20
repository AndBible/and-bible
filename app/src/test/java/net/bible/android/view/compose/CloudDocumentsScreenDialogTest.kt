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

import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import net.bible.android.TEST_SDK
import net.bible.service.common.DisplayColorMode
import net.bible.sharedcore.cloud.CloudDocFilter
import net.bible.sharedcore.cloud.CloudDocumentsDialog
import net.bible.sharedcore.navigation.DocGroup
import net.bible.sharedcore.navigation.DocGroupBy
import net.bible.sharedcore.navigation.DocGroupKey
import net.bible.sharedcore.navigation.defaultArrangement
import net.bible.sharedui.ProvideAppLocals
import net.bible.sharedui.cloud.CloudDocumentsScreen
import net.bible.sharedui.components.dialog.DialogButtonRole
import net.bible.sharedui.components.dialog.DialogButtonRoleKey
import net.bible.sharedui.theme.AbTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Task 17 (run-2 plan, appendix rows 8574/8586): `cloudDocumentsConfirmRemove`/
 * `cloudDocumentsConfirmPurge` moved their `AlertDialog.Builder` questions into
 * `CloudDocumentsController.dialog` (extending the pre-existing `syncNowDialog` into one
 * `CloudDocumentsDialog` sum type). This proves [CloudDocumentsScreen] renders `ConfirmRemove`/
 * `ConfirmPurge` with the lead line, the consequence line and the verb button and answers `onConfirmDialog`/`onDismissDialog` --
 * `SyncNow`'s own rendering (an `AbMultiSelectSheet`) is unchanged and already covered by
 * `CloudDocumentsGoldenTest`/`CloudDocumentsControllerRebuildIsolationTest`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class CloudDocumentsScreenDialogTest {
    @get:Rule val compose = createComposeRule()

    private var confirmCalls = 0
    private var dismissCalls = 0

    private fun show(dialog: CloudDocumentsDialog) = compose.setContent {
        ProvideAppLocals {
            AbTheme(darkTheme = false, colorMode = DisplayColorMode.NORMAL, disableAnimations = true) {
                CloudDocumentsScreen(
                    title = "Manage cloud documents",
                    loading = false,
                    isRefreshing = false,
                    onRefresh = {},
                    grouped = listOf(DocGroup(DocGroupKey.None, emptyList())),
                    statusFilters = emptyList(),
                    selectedStatusFilter = CloudDocFilter.ALL,
                    categoryFilters = emptyList(),
                    selectedCategoryFilter = null,
                    query = "",
                    selectionMode = false,
                    selectedIds = emptySet(),
                    syncEnabled = false,
                    dialog = dialog,
                    topBarActions = {},
                    onQueryChange = {},
                    searchModeActive = false,
                    onOpenSearch = {},
                    onCloseSearch = {},
                    onStatusFilterChange = {},
                    onCategoryFilterChange = {},
                    arrangement = defaultArrangement(emptySet()),
                    groupKeys = listOf(DocGroupBy.NONE),
                    rememberArrangement = false,
                    arrangementIsDefault = true,
                    onMoveSort = { _, _ -> },
                    onToggleSortDirection = {},
                    onGroupByChange = {},
                    onRememberChange = {},
                    onResetArrangement = {},
                    showRemoved = false,
                    onShowRemovedChange = {},
                    onRowClick = {},
                    onRowLongClick = {},
                    onRowAction = { _, _ -> },
                    onBulkAction = {},
                    onSyncNowConfirm = {},
                    onSyncNowDismiss = {},
                    onConfirmDialog = { confirmCalls++ },
                    onDismissDialog = { dismissCalls++ },
                    onNavigateUp = {},
                    onExitSelection = {},
                )
            }
        }
    }

    private fun confirmRole() = compose.onNodeWithTag("dialog-confirm").fetchSemanticsNode().config.getOrNull(DialogButtonRoleKey)

    @Test fun noneShowsNoDialog() {
        show(CloudDocumentsDialog.None)
        compose.onNodeWithText("Remove from cloud").assertDoesNotExist()
        compose.onNodeWithTag("dialog-confirm").assertDoesNotExist()
    }

    @Test fun confirmRemove_cloudOnly_showsLeadAndDetail_removeIsDestructiveAndConfirms() {
        show(CloudDocumentsDialog.ConfirmRemove(listOf("KJV"), "Remove \u201cKJV\u201d from the cloud?", allDevices = false))
        compose.onNodeWithText("Remove \u201cKJV\u201d from the cloud?").assertExists()
        compose.onNodeWithTag("dialog-detail")
            .assertTextEquals("Copies on this device are kept. Devices with document sync on will remove them too.")
        compose.onNodeWithTag("dialog-confirm").assertTextEquals("Remove")
        assertEquals(DialogButtonRole.Destructive, confirmRole())
        compose.onNodeWithTag("dialog-confirm").performClick()
        assertEquals(1, confirmCalls)
        assertEquals(0, dismissCalls)
    }

    @Test fun confirmRemove_allDevices_hasTheAllDevicesDetail() {
        show(CloudDocumentsDialog.ConfirmRemove(listOf("KJV", "ESV"), "Remove 2 documents from the cloud and all your devices?", allDevices = true))
        compose.onNodeWithText("Remove 2 documents from the cloud and all your devices?").assertExists()
        compose.onNodeWithTag("dialog-detail")
            .assertTextEquals("This also deletes the copies on this device and on every other device with document sync on.")
        assertEquals(DialogButtonRole.Destructive, confirmRole())
    }

    @Test fun confirmRemove_cancelAnswersDismiss_notConfirm() {
        show(CloudDocumentsDialog.ConfirmRemove(listOf("KJV"), "Remove \u201cKJV\u201d from the cloud?", allDevices = false))
        compose.onNodeWithTag("dialog-dismiss").performClick()
        assertEquals(0, confirmCalls)
        assertEquals(1, dismissCalls)
    }

    @Test fun confirmPurge_showsLeadAndDetail_removeIsDestructiveAndConfirms() {
        show(CloudDocumentsDialog.ConfirmPurge(listOf("KJV"), "Permanently remove \u201cKJV\u201d from the cloud history?"))
        compose.onNodeWithText("Permanently remove \u201cKJV\u201d from the cloud history?").assertExists()
        compose.onNodeWithTag("dialog-detail")
            .assertTextEquals("A device that still has a copy may upload it again on its next sync.")
        compose.onNodeWithTag("dialog-confirm").assertTextEquals("Remove")
        assertEquals(DialogButtonRole.Destructive, confirmRole())
        compose.onNodeWithTag("dialog-confirm").performClick()
        assertEquals(1, confirmCalls)
        assertEquals(0, dismissCalls)
    }

    @Test fun confirmPurge_cancelAnswersDismiss() {
        show(CloudDocumentsDialog.ConfirmPurge(listOf("KJV"), "Permanently remove \u201cKJV\u201d?"))
        compose.onNodeWithTag("dialog-dismiss").performClick()
        assertEquals(0, confirmCalls)
        assertEquals(1, dismissCalls)
    }

    @Test fun confirmBlock_hasNoDetail_andANonDestructiveVerbButton() {
        show(CloudDocumentsDialog.ConfirmBlock(listOf("KJV"), "Stop syncing 1 document to the cloud?"))
        compose.onNodeWithText("Stop syncing 1 document to the cloud?").assertExists()
        compose.onNodeWithTag("dialog-detail").assertDoesNotExist()
        compose.onNodeWithTag("dialog-confirm").assertTextEquals("Don\u2019t sync")
        assertEquals(DialogButtonRole.Confirm, confirmRole())
    }
}

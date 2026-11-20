package net.bible.sharedui.mydocuments

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import net.bible.sharedcore.mydocuments.MyDocItem
import net.bible.sharedui.components.dialog.DialogButtonRole
import net.bible.sharedui.components.dialog.DialogButtonRoleKey
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.iosStrings
import net.bible.sharedui.theme.AbTheme
import kotlin.test.Test

/** The My Documents delete confirmations (single and batch) must have a destructive confirm button. */
@OptIn(ExperimentalTestApi::class)
class MyDocumentsDeleteConfirmTest {
    private val strings = iosStrings("en")
    private val doc = MyDocItem(1, "KJV", "King James", "desc", isAiGenerated = false, canDelete = true)

    private fun androidx.compose.ui.test.ComposeUiTest.show(selection: Set<Long>) = setContent {
        CompositionLocalProvider(LocalStrings provides strings) {
            AbTheme(darkTheme = false) {
                MyDocumentsScreen(
                    title = "My documents", documents = listOf(doc), dirty = false, query = "", filtering = false,
                    searchModeActive = false, totalCount = 1, onOpenSearch = {}, onCloseSearch = {},
                    onQueryChange = {}, onMove = { _, _ -> }, onOpen = {}, onRename = { _, _ -> },
                    onEditDescription = { _, _ -> }, onDelete = {}, onExport = {}, onCreate = {}, onImport = {},
                    onSave = {}, onCancel = {}, onNavigateUp = {},
                    selection = selection, onToggleSelected = {}, onClearSelection = {},
                    onDeleteSelected = {}, onExportSelected = {},
                )
            }
        }
    }

    private fun androidx.compose.ui.test.ComposeUiTest.assertDestructiveConfirm(lead: String) {
        onAllNodesWithText(lead).assertCountEquals(1)
        onNodeWithTag("dialog-confirm").assert(
            SemanticsMatcher("role is Destructive") { it.config.getOrNull(DialogButtonRoleKey) == DialogButtonRole.Destructive },
        )
    }

    @Test fun singleDeleteConfirmShowsLeadAndHasDestructiveConfirm() = runComposeUiTest {
        show(emptySet())
        // Two "Menu" buttons: the document row's menu (first, lower on screen) and the screen's own help menu.
        onAllNodesWithContentDescription(strings.menu)[0].performClick()
        onNodeWithText(strings.deleteLabel).performClick()
        assertDestructiveConfirm(strings.deleteDocumentLead(doc.name))
    }

    @Test fun batchDeleteConfirmShowsLeadAndHasDestructiveConfirm() = runComposeUiTest {
        show(setOf(doc.id))
        onNodeWithContentDescription(strings.deleteLabel).performClick()
        assertDestructiveConfirm(strings.deleteDocumentsConfirmation)
    }
}

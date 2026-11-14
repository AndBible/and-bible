package net.bible.sharedui.mydocuments

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import net.bible.sharedcore.mydocuments.ContentType
import net.bible.sharedcore.mydocuments.MyDocPageItem
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.iosStrings
import net.bible.sharedui.theme.AbTheme
import kotlin.test.Test

/** #4104 (empty pages text) and #4105 (translated page content type) on the page list screen. */
@OptIn(ExperimentalTestApi::class)
class MyDocumentPagesScreenTextTest {
    @Composable
    private fun screen(items: List<MyDocPageItem>) = MyDocumentPagesScreen(
        title = "Sermon notes", pages = items, dirty = false,
        query = "", filtering = false, searchModeActive = false, totalCount = items.size,
        onOpenSearch = {}, onCloseSearch = {}, onQueryChange = {},
        onMove = { _, _ -> }, onOpen = {}, onRename = { _, _ -> }, onDelete = {}, onExport = {},
        onCreate = { _, _ -> }, onImport = {}, onSave = {}, onCancel = {}, onNavigateUp = {},
        onSwitchDocument = {},
        selection = emptySet(), onToggleSelected = {}, onClearSelection = {},
        onDeleteSelected = {}, onExportSelected = {},
    )

    private fun androidx.compose.ui.test.ComposeUiTest.show(items: List<MyDocPageItem>) = setContent {
        CompositionLocalProvider(LocalStrings provides iosStrings("en")) {
            AbTheme(darkTheme = false) { screen(items) }
        }
    }

    @Test
    fun emptyDocumentTalksAboutPagesNotDocuments() = runComposeUiTest {
        show(emptyList())
        onNodeWithText("No pages yet. Tap + to add one.").assertExists()
        onNodeWithText("No documents yet. Tap + to create one.").assertDoesNotExist()
    }

    @Test
    fun pageListShowsTranslatedContentTypeLabels() = runComposeUiTest {
        show(listOf(
            MyDocPageItem(1, "One", ContentType.MARKDOWN, isAiGenerated = false),
            MyDocPageItem(2, "Two", ContentType.HTML, isAiGenerated = false),
        ))
        onNodeWithText("Markdown").assertExists()
        onNodeWithText("HTML").assertExists()
        onNodeWithText("MARKDOWN").assertDoesNotExist()
    }

    @Test
    fun newPageSheetShowsTranslatedContentType() = runComposeUiTest {
        show(emptyList())
        onNodeWithContentDescription("New page").performClick()
        onNodeWithText("Markdown").assertExists()
        onNodeWithText("MARKDOWN").assertDoesNotExist()
    }
}

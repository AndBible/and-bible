package net.bible.sharedui.mydocuments

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import net.bible.sharedcore.mydocuments.ContentType
import net.bible.sharedcore.mydocuments.MyDocItem
import net.bible.sharedcore.mydocuments.MyDocPageItem
import net.bible.sharedui.reading.host.slots.LocalAgentStatusIcon
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.iosStrings
import net.bible.sharedui.theme.AbTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * #4167: AI entries in My Documents use the app-wide robot icon, resolved through the host seam
 * [LocalAgentStatusIcon] (the same one the agent log and activity list use), not Material's sparkle.
 */
@OptIn(ExperimentalTestApi::class)
class MyDocumentsAiIconTest {
    private val strings = iosStrings("en")

    private fun androidx.compose.ui.test.ComposeUiTest.show(counter: IntArray, content: @androidx.compose.runtime.Composable () -> Unit) =
        setContent {
            CompositionLocalProvider(
                LocalStrings provides strings,
                LocalAgentStatusIcon provides { counter[0]++; ColorPainter(Color.Gray) },
            ) { AbTheme(darkTheme = false) { content() } }
        }

    @Test
    fun documentListUsesRobotIconOnlyForAiEntries() = runComposeUiTest {
        val uses = IntArray(1)
        val docs = listOf(
            MyDocItem(1, "AI1", "AI doc", "d", isAiGenerated = true, canDelete = true),
            MyDocItem(2, "H1", "Human doc", "d", isAiGenerated = false, canDelete = true),
        )
        show(uses) {
            MyDocumentsScreen(
                title = "My documents", documents = docs, dirty = false, query = "", filtering = false,
                searchModeActive = false, totalCount = 2, onOpenSearch = {}, onCloseSearch = {},
                onQueryChange = {}, onMove = { _, _ -> }, onOpen = {}, onRename = { _, _ -> },
                onEditDescription = { _, _ -> }, onDelete = {}, onExport = {}, onCreate = {}, onImport = {},
                onSave = {}, onCancel = {}, onNavigateUp = {},
                selection = emptySet(), onToggleSelected = {}, onClearSelection = {},
                onDeleteSelected = {}, onExportSelected = {},
            )
        }
        waitForIdle()
        assertEquals(1, uses[0], "robot icon requested once, for the single AI document")
    }

    @Test
    fun pageListUsesRobotIconOnlyForAiEntries() = runComposeUiTest {
        val uses = IntArray(1)
        val pages = listOf(
            MyDocPageItem(1, "AI page", ContentType.MARKDOWN, isAiGenerated = true),
            MyDocPageItem(2, "Human page", ContentType.MARKDOWN, isAiGenerated = false),
        )
        show(uses) {
            MyDocumentPagesScreen(
                title = "Sermon notes", pages = pages, dirty = false,
                query = "", filtering = false, searchModeActive = false, totalCount = 2,
                onOpenSearch = {}, onCloseSearch = {}, onQueryChange = {},
                onMove = { _, _ -> }, onOpen = {}, onRename = { _, _ -> }, onDelete = {}, onExport = {},
                onCreate = { _, _ -> }, onImport = {}, onSave = {}, onCancel = {}, onNavigateUp = {},
                onSwitchDocument = {},
                selection = emptySet(), onToggleSelected = {}, onClearSelection = {},
                onDeleteSelected = {}, onExportSelected = {},
            )
        }
        waitForIdle()
        assertEquals(1, uses[0], "robot icon requested once, for the single AI page")
    }
}

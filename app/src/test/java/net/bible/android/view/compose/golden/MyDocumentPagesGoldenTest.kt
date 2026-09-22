package net.bible.android.view.compose.golden

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import net.bible.android.TEST_SDK
import net.bible.sharedcore.mydocuments.ContentType
import net.bible.sharedcore.mydocuments.MyDocPageItem
import net.bible.sharedui.components.AbCreateItemSheetContent
import net.bible.sharedui.mydocuments.MyDocumentPagesScreen
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class MyDocumentPagesGoldenTest {
    private val pages = listOf(
        MyDocPageItem(0, "Introduction", ContentType.MARKDOWN, isAiGenerated = false),
        MyDocPageItem(1, "Chapter 1", ContentType.HTML, isAiGenerated = false),
        MyDocPageItem(2, "AI summary", ContentType.MARKDOWN, isAiGenerated = true),
    )

    @Composable
    private fun screen(
        items: List<MyDocPageItem> = pages,
        dirty: Boolean = false,
        query: String = "",
        filtering: Boolean = false,
        searchModeActive: Boolean = false,
        selection: Set<Long> = emptySet(),
    ) = MyDocumentPagesScreen(
        title = "Sermon notes", pages = items, dirty = dirty,
        query = query, filtering = filtering, searchModeActive = searchModeActive,
        totalCount = pages.size,
        onOpenSearch = {}, onCloseSearch = {}, onQueryChange = {},
        onMove = { _, _ -> }, onOpen = {}, onRename = { _, _ -> }, onDelete = {}, onExport = {},
        onCreate = { _, _ -> }, onImport = {}, onSave = {}, onCancel = {}, onNavigateUp = {},
        onSwitchDocument = {},
        selection = selection, onToggleSelected = {}, onClearSelection = {},
        onDeleteSelected = {}, onExportSelected = {},
    )

    @Test fun myDocumentPages_populated() { captureMatrix("MyDocumentPages", "populated") { screen() } }

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "ar")
    fun myDocumentPages_populated_rtl() { captureRtl("MyDocumentPages", "populated") { screen() } }

    @Test fun myDocumentPages_dirty() {
        captureGolden("MyDocumentPages", "dirty", EDGE_MODE) { screen(dirty = true) }
    }

    @Test fun myDocumentPages_empty() {
        captureGolden("MyDocumentPages", "empty", EDGE_MODE) { screen(items = emptyList()) }
    }

    @Test fun myDocumentPages_createSheet() {
        captureGolden("MyDocumentPages", "createSheet", EDGE_MODE) {
            AbCreateItemSheetContent(
                title = "New page", initialName = "Page 4",
                confirmText = "OK", importText = "Import page",
                onCreate = {}, onImport = {},
                extraContent = { Text("Content type: MARKDOWN") },
            )
        }
    }

    @Test fun myDocumentPages_searchMode() {
        captureMatrix("MyDocumentPages", "searchMode") {
            screen(items = pages.take(2), query = "chap", filtering = true, searchModeActive = true)
        }
    }

    @Test fun myDocumentPages_filtering() {
        captureGolden("MyDocumentPages", "filtering", EDGE_MODE) {
            screen(items = pages.take(1), query = "chap", filtering = true)
        }
    }

    @Test fun myDocumentPages_selection() {
        captureGolden("MyDocumentPages", "selection", EDGE_MODE) {
            screen(selection = setOf(0L, 1L))
        }
    }
}

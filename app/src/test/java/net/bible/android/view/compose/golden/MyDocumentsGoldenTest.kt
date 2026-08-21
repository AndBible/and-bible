package net.bible.android.view.compose.golden

import androidx.compose.runtime.Composable
import net.bible.android.TEST_SDK
import net.bible.sharedcore.mydocuments.MyDocItem
import net.bible.sharedui.components.AbCreateItemSheetContent
import net.bible.sharedui.mydocuments.MyDocumentsScreen
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class MyDocumentsGoldenTest {
    private val docs = listOf(
        MyDocItem(0, "MyDoc_a", "Sermon notes", "Weekly sermon outlines", isAiGenerated = false, canDelete = true),
        MyDocItem(1, "MyDoc_b", "Study of Romans", "", isAiGenerated = false, canDelete = true),
        MyDocItem(2, "AiDoc_c", "AI: Parables", "Generated summary", isAiGenerated = true, canDelete = false),
    )

    @Composable
    private fun screen(
        items: List<MyDocItem> = docs,
        dirty: Boolean = false,
        query: String = "",
        filtering: Boolean = false,
        searchModeActive: Boolean = false,
    ) = MyDocumentsScreen(
        title = "My documents", documents = items, dirty = dirty,
        query = query, filtering = filtering, searchModeActive = searchModeActive,
        totalCount = docs.size,
        onOpenSearch = {}, onCloseSearch = {}, onQueryChange = {},
        onMove = { _, _ -> }, onOpen = {}, onRename = { _, _ -> }, onEditDescription = { _, _ -> },
        onDelete = {}, onExport = {}, onCreate = {}, onImport = {}, onSave = {}, onCancel = {},
        onNavigateUp = {},
    )

    @Test fun myDocuments_populated() { captureMatrix("MyDocuments", "populated") { screen() } }

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "ar")
    fun myDocuments_populated_rtl() { captureRtl("MyDocuments", "populated") { screen() } }

    @Test fun myDocuments_dirty() {
        captureGolden("MyDocuments", "dirty", EDGE_MODE) { screen(dirty = true) }
    }

    @Test fun myDocuments_empty() {
        captureGolden("MyDocuments", "empty", EDGE_MODE) { screen(items = emptyList()) }
    }

    @Test fun myDocuments_createSheet() {
        captureGolden("MyDocuments", "createSheet", EDGE_MODE) {
            AbCreateItemSheetContent(
                title = "Create new document", initialName = "Document 4",
                confirmText = "OK", importText = "Import document",
                onCreate = {}, onImport = {},
            )
        }
    }

    @Test fun myDocuments_searchMode() {
        captureMatrix("MyDocuments", "searchMode") {
            screen(items = docs.take(2), query = "rom", filtering = true, searchModeActive = true)
        }
    }

    @Test fun myDocuments_filtering() {
        // Search mode closed but a filter still applied is impossible in production (closing search
        // clears the query); this state exists to pin the drag handles being GONE while filtering.
        captureGolden("MyDocuments", "filtering", EDGE_MODE) {
            screen(items = docs.take(1), query = "rom", filtering = true, searchModeActive = false)
        }
    }
}

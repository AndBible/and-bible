package net.bible.android.view.compose.golden

import androidx.compose.runtime.Composable
import net.bible.android.TEST_SDK
import net.bible.sharedcore.mydocuments.MyDocItem
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
    private fun screen(items: List<MyDocItem> = docs, dirty: Boolean = false) = MyDocumentsScreen(
        title = "My documents", documents = items, dirty = dirty,
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
}

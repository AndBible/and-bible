package net.bible.android.view.compose.golden

import androidx.compose.runtime.Composable
import net.bible.android.TEST_SDK
import net.bible.sharedcore.mydocuments.ContentType
import net.bible.sharedcore.mydocuments.MyDocPageItem
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
    private fun screen(items: List<MyDocPageItem> = pages, dirty: Boolean = false) = MyDocumentPagesScreen(
        title = "Sermon notes", pages = items, dirty = dirty,
        onMove = { _, _ -> }, onOpen = {}, onRename = { _, _ -> }, onDelete = {}, onExport = {},
        onCreate = { _, _ -> }, onImport = {}, onSave = {}, onCancel = {}, onNavigateUp = {},
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
}

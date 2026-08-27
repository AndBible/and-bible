package net.bible.android.view.compose.golden

import androidx.compose.foundation.layout.heightIn
import net.bible.android.TEST_SDK
import net.bible.sharedcore.history.HistoryEntry
import net.bible.sharedcore.history.HistoryError
import net.bible.sharedui.history.HistoryScreen
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class HistoryGoldenTest {

    private fun sampleEntries() = listOf(
        HistoryEntry(0, "Genesis 1:1", "9:15 am, Tue 8 Jul"),
        HistoryEntry(1, "John 3:16", "9:14 am, Tue 8 Jul"),
        HistoryEntry(2, "Psalms 23:1", "9:10 am, Tue 8 Jul"),
    )

    @Test
    fun history_primary() {
        captureMatrix("History", "primary") {
            HistoryScreen(
                title = "History",
                entries = sampleEntries(),
                error = null,
                onSelect = {},
                onDismissError = {},
            )
        }
    }

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "ar")
    fun history_primary_rtl() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        captureRtl("History", "primary") {
            HistoryScreen(
                title = context.getString(net.bible.android.activity.R.string.history_for, "Workspace", 1),
                entries = sampleEntries(),
                error = null,
                onSelect = {},
                onDismissError = {},
            )
        }
    }

    @Test
    fun history_empty() {
        captureGolden("History", "empty", EDGE_MODE) {
            HistoryScreen(
                title = "History",
                entries = emptyList(),
                error = null,
                onSelect = {},
                onDismissError = {},
            )
        }
    }

    @Test
    fun history_error() {
        captureGolden("History", "error", EDGE_MODE) {
            HistoryScreen(
                title = "History",
                entries = sampleEntries(),
                error = HistoryError.REVERT_FAILED,
                onSelect = {},
                onDismissError = {},
            )
        }
    }

    @Test fun history_sheetBody() = captureGolden("History", "sheetBody", GoldenMode.LIGHT) {
        androidx.compose.foundation.layout.Box(
            androidx.compose.ui.Modifier.heightIn(max = net.bible.sharedui.components.AbSheetContentMaxHeight)
        ) {
            net.bible.sharedui.history.HistoryListContent(entries = sampleEntries(), onSelect = {})
        }
    }
}

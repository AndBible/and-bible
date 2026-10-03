package net.bible.android.view.compose.golden

import net.bible.android.TEST_SDK
import net.bible.sharedcore.ai.RawLogEntryVd
import net.bible.sharedui.ai.RawLlmLogScreen
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class RawLlmLogGoldenTest {

    // DB mode: recordText populated, entries empty -- mirrors RawLlmLogController's KDoc contract.
    private val dbRecordText = """
        {
          "prompt": "Explain passage",
          "model": "claude-3-5-sonnet",
          "messages": [
            {"role": "user", "content": "Explain John 3:16 in plain language."},
            {"role": "assistant", "content": "John 3:16 states that God's love for the world..."}
          ]
        }
    """.trimIndent()

    // In-memory mode: entries populated, recordText null -- one Tool call + one API response entry.
    private val entries = listOf(
        RawLogEntryVd(title = "User", tokenInfo = "~42 tokens", body = "Explain John 3:16 in plain language."),
        RawLogEntryVd(
            title = "Tool call: getVerseContent", tokenInfo = "128 in / 64 out · $0.002",
            body = "{\n  \"tool\": \"getVerseContent\",\n  \"args\": {\"reference\": \"John 3:16\"}\n}",
        ),
        RawLogEntryVd(title = "API response (iteration 2)", tokenInfo = "~310 tokens", body = "John 3:16 states that God's love for the world..."),
    )

    private fun screen(
        title: String = "Explain passage",
        loading: Boolean = false,
        recordText: String? = null,
        entries: List<RawLogEntryVd> = emptyList(),
        expandedIndices: Set<Int> = emptySet(),
        canReportBug: Boolean = true,
    ) = @androidx.compose.runtime.Composable {
        RawLlmLogScreen(
            title = title,
            loading = loading,
            recordText = recordText,
            entries = entries,
            expandedIndices = expandedIndices,
            canReportBug = canReportBug,
            onToggleExpanded = {},
            onCopy = {},
            onShare = {},
            onDelete = {},
            onReportBug = {},
            onNavigateUp = {},
        )
    }

    // heightDp=700: monospace text block, several lines -- headroom so nothing clips.
    @Test fun db_text_mode_matrix() =
        captureMatrix("RawLlmLog", "dbtext", heightDp = 700, content = screen(recordText = dbRecordText))

    // heightDp=900: 3 expandable rows, the middle one expanded showing its monospace body.
    @Test fun entries_mode_matrix() =
        captureMatrix(
            "RawLlmLog", "entries", heightDp = 900,
            content = screen(entries = entries, expandedIndices = setOf(1)),
        )

    @Test fun loading() =
        captureGolden("RawLlmLog", "loading", EDGE_MODE, content = screen(loading = true, canReportBug = false))

    @Test fun empty() =
        captureGolden("RawLlmLog", "empty", EDGE_MODE, content = screen(canReportBug = false))
}

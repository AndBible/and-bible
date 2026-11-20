package net.bible.android.view.widget

import android.app.Application
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import net.bible.android.TEST_SDK
import net.bible.android.platform.AndroidWidgetStrings
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Goldens of the Listening list at 2x2, 4x3 and the empty 4x2, with a long-name row so the column geometry is visible. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = Application::class, qualifiers = "w500dp-h900dp")
class ListeningListGoldenTest {
    private val context get() = ApplicationProvider.getApplicationContext<Application>()
    private val strings get() = AndroidWidgetStrings(context)

    private val rows = listOf(
        row("Psalm 23:1", "WEB", 34, repeats = true, current = true),
        row("Pilgrim's Progress, Part 1", "PilgrimsProgress", 61),
        row("John 3:16", "KJV", 80),
        row("Genesis 1:1", "WEB", null),
        row("Romans 8:1", "FinRK", 12, repeats = true),
    )

    @Test fun listeningList_allModes() {
        for (mode in WidgetMode.entries) {
            val full = speakingState().copy(listening = rows)
            captureWidget(context, "listening_2x2", 162, 162, mode) { ListeningListContent(full, strings, 162.dp, 162.dp) }
            captureWidget(context, "listening_4x3", 334, 278, mode) { ListeningListContent(full, strings, 334.dp, 278.dp) }
            captureWidget(context, "listening_4x2_empty", 334, 182, mode) { ListeningListContent(stoppedState(), strings, 334.dp, 182.dp) }
        }
    }
}

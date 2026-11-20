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

/** Goldens of the Speak (2x1..5x1) and Speak with text (3x2, 4x2, 4x3) widgets, speaking and stopped, all four modes. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = Application::class, qualifiers = "w500dp-h900dp")
class SpeakWidgetsGoldenTest {
    private val context get() = ApplicationProvider.getApplicationContext<Application>()
    private val strings get() = AndroidWidgetStrings(context)

    @Test fun speakStrips_allModes() {
        for (mode in WidgetMode.entries) {
            for ((cells, w) in listOf(2 to 162, 3 to 248, 4 to 334, 5 to 420)) {
                captureWidget(context, "speak_${cells}x1_speaking", w, 86, mode) { SpeakStripContent(speakingState(), strings, w.dp) }
                captureWidget(context, "speak_${cells}x1_stopped", w, 86, mode) { SpeakStripContent(stoppedState(), strings, w.dp) }
            }
        }
    }

    @Test fun speakTextWidgets_allModes() {
        for (mode in WidgetMode.entries) {
            for ((name, size) in listOf("3x2" to (248 to 182), "4x2" to (334 to 182), "4x3" to (334 to 278))) {
                val (w, h) = size
                captureWidget(context, "speaktext_${name}_speaking", w, h, mode) { SpeakTextContent(speakingState(), strings, w.dp, h.dp) }
                captureWidget(context, "speaktext_${name}_stopped", w, h, mode) { SpeakTextContent(stoppedState(), strings, w.dp, h.dp) }
            }
        }
    }

    @Test fun attention_allModes() {
        for (mode in WidgetMode.entries) {
            captureWidget(context, "attention_4x1", 334, 86, mode) { AttentionGlanceContent() }
        }
    }
}

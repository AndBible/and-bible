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
            for ((cells, w) in listOf(1 to 56, 2 to 162, 3 to 210, 4 to 280, 5 to 350)) {
                val h = if (cells == 1) 56 else 86
                captureWidget(context, "speak_${cells}x1_speaking", w, h, mode) { SpeakStripContent(speakingState(), strings, w.dp) }
                captureWidget(context, "speak_${cells}x1_stopped", w, h, mode) { SpeakStripContent(stoppedState(), strings, w.dp) }
            }
        }
    }

    @Test fun speakTextWidgets_allModes() {
        for (mode in WidgetMode.entries) {
            for ((name, size) in listOf("2x2" to (130 to 130), "3x2" to (210 to 130), "4x2" to (280 to 130), "4x3" to (280 to 200))) {
                val (w, h) = size
                captureWidget(context, "speaktext_${name}_speaking", w, h, mode) { SpeakTextContent(speakingState(), strings, w.dp, h.dp) }
                captureWidget(context, "speaktext_${name}_stopped", w, h, mode) { SpeakTextContent(stoppedState(), strings, w.dp, h.dp) }
            }
        }
    }

    @Test fun minimumSizes_allModes() {
        for (mode in WidgetMode.entries) {
            captureWidget(context, "speak_minimum", 40, 40, mode) { SpeakStripContent(stoppedState(), strings, 40.dp) }
            val longVerse = speakingState().copy(verseText = "The LORD is my shepherd; I shall not want. ".repeat(15))
            captureWidget(context, "speaktext_minimum_long", 110, 110, mode) { SpeakTextContent(longVerse, strings, 110.dp, 110.dp) }
        }
    }

    @Test fun enlargedMinimum_allModes() {
        val originalScale = context.resources.configuration.fontScale
        try {
            org.robolectric.RuntimeEnvironment.setFontScale(1.3f)
            val state = speakingState().copy(verseText = "The LORD is my shepherd; I shall not want. ".repeat(15))
            for (mode in WidgetMode.entries) {
                captureWidget(context, "speaktext_minimum_font130", 110, 110, mode) {
                    SpeakTextContent(state, strings, 110.dp, 110.dp)
                }
            }
        } finally {
            org.robolectric.RuntimeEnvironment.setFontScale(originalScale)
        }
    }

    @Test fun attention_allModes() {
        for (mode in WidgetMode.entries) {
            captureWidget(context, "attention_4x1", 334, 86, mode) { AttentionGlanceContent() }
        }
    }
}

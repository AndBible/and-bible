package net.bible.android.view.widget

import android.app.Application
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import net.bible.android.TEST_SDK
import net.bible.android.activity.R
import net.bible.android.platform.AndroidWidgetStrings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** What each Speak and Speak-with-text size shows, read from the inflated widget's view tree. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = Application::class)
class SpeakGlanceWidgetTest {
    private val context get() = ApplicationProvider.getApplicationContext<Application>()
    private val strings get() = AndroidWidgetStrings(context)
    private fun s(id: Int) = context.getString(id)

    private fun strip(w: Int, state: net.bible.android.control.widget.SpeakWidgetState) =
        renderWidget(context, w, 86) { SpeakStripContent(state, strings, w.dp) }

    private fun text(w: Int, h: Int, state: net.bible.android.control.widget.SpeakWidgetState) =
        renderWidget(context, w, h) { SpeakTextContent(state, strings, w.dp, h.dp) }

    @Test fun twoByOneShowsPlayAndReference() {
        val v = strip(162, speakingState())
        assertNotNull(v.byDescription(s(R.string.pause)))
        assertTrue(v.texts().contains("Psalm 23:1"))
        assertTrue(v.texts().contains("34 % · WEB"))
        assertNull("2x1 has no rewind", v.byDescription(s(R.string.rewind)))
        assertNull("2x1 has no sleep toggle", v.byDescription(s(R.string.widget_toggle_sleep_timer)))
    }

    @Test fun threeByOneAddsPrevNextAndSleep() {
        val v = strip(248, speakingState())
        listOf(R.string.previous, R.string.next, R.string.widget_toggle_sleep_timer).forEach {
            assertNotNull("missing ${s(it)}", v.byDescription(s(it)))
        }
        assertNull(v.byDescription(s(R.string.rewind)))
        assertNull(v.byDescription(s(R.string.stop)))
    }

    @Test fun fourByOneShowsFiveTransportButtons() {
        val v = strip(334, speakingState())
        listOf(R.string.rewind, R.string.previous, R.string.stop, R.string.next, R.string.forward, R.string.widget_toggle_sleep_timer, R.string.pause)
            .forEach { assertNotNull("missing ${s(it)}", v.byDescription(s(it))) }
        assertTrue("4x1 shows the title", v.texts().contains("Psalm 23"))
    }

    @Test fun fiveByOneShowsStatusAndSleepChip() {
        val v = strip(420, speakingState())
        assertTrue(v.texts().contains("Psalm 23:1 · 34 % · WEB"))
        assertTrue(v.texts().any { it.contains(context.getString(R.string.speak_sleep_timer_minutes, 18)) })
    }

    @Test fun stoppedShowsPlayAndContinueFrom() {
        val v = strip(420, stoppedState())
        assertNotNull(v.byDescription(s(R.string.widget_play)))
        assertNull(v.byDescription(s(R.string.pause)))
        assertTrue(v.texts().contains("Continue from Psalm 23:1 · WEB"))
        assertFalse(v.texts().any { it.contains("stopped", ignoreCase = true) })
    }

    @Test fun textWidgetFourByThreeShowsNextVerseDimmed() {
        val v = text(334, 278, speakingState())
        assertTrue(v.texts().contains("He makes me lie down in green pastures."))
        assertTrue(v.texts().contains("Psalm 23:1 · 34 % · WEB"))
        listOf(R.string.widget_toggle_sleep_timer, R.string.rewind, R.string.previous, R.string.next, R.string.forward, R.string.stop)
            .forEach { assertNotNull("missing ${s(it)}", v.byDescription(s(it))) }
    }

    @Test fun textWidgetThreeByTwoShowsOnlyTheShortControls() {
        val v = text(248, 182, speakingState())
        assertTrue(v.texts().contains("The LORD is my shepherd; I shall not want."))
        assertNull(v.texts().firstOrNull { it.startsWith("He makes") })
        assertNotNull(v.byDescription(s(R.string.previous)))
        assertNull(v.byDescription(s(R.string.stop)))
    }

    @Test fun textWidgetStoppedExplainsWhereItContinues() {
        val v = text(334, 182, stoppedState())
        assertTrue(v.texts().contains("Continue from Psalm 23:1 · WEB"))
    }

    @Test fun damagedDataShowsAttentionOnly() {
        val v = renderWidget(context, 334, 86) { AttentionGlanceContent() }
        assertEquals(listOf(s(R.string.damaged_data_needs_attention)), v.texts())
        assertTrue("no transport buttons", v.descriptions().isEmpty())
    }
}

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

    @Test fun oneCellStripShowsOnlyPlay() {
        for (w in listOf(40, 80, 109)) {
            val v = strip(w, speakingState())
            assertNotNull("w=$w play/pause", v.byDescription(s(R.string.pause)))
            assertEquals("w=$w only the play button", 1, v.descriptions().size)
            assertTrue("w=$w no text", v.texts().isEmpty())
        }
        assertNotNull(strip(60, stoppedState()).byDescription(s(R.string.widget_play)))
    }

    @Test fun breakpointsAreTheLauncherMinimumsOfNCells() {
        assertEquals(listOf(110, 180, 250, 320), SPEAK_BREAKPOINT_DP)
        assertEquals(
            listOf(1, 2, 2, 3, 3, 4, 4, 5),
            listOf(109, 110, 179, 180, 249, 250, 319, 320).map { stripColumns(it.dp) },
        )
    }

    @Test fun threeByOneAddsPrevNextAndSleep() {
        val v = strip(210, speakingState())
        listOf(R.string.previous, R.string.next, R.string.widget_toggle_sleep_timer).forEach {
            assertNotNull("missing ${s(it)}", v.byDescription(s(it)))
        }
        assertNull(v.byDescription(s(R.string.rewind)))
        assertNull(v.byDescription(s(R.string.stop)))
    }

    @Test fun fourByOneShowsFiveTransportButtons() {
        val v = strip(280, speakingState())
        listOf(R.string.rewind, R.string.previous, R.string.stop, R.string.next, R.string.forward, R.string.widget_toggle_sleep_timer, R.string.pause)
            .forEach { assertNotNull("missing ${s(it)}", v.byDescription(s(it))) }
        assertTrue("4x1 shows the title", v.texts().contains("Psalm 23"))
    }

    @Test fun fiveByOneShowsStatusAndSleepChip() {
        val v = strip(350, speakingState())
        assertTrue(v.texts().contains("Psalm 23:1 · 34 % · WEB"))
        assertTrue(v.texts().any { it.contains(context.getString(R.string.speak_sleep_timer_minutes, 18)) })
    }

    @Test fun stoppedShowsPlayAndContinueFrom() {
        val v = strip(350, stoppedState())
        assertNotNull(v.byDescription(s(R.string.widget_play)))
        assertNull(v.byDescription(s(R.string.pause)))
        assertTrue(v.texts().contains("Continue from Psalm 23:1 · WEB"))
        assertFalse(v.texts().any { it.contains("stopped", ignoreCase = true) })
    }

    @Test fun textWidgetFourByThreeShowsNextVerseDimmed() {
        val v = text(280, 200, speakingState())
        assertTrue(v.texts().contains("He makes me lie down in green pastures."))
        assertTrue(v.texts().contains("Psalm 23:1 · 34 % · WEB"))
        listOf(R.string.widget_toggle_sleep_timer, R.string.rewind, R.string.previous, R.string.next, R.string.forward, R.string.stop)
            .forEach { assertNotNull("missing ${s(it)}", v.byDescription(s(it))) }
    }

    @Test fun textWidgetThreeByTwoShowsOnlyTheShortControls() {
        val v = text(210, 130, speakingState())
        assertTrue(v.texts().contains("The LORD is my shepherd; I shall not want."))
        assertNull(v.texts().firstOrNull { it.startsWith("He makes") })
        assertNotNull(v.byDescription(s(R.string.previous)))
        assertNull(v.byDescription(s(R.string.stop)))
    }

    @Test fun textWidgetTwoCellsWideShowsVerseAndPlayOnly() {
        for (w in listOf(80, 130, 179)) {
            val v = text(w, 182, speakingState())
            assertTrue("w=$w verse", v.texts().contains("The LORD is my shepherd; I shall not want."))
            assertNotNull("w=$w play", v.byDescription(s(R.string.pause)))
            assertEquals("w=$w only play", 1, v.descriptions().size)
        }
    }

    @Test fun responsiveMinimumUsesTheRealTwoCellGeometry() {
        val sizes = (SpeakTextGlanceWidget().sizeMode as androidx.glance.appwidget.SizeMode.Responsive).sizes
        val smallest = sizes.minBy { it.width.value * it.height.value }
        assertTrue("two-cell widgets must not render into an obsolete 80 dp bucket", smallest.width >= 110.dp)
    }

    @Test fun minimumTextWidgetKeepsCompleteLinesAtLargerFontSizes() {
        val longVerse = "The LORD is my shepherd; I shall not want. ".repeat(15)
        val originalScale = context.resources.configuration.fontScale
        try {
            for (scale in listOf(1f, 1.3f)) {
                org.robolectric.RuntimeEnvironment.setFontScale(scale)
                val v = text(110, 110, speakingState().copy(verseText = longVerse))
                v.allViews().filterIsInstance<android.widget.TextView>().single { it.text.toString() == longVerse }
                    .assertCompleteLines("Speak text responsive minimum, fontScale $scale")
            }
        } finally {
            org.robolectric.RuntimeEnvironment.setFontScale(originalScale)
        }
    }

    @Test fun smallestTextWidgetClipsLongVerseWithoutHidingPlay() {
        val longVerse = "The LORD is my shepherd; I shall not want. ".repeat(15)
        for (mode in WidgetMode.entries) {
            val state = speakingState().copy(verseText = longVerse)
            val v = renderWidget(context, 110, 110, mode) { SpeakTextContent(state, strings, 110.dp, 110.dp) }
            val play = v.byDescription(s(R.string.pause))!!
            val bounds = android.graphics.Rect()
            assertTrue("$mode play button is visible", play.getGlobalVisibleRect(bounds))
            assertEquals("$mode play button is not vertically clipped", play.height, bounds.height())
            val verse = v.allViews().filterIsInstance<android.widget.TextView>().single { it.text.toString() == longVerse }
            verse.assertCompleteLines("$mode minimum Speak text")
            assertEquals(android.text.TextUtils.TruncateAt.END, verse.ellipsize)
        }
    }

    @Test fun textWidgetThreeToFourCellsBreakAtTheLauncherMinimums() {
        assertNotNull(text(180, 110, speakingState()).byDescription(s(R.string.previous)))
        assertNull("249 dp is still 3 cells wide, no status line", text(249, 130, speakingState()).texts().firstOrNull { it == "Psalm 23:1 · 34 % · WEB" })
        assertTrue("250 dp is 4 cells wide", text(250, 130, speakingState()).texts().contains("Psalm 23:1 · 34 % · WEB"))
        assertNull("179 dp is 2 rows", text(250, 179, speakingState()).byDescription(s(R.string.stop)))
        assertNotNull("180 dp is 3 rows", text(250, 180, speakingState()).byDescription(s(R.string.stop)))
    }

    @Test fun textWidgetStoppedExplainsWhereItContinues() {
        val v = text(280, 130, stoppedState())
        assertTrue(v.texts().contains("Continue from Psalm 23:1 · WEB"))
    }

    @Test fun damagedDataShowsAttentionOnly() {
        val v = renderWidget(context, 334, 86) { AttentionGlanceContent() }
        assertEquals(listOf(s(R.string.damaged_data_needs_attention)), v.texts())
        assertTrue("no transport buttons", v.descriptions().isEmpty())
    }
}

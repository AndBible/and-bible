package net.bible.android.view.widget

import android.app.Application
import android.graphics.BitmapFactory
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.action.Action
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.GlanceTheme
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.test.core.app.ApplicationProvider
import android.content.ComponentName
import net.bible.android.TEST_SDK
import net.bible.android.activity.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Goldens of the shared widget building blocks (4x1-sized strip, 334x86 dp) in all four colour modes. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = Application::class, qualifiers = "w500dp-h900dp")
class WidgetComponentsGoldenTest {
    private val context get() = ApplicationProvider.getApplicationContext<Application>()
    private val noop: Action get() = actionStartActivity(ComponentName(context, "net.bible.android.view.widget.Noop"))

    private fun strip(mode: WidgetMode) = captureWidget(context, "components", 334, 86, mode) {
        Column(GlanceModifier.fillMaxSize().background(GlanceTheme.colors.surface).padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlayButton(playing = true, size = 52.dp, action = noop, contentDescription = "Pause")
                Spacer(GlanceModifier.width(6.dp))
                WidgetIconButton(R.drawable.ic_widget_prev, "Previous", 32.dp, noop)
                WidgetIconButton(R.drawable.ic_widget_rewind, "Rewind", 32.dp, noop)
                WidgetIconButton(R.drawable.ic_widget_forward, "Forward", 32.dp, noop)
                WidgetIconButton(R.drawable.ic_widget_next, "Next", 32.dp, noop)
                WidgetIconButton(R.drawable.ic_widget_moon, "Sleep", 32.dp, noop, active = true)
                Spacer(GlanceModifier.width(6.dp))
                SleepChip(18)
            }
            Spacer(GlanceModifier.height(8.dp))
            WidgetProgressBar(0.34f)
        }
    }

    @Test
    fun components_allModes() {
        WidgetMode.values().forEach { strip(it) }
        WidgetMode.values().forEach { assertTrue(File(widgetGoldenPath("components", it)).length() > 0) }
    }

    @Test
    fun monoHasNoHue() {
        listOf(WidgetMode.MONO, WidgetMode.MONO_DARK).forEach { mode ->
            strip(mode)
            val path = widgetGoldenPath("components", mode)
            val bmp = checkNotNull(BitmapFactory.decodeFile(path)) { "golden $path is missing or not a decodable PNG; record it first" }
            var worst = 0
            for (x in 0 until bmp.width) for (y in 0 until bmp.height) {
                val p = bmp.getPixel(x, y)
                val r = (p shr 16) and 0xff; val g = (p shr 8) and 0xff; val b = p and 0xff
                worst = maxOf(worst, maxOf(r, g, b) - minOf(r, g, b))
            }
            assertTrue("$mode has hue (channel spread $worst)", worst <= 8)
        }
    }

    @Test
    fun progressBitmap_fillsExactFractionWithSquareEnds() {
        val track = 0xFF112233.toInt(); val fill = 0xFFAABBCC.toInt()
        val half = progressBitmap(0.5f, track, fill)
        assertEquals(fill, half.getPixel(0, 0))
        assertEquals(fill, half.getPixel(half.width / 2 - 1, half.height - 1))
        assertEquals(track, half.getPixel(half.width / 2, 0))
        assertEquals(track, half.getPixel(half.width - 1, 0))
        assertEquals(track, progressBitmap(0f, track, fill).getPixel(0, 0))
        assertEquals(fill, progressBitmap(2f, track, fill).getPixel(half.width - 1, 0))
    }
}

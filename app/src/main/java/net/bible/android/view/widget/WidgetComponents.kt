package net.bible.android.view.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toArgb
import androidx.glance.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.Image
import androidx.glance.LocalContext
import androidx.glance.ImageProvider
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.ColorFilter
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Row
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.height
import androidx.glance.layout.fillMaxWidth
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import net.bible.android.activity.R

/** Filled, accent-coloured play/pause control: the only primary control of a widget. */
@Composable
fun PlayButton(playing: Boolean, size: Dp, action: Action, contentDescription: String) {
    Box(
        modifier = GlanceModifier.size(size).cornerRadius(14.dp)
            .background(GlanceTheme.colors.primary)
            .clickable(action)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(if (playing) R.drawable.ic_widget_pause else R.drawable.ic_widget_play),
            contentDescription = null,
            colorFilter = ColorFilter.tint(GlanceTheme.colors.onPrimary),
            modifier = GlanceModifier.size(size * 0.55f),
        )
    }
}

/** Plain secondary icon button; [active] draws it on a filled container (e.g. sleep timer on). */
@Composable
fun WidgetIconButton(icon: Int, contentDescription: String, size: Dp, action: Action, active: Boolean = false) {
    val base = GlanceModifier.size(size).cornerRadius(size / 2)
    Box(
        modifier = (if (active) base.background(GlanceTheme.colors.primaryContainer) else base)
            .clickable(action)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(icon),
            contentDescription = null,
            colorFilter = ColorFilter.tint(
                if (active) GlanceTheme.colors.onPrimaryContainer else GlanceTheme.colors.onSurface
            ),
            modifier = GlanceModifier.size(size * 0.6f),
        )
    }
}

/**
 * 4 dp progress bar, primary on surface variant; [fraction] is clamped to 0..1.
 *
 * Drawn as a stretched bitmap rather than a `ProgressBar` RemoteView: the geometry is then identical
 * on every launcher and in the Robolectric goldens (which do not paint framework progress bars).
 */
@Composable
fun WidgetProgressBar(fraction: Float, modifier: GlanceModifier = GlanceModifier, fillWidth: Boolean = true) {
    val context = LocalContext.current
    val track = GlanceTheme.colors.surfaceVariant.getColor(context).toArgb()
    val fill = GlanceTheme.colors.primary.getColor(context).toArgb()
    Image(
        provider = ImageProvider(progressBitmap(fraction.coerceIn(0f, 1f), track, fill)),
        contentDescription = null,
        contentScale = ContentScale.FillBounds,
        modifier = (if (fillWidth) modifier.fillMaxWidth() else modifier).height(4.dp),
    )
}

private const val BAR_W = 200
private const val BAR_H = 4

/**
 * Track with a fill of [fraction] of its width, at 1/200 resolution. The ends are square on purpose: the bitmap is
 * stretched to the bar's real width (a few dp up to a whole widget), which would squash rounded caps into ellipses.
 * Kept tiny (200x4) because a list widget carries one per row inside the RemoteViews size limit.
 */
internal fun progressBitmap(fraction: Float, trackColor: Int, fillColor: Int): Bitmap {
    val bmp = Bitmap.createBitmap(BAR_W, BAR_H, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val paint = Paint()
    paint.color = trackColor
    canvas.drawRect(0f, 0f, BAR_W.toFloat(), BAR_H.toFloat(), paint)
    if (fraction > 0f) {
        paint.color = fillColor
        canvas.drawRect(0f, 0f, BAR_W * fraction.coerceIn(0f, 1f), BAR_H.toFloat(), paint)
    }
    return bmp
}

/** Small chip with the remaining sleep-timer time, e.g. "18 min". */
@Composable
fun SleepChip(minutes: Int) {
    Row(
        modifier = GlanceModifier.cornerRadius(12.dp).background(GlanceTheme.colors.primaryContainer)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            provider = ImageProvider(R.drawable.ic_widget_moon),
            contentDescription = null,
            colorFilter = ColorFilter.tint(GlanceTheme.colors.onPrimaryContainer),
            modifier = GlanceModifier.size(14.dp),
        )
        Text(
            text = " " + LocalContext.current.getString(R.string.speak_sleep_timer_minutes, minutes),
            style = TextStyle(color = GlanceTheme.colors.onPrimaryContainer, fontSize = 12.sp, fontWeight = FontWeight.Medium),
            maxLines = 1,
        )
    }
}

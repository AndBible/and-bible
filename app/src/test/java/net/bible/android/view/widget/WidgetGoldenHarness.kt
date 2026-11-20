package net.bible.android.view.widget

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.color.ColorProviders
import org.robolectric.Robolectric
import com.github.takahirom.roborazzi.captureRoboImage
import android.widget.RemoteViews
import androidx.glance.appwidget.AppWidgetId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/** Colour variants a widget golden is captured in; [mono] ones are grey-only. */
enum class WidgetMode(val suffix: String, val colors: ColorProviders) {
    LIGHT("light", WidgetColors.light),
    DARK("dark", WidgetColors.dark),
    MONO("mono", WidgetColors.mono),
    MONO_DARK("mono_dark", WidgetColors.monoDark),
}

/** Where a widget golden is written, relative to the `:app` module directory. */
fun widgetGoldenPath(name: String, mode: WidgetMode) = "src/test/roborazzi/widgets/${name}_${mode.suffix}.png"

/**
 * Renders Glance [content] to `RemoteViews` through Glance's own composition path, applies it into a
 * [FrameLayout] of [widthDp] x [heightDp] and captures it with Roborazzi as
 * `widgets/<name>_<mode>.png`. Returns the inflated view so a test can assert on its layout.
 */
fun captureWidget(
    context: Context,
    name: String,
    widthDp: Int,
    heightDp: Int,
    mode: WidgetMode,
    content: @Composable () -> Unit,
): View {
    val widget = object : GlanceAppWidget() {
        override val sizeMode = SizeMode.Single
        override suspend fun provideGlance(context: Context, id: GlanceId) = provideContent {
            AbWidgetTheme(mode.colors) { content() }
        }
    }
    val options = Bundle().apply {
        putInt("appWidgetMinWidth", widthDp); putInt("appWidgetMaxWidth", widthDp)
        putInt("appWidgetMinHeight", heightDp); putInt("appWidgetMaxHeight", heightDp)
    }
    val remoteViews = renderToRemoteViews(widget, context, options, DpSize(widthDp.dp, heightDp.dp))
    val density = context.resources.displayMetrics.density
    val w = (widthDp * density).toInt()
    val h = (heightDp * density).toInt()
    val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    val host = FrameLayout(activity)
    val view = remoteViews.apply(activity, host)
    host.addView(view, ViewGroup.LayoutParams(w, h))
    activity.setContentView(host, ViewGroup.LayoutParams(w, h))
    host.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
    host.layout(0, 0, w, h)
    host.captureRoboImage(widgetGoldenPath(name, mode))
    return view
}

/**
 * Glance's `runComposition` is not visible to Kotlin callers (library-restricted), so it is called
 * reflectively; its signature is `(GlanceAppWidget, Context, GlanceId, Bundle, List<DpSize>, Any?)`.
 */
@Suppress("UNCHECKED_CAST")
private fun renderToRemoteViews(widget: GlanceAppWidget, context: Context, options: Bundle, size: DpSize): RemoteViews {
    val method = Class.forName("androidx.glance.appwidget.AppWidgetComposerKt").declaredMethods
        .single { it.name == "runComposition" && it.parameterTypes.size == 6 }
    val flow = method.invoke(null, widget, context, AppWidgetId(-1), options, listOf(size), null) as Flow<RemoteViews>
    return runBlocking { flow.first() }
}



/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */

package net.bible.sharedui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.ViewGroup
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowInsetsControllerCompat

/** Unwraps [ContextWrapper]s until an [Activity] is found; `null` when there is none (the
 *  Roborazzi golden harness and any `@Preview`). */
fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

/**
 * Makes the Android system bars match the Compose surface drawn under them (A/B batch 3, F1).
 *
 * - `statusBarColor` is set unconditionally. It is honoured below API 35 (where the window fits
 *   system windows, so Compose never sees a status-bar inset) and silently ignored from API 35 on,
 *   where the window is edge-to-edge. This is the same line classic runs at
 *   `MainBibleActivity.kt:2280`, which is explicitly skipped on the Compose path.
 * - [fillWindowBackground] paints the **content root** — the view `ActivityBase.setupUi` pads by
 *   the system-bar insets on API 35+ — so the strip the bars sit over shows [container] instead of
 *   `?android:windowBackground`. Callers that already paint behind the bars themselves (the reading
 *   toolbar) pass `false`. Below API 35 the content root is not the strip, so this is harmless.
 * - The status-bar icon appearance follows [container]'s luminance, same 0.45 threshold as
 *   `ReadingProgressPalette.textColorForBackground`. Classic only ever set light-icon mode for
 *   monochrome+day, which would leave white icons unreadable on a light workspace colour.
 *
 * Idempotent: re-applying the same colour writes the same values. Safe to call from a `SideEffect`
 * on every recomposition.
 */
fun applySystemBarColor(activity: Activity, container: Color, fillWindowBackground: Boolean) {
    val argb = container.toArgb()
    @Suppress("DEPRECATION")
    if (activity.window.statusBarColor != argb) activity.window.statusBarColor = argb

    if (fillWindowBackground) {
        val root = activity.findViewById<ViewGroup>(android.R.id.content)
        val current = root?.background
        if (root != null && !(current is ColorDrawable && current.color == argb)) {
            root.background = ColorDrawable(argb)
        }
    }

    WindowInsetsControllerCompat(activity.window, activity.window.decorView)
        .isAppearanceLightStatusBars = container.luminance() >= 0.45f
}

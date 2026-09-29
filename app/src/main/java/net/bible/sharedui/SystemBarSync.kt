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
import android.view.ViewGroup
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowInsetsControllerCompat
import net.bible.android.view.activity.nav.SystemBarPolicyHost

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
 * - [fillWindowBackground] governs the **content root**'s background. `true` paints it
 *   [container], so the strip the bars sit over shows that colour instead of
 *   `?android:windowBackground`. `false` **clears** it — sets the background to `null` — so a
 *   screen that paints behind the bars itself (the reading toolbar) does not inherit whatever the
 *   previous screen's `true` call left painted there (host-inset-ownership spec, section 3.5).
 *   `false` is no longer inert: before that fix it left the previous fill in place.
 * - The status-bar icon appearance follows [container]'s luminance, same 0.45 threshold as
 *   `ReadingProgressPalette.textColorForBackground`. Classic only ever set light-icon mode for
 *   monochrome+day, which would leave white icons unreadable on a light workspace colour. When
 *   [activity] is a `SystemBarPolicyHost` (the nav host) the write is delegated: the colour is only
 *   reported and the host's policy decides.
 * - **Floating windows are skipped for both the colour and the appearance write** (A/B batch 3
 *   review fix, Minor 7). A dialog-themed Activity (the since-deleted `HistoryComposeActivity`
 *   was this codebase's one example,
 *   `Theme.AppCompat...Dialog.Alert`) does not own the real status bar: `statusBarColor` is already
 *   ignored on one by the platform, but `isAppearanceLightStatusBars` is a
 *   `WindowInsetsController` property that DOES still apply while the floating window has focus —
 *   and nothing restores the underlying (reading-view) Activity's own appearance when the dialog is
 *   dismissed, so opening History over a dark reading view could leave the wrong icon contrast
 *   behind. [fillWindowBackground] is NOT skipped for a floating window: a floating host that
 *   passes `false` still has its own content-root background cleared, same as a non-floating one.
 *
 * Idempotent: re-applying the same colour writes the same values. Safe to call from a `SideEffect`
 * on every recomposition.
 */
fun applySystemBarColor(activity: Activity, container: Color, fillWindowBackground: Boolean) {
    val argb = container.toArgb()
    val floating = activity.window.isFloating

    if (!floating) {
        @Suppress("DEPRECATION")
        if (activity.window.statusBarColor != argb) activity.window.statusBarColor = argb
    }

    val root = activity.findViewById<ViewGroup>(android.R.id.content)
    if (fillWindowBackground) {
        val current = root?.background
        if (root != null && !(current is ColorDrawable && current.color == argb)) {
            root.background = ColorDrawable(argb)
        }
    } else {
        // Symmetry: `true` paints the root, so `false` must un-paint it. Without this a screen that
        // does not want the fill (the reading toolbar, which paints its own strip) inherits whatever
        // the previous screen left behind. Host-inset-ownership spec, section 3.5.
        if (root?.background != null) root.background = null
    }

    if (!floating) {
        val controller = WindowInsetsControllerCompat(activity.window, activity.window.decorView)
        // A/B batch 3 review fix (Minor 1): named for what the flag MEANS, not the bug it fixes — a
        // light container asks for a light STATUS BAR BACKGROUND, i.e. DARK icons drawn on top of it.
        // ("wantsLightIcons" was an inverted misnomer: it read as "light container -> light icons",
        // which is exactly the F1 bug this function fixes.)
        val policyHost = activity as? SystemBarPolicyHost
        if (policyHost != null) {
            // Fix batch 2 §2.4: the nav host decides the status icons (it knows fullscreen and what is
            // really under the bar); a top bar only reports its colour.
            policyHost.onTopBarColourReported(argb)
        } else {
            val statusBarBackgroundIsLight = container.luminance() >= 0.45f
            if (controller.isAppearanceLightStatusBars != statusBarBackgroundIsLight) {
                controller.isAppearanceLightStatusBars = statusBarBackgroundIsLight
            }
        }

        // Round 12b §3: the same luminance rule for the NAVIGATION bar's icons — but only when
        // `fillWindowBackground` is true. That flag already means "the container colour IS the
        // window background", which is exactly the condition under which the container is also what
        // sits behind the navigation bar. When it is false (the reading toolbar, which paints only
        // its own status-bar strip) the colour behind the navigation bar belongs to someone else —
        // `MainBibleActivity.showSystemUI()` owns it there — and using the toolbar's colour would be
        // the inverted-source mistake the comment above warns about, one bar down.
        if (fillWindowBackground) {
            val navBarBackgroundIsLight = container.luminance() >= 0.45f
            if (controller.isAppearanceLightNavigationBars != navBarBackgroundIsLight) {
                controller.isAppearanceLightNavigationBars = navBarBackgroundIsLight
            }
        }
    }
}

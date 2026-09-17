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
package net.bible.android.view.activity.base

import android.os.Build
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import net.bible.service.device.ScreenSettings

/**
 * Everything `ActivityBase.setupUi()` does EXCEPT its content-root inset padding.
 *
 * A Compose host calls this instead of taking `ActivityBase.setupUi()` (which it opts out of with
 * `disableBaseSetupUi = true`), because the padding is the one item it must not have: under this
 * app's Compose UI the scaffolds apply the system-bar insets themselves, and a padded content root
 * would add them a second time. See
 * `docs/superpowers/specs/2026-09-18-compose-host-inset-ownership-design.md` sections 3.1-3.2.
 *
 * The four items below are copied from `ActivityBase.kt:128-160` deliberately rather than shared
 * with it: `ActivityBase` keeps its own copy for the classic View screens, which still want the
 * padding. When the last classic screen goes, this file becomes the only copy.
 */
fun ComponentActivity.applyComposeHostWindowSetup() {
    enableEdgeToEdge()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
    } else {
        WindowCompat.setDecorFitsSystemWindows(window, true)
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        window.attributes.layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        if (!ScreenSettings.nightMode) {
            window.decorView.systemUiVisibility =
                window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
    }
}

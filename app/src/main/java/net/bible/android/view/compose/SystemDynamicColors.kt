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

package net.bible.android.view.compose

import android.content.Context
import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import net.bible.service.common.VideoLook
import net.bible.sharedui.theme.systemColorSchemeProvider

/**
 * Android's half of the opt-in "Use system colors (Material You)" setting: installs the
 * wallpaper-derived dynamic scheme as [systemColorSchemeProvider] on Android 12+ (API 31) and
 * leaves it null below, where the setting row is hidden as well.
 *
 * The provider reads the application context's current resources on every call, so after a
 * wallpaper change (the system recreates activities with the new palette) the next derivation
 * picks up the new colours.
 */
object SystemDynamicColors {
    val available: Boolean
        @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.S) get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    /**
     * Installs the provider. A video-look capture build ([VideoLook.enabled]) installs none, so the
     * "Use system colors" setting left on in a reused profile cannot change a capture's colours:
     * videos always show the built-in default theme.
     */
    fun install(context: Context, videoLook: Boolean = VideoLook.enabled) {
        val app = context.applicationContext
        systemColorSchemeProvider = if (available && !videoLook) { dark ->
            if (dark) dynamicDarkColorScheme(app) else dynamicLightColorScheme(app)
        } else null
    }
}

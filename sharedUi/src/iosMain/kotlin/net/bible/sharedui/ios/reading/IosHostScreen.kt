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


@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class, kotlinx.cinterop.ExperimentalForeignApi::class)

package net.bible.sharedui.ios.reading

import kotlinx.cinterop.useContents
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.UIKit.UIScreen

/** Opens [url] through UIKit (the default of [HostSpeakSettingsPlatform]); a malformed URL is ignored. */
internal fun iosOpenUrl(url: String) {
    val nsUrl = NSURL.URLWithString(url) ?: return
    UIApplication.sharedApplication.openURL(nsUrl, options = emptyMap<Any?, Any?>(), completionHandler = null)
}

/** UIKit's URL of the app's page in the Settings app (`UIApplicationOpenSettingsURLString`). */
internal val iosOpenSettingsUrl: String get() = UIApplicationOpenSettingsURLString

/** Whether the screen is taller than wide (the passage grid lays out differently in portrait). */
internal fun iosIsPortrait(): Boolean = UIScreen.mainScreen.bounds.useContents { size.height >= size.width }

/** The screen scale (px per dp = CSS px), Compose's density on iOS. */
internal fun iosScreenScale(): Float = UIScreen.mainScreen.scale.toFloat()

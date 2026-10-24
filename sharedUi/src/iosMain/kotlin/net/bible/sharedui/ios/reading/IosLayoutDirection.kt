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
package net.bible.sharedui.ios.reading

import platform.UIKit.UIApplication
import platform.UIKit.UIUserInterfaceLayoutDirection

/**
 * Whether the app's UI runs right to left: UIKit's layout direction for the application, which is the direction
 * Compose Multiplatform lays the app out in (it follows the app's language, not a separate setting). iOS counterpart of
 * Android's `CommonUtils.isRtl`; read per swipe, so a language change applies at once.
 */
internal fun iosIsRightToLeft(): Boolean =
    UIApplication.sharedApplication.userInterfaceLayoutDirection == UIUserInterfaceLayoutDirection.UIUserInterfaceLayoutDirectionRightToLeft

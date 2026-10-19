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
package net.bible.sharedui.ios.ports

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.UIKit.popoverPresentationController

/** The frontmost view controller of the app's key window, or null before a window exists. */
private fun topViewController(): UIViewController? {
    val app = UIApplication.sharedApplication
    val window: UIWindow? = app.keyWindow
        ?: app.connectedScenes.filterIsInstance<UIWindowScene>().firstNotNullOfOrNull { it.windows.firstOrNull() as? UIWindow }
    var top = window?.rootViewController ?: return null
    while (true) top = top.presentedViewController ?: return top
}

/**
 * Presents the system share sheet for [items] (strings or file [NSURL]s). On iPad the sheet needs an anchor; it points at the bottom centre of the screen.
 */
@OptIn(ExperimentalForeignApi::class)
internal fun iosPresentShare(items: List<Any>) {
    val top = topViewController() ?: return
    val sheet = UIActivityViewController(activityItems = items, applicationActivities = null)
    sheet.popoverPresentationController?.let { popover ->
        popover.sourceView = top.view
        val (w, h) = top.view.bounds.useContents { size.width to size.height }
        popover.sourceRect = CGRectMake(w / 2.0, h - 1.0, 1.0, 1.0)
    }
    top.presentViewController(sheet, animated = true, completion = null)
}

/** Shares the file at [path]; the file name is what the targets show, [title] is not used by the system sheet. */
@Suppress("UNUSED_PARAMETER")
internal fun iosShareFile(path: String, title: String) = iosPresentShare(listOf(NSURL.fileURLWithPath(path)))

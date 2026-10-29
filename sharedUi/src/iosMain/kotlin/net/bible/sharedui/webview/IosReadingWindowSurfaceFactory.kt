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

package net.bible.sharedui.webview

import net.bible.sharedcore.bibleview.JsHost
import net.bible.sharedui.app.ports.ExternalLinks
import net.bible.sharedui.app.ports.TextShare
import net.bible.sharedui.components.AppToasts
import net.bible.sharedui.ios.ports.IosExternalLinks
import net.bible.sharedui.ios.ports.IosTextShare
import net.bible.sharedui.ios.ports.iosCanPresentShare
import net.bible.sharedui.ios.ports.iosShareFile
import net.bible.sharedui.ios.reading.iosCopyToPasteboard
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.writeToFile

/** The iOS [HostReadingWindowSurface]: UIKit share sheet, general pasteboard, `NSFileManager` temp files and `NSTemporaryDirectory`. */
@kotlin.native.HiddenFromObjC
fun iosReadingWindowSurface(
    host: JsHost,
    links: ExternalLinks = IosExternalLinks(),
    textShare: TextShare = IosTextShare(),
    toast: (String) -> Unit = { AppToasts.show(it) },
    shareFile: (path: String) -> Unit = ::iosShareFile,
    canShare: () -> Boolean = ::iosCanPresentShare,
    tempDir: () -> String = { NSTemporaryDirectory() },
    copyToClipboard: (String) -> Unit = ::iosCopyToPasteboard,
): HostReadingWindowSurface = HostReadingWindowSurface(
    host, links, textShare, toast, shareFile, canShare, tempDir, copyToClipboard, ::iosWriteSharedHtml,
)

/** Replaces [dir] with a fresh empty directory and writes [html] (UTF-8, atomically) to [path]; false when the write failed. */
internal fun iosWriteSharedHtml(dir: String, path: String, html: String): Boolean {
    val fm = NSFileManager.defaultManager
    fm.removeItemAtPath(dir, null)
    fm.createDirectoryAtPath(dir, withIntermediateDirectories = true, attributes = null, error = null)
    return NSString.create(string = html).writeToFile(path, atomically = true, encoding = NSUTF8StringEncoding, error = null)
}

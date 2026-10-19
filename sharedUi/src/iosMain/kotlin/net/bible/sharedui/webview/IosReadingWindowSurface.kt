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
import net.bible.sharedcore.log.nsLog
import net.bible.sharedui.app.ports.ExternalLinks
import net.bible.sharedui.app.ports.TextShare
import net.bible.sharedui.components.AppToasts
import net.bible.sharedui.ios.ports.IosExternalLinks
import net.bible.sharedui.ios.ports.IosTextShare
import net.bible.sharedui.ios.ports.iosShareFile
import net.bible.sharedui.reading.bridge.ReadingWindowSurface
import net.bible.sharedui.reading.bridge.webSearchUrl
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.writeToFile
import platform.UIKit.UIPasteboard

/**
 * iOS per-window actions of a reading view (Android: `BibleViewSurface`): copy to the pasteboard, share text through
 * [textShare], share an HTML document as a temporary `.html` file through [shareFile], toasts through [toast]
 * ([AppToasts]) and web search as the search URL [webSearchUrl] opened through [links].
 */
@kotlin.native.HiddenFromObjC
class IosReadingWindowSurface(
    private val host: JsHost,
    private val links: ExternalLinks = IosExternalLinks(),
    private val textShare: TextShare = IosTextShare(),
    private val toast: (String) -> Unit = { AppToasts.show(it) },
    private val shareFile: (path: String) -> Unit = ::iosShareFile,
    /** The temporary directory shared HTML files go under; a parameter for tests. */
    private val tempDir: () -> String = { NSTemporaryDirectory() },
) : ReadingWindowSurface {
    override fun copyText(label: String?, text: String) {
        host.post { UIPasteboard.generalPasteboard.string = text }
    }

    override fun shareText(text: String, subject: String?) {
        textShare.shareText(text, subject ?: "")
    }

    /**
     * Writes [html] to `<temp>/shared-html/<title>.html` and hands it to [shareFile]. The file is named after the
     * sanitised [title] ("shared" when nothing is left) so the recipient sees a meaningful name. The share sheet has
     * no title or subject on iOS, so the title only names the file. Files of earlier shares are deleted first, so at
     * most one shared file lives in the temp directory.
     */
    override fun shareHtml(html: String, title: String) {
        val dir = tempDir().trimEnd('/') + "/shared-html"
        val fm = NSFileManager.defaultManager
        fm.removeItemAtPath(dir, null)
        fm.createDirectoryAtPath(dir, withIntermediateDirectories = true, attributes = null, error = null)
        val path = "$dir/${sharedFileName(title)}.html"
        val written = NSString.create(string = html).writeToFile(path, atomically = true, encoding = NSUTF8StringEncoding, error = null)
        if (!written) {
            nsLog("shareHtml: could not write $path")
            return
        }
        host.post { shareFile(path) }
    }

    override fun stopSelection(removeRanges: Boolean) {
        host.evaluate("window.getSelection().removeAllRanges()", null)
    }

    override fun toast(text: String) = toast.invoke(text)

    override fun openWebSearch(query: String) = links.open(webSearchUrl(query))
}

/** A file-name stem from [title]: path separators and control characters become spaces, trimmed, at most 60 characters. */
internal fun sharedFileName(title: String): String {
    val cleaned = title.map { if (it in "/\\:*?\"<>|" || it.code < 32) ' ' else it }.joinToString("")
        .trim().take(60).trim().trimStart('.')
    return cleaned.ifBlank { "shared" }
}

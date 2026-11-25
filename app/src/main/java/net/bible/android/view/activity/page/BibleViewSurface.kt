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


package net.bible.android.view.activity.page

import android.app.SearchManager
import android.content.ClipData
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.launch
import net.bible.android.activity.R
import net.bible.android.SharedConstants
import net.bible.android.control.backup.BackupControl
import net.bible.android.control.event.UserMessages
import net.bible.android.platform.AndroidBackupHost
import net.bible.service.common.CommonUtils
import net.bible.sharedui.reading.bridge.ReadingWindowSurface
import net.bible.sharedui.webview.sharedFileName
import okio.Path.Companion.toOkioPath
import java.io.File

/** Android [ReadingWindowSurface] over one [BibleView] (its WebView selection, host activity and clipboard). */
class BibleViewSurface(private val bibleView: BibleView) : ReadingWindowSurface {
    private val hostActivity get() = bibleView.hostCallbacks.hostActivity

    override fun copyText(label: String?, text: String) {
        CommonUtils.copyToClipboard(ClipData.newPlainText(label, text))
    }

    override fun shareText(text: String, subject: String?) {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            type = "text/plain"
        }
        hostActivity.startActivity(Intent.createChooser(sendIntent, hostActivity.getString(R.string.share)))
    }

    /** [title] names the exported file and is also the share subject. */
    override fun shareHtml(html: String, title: String) {
        val targetDir = File(SharedConstants.internalFilesDir, "export/")
        targetDir.mkdirs()
        // Recipients may open a shared URI asynchronously; retain recent exports for at least a day.
        val expiredBefore = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        targetDir.listFiles()?.filter { it.isFile && it.extension == "html" && it.lastModified() < expiredBefore }
            ?.forEach { it.delete() }
        val fileName = "${sharedFileName(title)}.html"
        val targetFile = File(targetDir, fileName)
        targetFile.writeText(html)

        val titleStr = hostActivity.getString(R.string.export_fileformat, "HTML")
        bibleView.scope.launch {
            org.koin.java.KoinJavaComponent.get<BackupControl>(BackupControl::class.java).saveOrShare(
                AndroidBackupHost { hostActivity },
                targetFile.toOkioPath(),
                fileName = fileName,
                shareMimeType = "text/html",
                saveMimeType = "text/html",
                chooserTitle = titleStr,
                message = titleStr,
                subject = title,
                promptTitle = hostActivity.getString(R.string.export_destination_title),
                promptMessage = hostActivity.getString(R.string.export_destination_message),
            )
        }
    }

    override fun stopSelection(removeRanges: Boolean) = bibleView.stopSelection(removeRanges)

    override fun toast(text: String) {
        UserMessages.toast(text)
    }

    override fun openWebSearch(query: String) {
        try {
            val intent = Intent(Intent.ACTION_WEB_SEARCH)
            intent.putExtra(SearchManager.QUERY, query)
            bibleView.host.hostContext.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Browser app not available to search: $query")
        }
    }

    private companion object {
        const val TAG = "BibleViewSurface"
    }
}

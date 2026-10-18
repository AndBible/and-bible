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
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.ios.ports

import androidx.compose.runtime.Composable
import net.bible.sharedui.app.download.DownloadHostPlatform
import net.bible.sharedui.app.download.DownloadOverflowItem
import net.bible.sharedui.ios.IosPending
import okio.Path
import org.crosswire.jsword.book.Book

/** Pending (Task 5 and stream H make parts real). */
@kotlin.native.HiddenFromObjC
class IosDownloadHostPlatform : DownloadHostPlatform {
    override fun copyFile(from: Path, to: Path): Boolean {
        IosPending.log("DownloadHostPlatform.copyFile")
        return false
    }

    override suspend fun showAbout(book: Book) {
        IosPending.log("DownloadHostPlatform.showAbout")
    }

    override suspend fun unlock(book: Book): Boolean {
        IosPending.log("DownloadHostPlatform.unlock")
        return false
    }

    override suspend fun confirmUpgradeWithBookmarks(): Boolean {
        IosPending.log("DownloadHostPlatform.confirmUpgradeWithBookmarks")
        return false
    }

    @Composable
    override fun OverflowIcon(item: DownloadOverflowItem) {
        IosPending.log("DownloadHostPlatform.OverflowIcon")
    }
}

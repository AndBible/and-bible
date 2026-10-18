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


package net.bible.sharedui.reading.bridge

import net.bible.android.control.page.CurrentPage
import net.bible.android.database.IdType
import net.bible.android.database.bookmarks.BookmarkEntities
import org.crosswire.jsword.book.Book

/**
 * Platform seam for everything the reading bridge and reading host launch that leaves the reading
 * view (other screens, external apps, result-bearing choosers). Android: `AndroidReadingNavigator`.
 */
interface ReadingNavigator {
    /** Add-on downloads (route `download(addons = true)`); refreshes suggested documents on finish. */
    fun openDownloads(addons: Boolean)

    /** Download screen pre-filled with a module search (the `download:` link scheme). */
    fun openDownloadSearch(initials: String?)

    /** Reading progress screen on [tab]. */
    fun openReadingProgress(tab: Int)

    fun openReadingProgressSettings()

    fun openWorkspaceSelector()

    fun openSearch(document: Book?)

    fun openPromptEditor(promptId: String)

    fun openExternalUrl(url: String)

    /**
     * An http(s) link tapped in the reading text. Defaults to [openExternalUrl]; Android overrides it to
     * keep asking first only in discrete mode, as the text's links always did.
     */
    fun openWebLink(url: String) = openExternalUrl(url)

    /** Start the key chooser that fits [page]'s document type. */
    fun startKeyChooser(page: CurrentPage)

    /**
     * Let the user choose labels for [bookmark] (preselected [labelIds]). Returns the chosen label
     * ids, or null when cancelled. Android also sets the primary label and updates the workspace's
     * recent/auto-assign label settings as part of the chooser.
     */
    suspend fun assignLabels(bookmark: BookmarkEntities.BaseBookmarkWithNotes, labelIds: List<IdType>): List<IdType>?
}

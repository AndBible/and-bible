@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.webview

import net.bible.android.control.page.CurrentPage
import net.bible.android.database.IdType
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.sharedcore.log.nsLog
import net.bible.sharedui.reading.bridge.LogOnce
import net.bible.sharedui.reading.bridge.ReadingNavigator
import org.crosswire.jsword.book.Book

/**
 * Placeholder [ReadingNavigator] for iOS until phase 2 reconciles it with the iOS navigation ports:
 * every method logs "pending: <name>" once and does nothing; [assignLabels] answers "cancelled".
 */
@kotlin.native.HiddenFromObjC
object PendingIosReadingNavigator : ReadingNavigator {
    private val pending = LogOnce { nsLog(it) }

    override fun openDownloads(addons: Boolean) { pending.pending("openDownloads") }
    override fun openDownloadSearch(initials: String?) { pending.pending("openDownloadSearch") }
    override fun openReadingProgress(tab: Int) { pending.pending("openReadingProgress") }
    override fun openReadingProgressSettings() { pending.pending("openReadingProgressSettings") }
    override fun openWorkspaceSelector() { pending.pending("openWorkspaceSelector") }
    override fun openChooseDocument(type: String?) { pending.pending("openChooseDocument") }
    override fun openSearch(document: Book?) { pending.pending("openSearch") }
    override fun openPromptEditor(promptId: String) { pending.pending("openPromptEditor") }
    override fun openExternalUrl(url: String) { pending.pending("openExternalUrl") }
    override fun openWebLink(url: String) { pending.pending("openWebLink") }
    override fun startKeyChooser(page: CurrentPage) { pending.pending("startKeyChooser") }

    override suspend fun assignLabels(
        bookmark: BookmarkEntities.BaseBookmarkWithNotes,
        labelIds: List<IdType>,
    ): List<IdType>? {
        pending.pending("assignLabels")
        return null
    }
}

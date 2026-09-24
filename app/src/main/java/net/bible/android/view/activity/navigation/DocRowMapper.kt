/*
 * Copyright (c) 2026 Martin Denham, Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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
package net.bible.android.view.activity.navigation

import net.bible.android.control.download.DocumentStatus.DocumentInstallStatus
import net.bible.android.control.download.DownloadControl
import net.bible.android.control.download.LanguageGrouping
import net.bible.android.control.document.canDelete
import net.bible.android.view.activity.download.installedDocument
import net.bible.service.download.DownloadManager
import net.bible.sharedcore.navigation.DocInstallStatus
import net.bible.sharedcore.navigation.DocRow
import net.bible.sharedcore.navigation.LangOption
import net.bible.sharedui.docCategoryOf
import org.crosswire.jsword.book.Book

/**
 * The one `Book` -> [DocRow] mapping, shared by the full ChooseDocument screen
 * ([ChooseDocumentComposeActivity]) and round 15b's document quick sheet
 * (`ComposeReadingViewHost.buildDocumentQuickTabsForHost`).
 *
 * Extracted from `ChooseDocumentComposeActivity` so the two cannot drift — a second copy would
 * silently diverge in language grouping, which is exactly what makes the deduped language dropdown
 * and the sheet's rows agree about a document's language.
 *
 * Construct ONE mapper per book list: [grouping] and [langByKey] are derived from the whole list, so
 * mapping books through mappers built from different lists would hand the same document different
 * [LangOption]s. Building it walks every book's language, so build it off the main thread (both call
 * sites do, inside `withContext(Dispatchers.Default)`).
 *
 * Deliberately NOT carrying `ChooseDocumentComposeActivity`'s FTS DAO seeding: that is
 * ChooseDocument-only (it feeds that screen's search field) and has no business running whenever a
 * quick sheet opens.
 */
internal class DocRowMapper(private val downloadControl: DownloadControl, books: List<Book>) {
    /** Reuse the classic language grouping so dedup + representative displayName match classic. */
    private val grouping = LanguageGrouping(books.mapNotNull { it.language })

    /**
     * ONE canonical [LangOption] per grouping key, from classic's representatives (most-canonical
     * member: 2-letter code, no script, no country). Every [DocRow] in a group shares this exact
     * option, so the deduped dropdown entry shows the same displayName/code classic's spinner would
     * — regardless of which row the controller keeps when deduping by groupingKey.
     */
    private val langByKey: Map<String, LangOption> = grouping.representatives.mapNotNull { lang ->
        val key = grouping.key(lang) ?: return@mapNotNull null
        key to LangOption(lang.code ?: "", lang.name, key)
    }.toMap()

    fun toDocRow(book: Book): DocRow = with(book) {
        val status = downloadControl.getDocumentStatus(this)
        val key = grouping.key(language) ?: (language.code ?: "")
        // ChooseDocument never loads the recommended/bad-document configs (only DownloadActivity does),
        // so classic isRecommended(null)/isBadDocument(null,…) are always false here.
        DocRow(
            docId = initials,
            osisId = osisID,
            abbreviation = abbreviation,
            name = name,
            language = langByKey[key]
                ?: LangOption(language.code ?: "", language.name, key),
            repository = getProperty(DownloadManager.REPOSITORY_KEY) ?: "",
            category = docCategoryOf(bookCategory),
            installStatus = status.documentInstallStatus.toDocInstallStatus(),
            percentDone = status.percentDone,
            recommended = false,
            badWarn = false,
            locked = isLocked,
            enciphered = isEnciphered,
            // From the INSTALLED copy, so both document screens derive this flag from the same
            // object handleDelete acts on (here they are the same Book, so the value is unchanged).
            canDelete = runCatching { installedDocument?.canDelete ?: false }.getOrDefault(false),
            installSizeMb = null, // ChooseDocument does not show install size (download-only)
        )
    }

    private fun DocumentInstallStatus.toDocInstallStatus(): DocInstallStatus = when (this) {
        DocumentInstallStatus.INSTALLED -> DocInstallStatus.INSTALLED
        DocumentInstallStatus.NOT_INSTALLED -> DocInstallStatus.NOT_INSTALLED
        DocumentInstallStatus.BEING_INSTALLED -> DocInstallStatus.BEING_INSTALLED
        DocumentInstallStatus.UPGRADE_AVAILABLE -> DocInstallStatus.UPGRADE_AVAILABLE
        DocumentInstallStatus.ERROR_DOWNLOADING -> DocInstallStatus.ERROR_DOWNLOADING
        DocumentInstallStatus.INSTALL_CANCELLED -> DocInstallStatus.INSTALL_CANCELLED
    }
}

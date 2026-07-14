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
package net.bible.android.view.activity.search

import android.os.Bundle
import androidx.activity.compose.setContent
import net.bible.android.activity.R
import net.bible.android.control.page.PageControl
import net.bible.android.control.search.SearchControl
import net.bible.android.view.Screen
import net.bible.android.view.ScreenLauncher
import net.bible.android.view.activity.base.ActivityBase
import net.bible.service.common.CommonUtils
import net.bible.service.device.ScreenSettings
import net.bible.service.sword.SwordDocumentFacade
import net.bible.sharedcore.search.SearchIndexService
import net.bible.sharedui.ProvideAppLocals
import net.bible.sharedui.search.SearchIndexScreen
import net.bible.sharedui.theme.AbTheme
import org.crosswire.jsword.book.Book
import org.koin.android.ext.android.inject

/**
 * Compose host for the search-index prompt — the new-path twin of classic [SearchIndex].
 * Resolves the document to index from the [SearchControl.SEARCH_DOCUMENT] extra (fallback:
 * current page document), then delegates the create/rebuild kickoff to [SearchIndexService]
 * and launches [Screen.SearchIndexProgress] to monitor progress.
 */
class SearchIndexComposeActivity : ActivityBase() {
    private val service: SearchIndexService by inject()
    private val pageControl: PageControl by inject()

    private val documentToIndex: Book?
        get() {
            val initials = intent.getStringExtra(SearchControl.SEARCH_DOCUMENT)
            return if (!initials.isNullOrEmpty()) SwordDocumentFacade.getDocumentByInitials(initials)
            else pageControl.currentPageManager.currentPage.currentDocument
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val doc = documentToIndex
        // Parity: nothing to index → finish immediately.
        if (doc == null) { finish(); return }

        val docId = doc.initials
        val documentName = doc.name
        val title = getString(R.string.search_index)
        val isRebuild = service.hasIndex(docId)

        setContent {
            ProvideAppLocals {
                AbTheme(
                    darkTheme = ScreenSettings.nightMode,
                    colorMode = CommonUtils.settings.displayColorMode,
                    disableAnimations = CommonUtils.settings.disableAnimations,
                ) {
                    SearchIndexScreen(
                        title = title,
                        documentName = documentName,
                        isRebuild = isRebuild,
                        onCreate = {
                            service.createIndex(docId)
                            // Monitor progress (mirrors classic SearchIndex.monitorProgress):
                            // forward all current extras, always specify the indexed document.
                            val progress = ScreenLauncher.intentFor(this, Screen.SearchIndexProgress).apply {
                                putExtras(intent)
                                putExtra(SearchControl.SEARCH_DOCUMENT, docId)
                            }
                            startActivity(progress)
                            finish()
                        },
                        onCancel = { finish() },
                        onNavigateUp = { finish() },
                    )
                }
            }
        }
    }
}

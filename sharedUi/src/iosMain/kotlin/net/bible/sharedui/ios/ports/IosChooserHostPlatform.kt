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
import kotlinx.coroutines.CoroutineScope
import net.bible.android.control.page.CurrentGeneralBookPage
import net.bible.android.control.page.CurrentMapPage
import net.bible.sharedcore.nav.PassageResult
import net.bible.sharedcore.navigation.BookPick
import net.bible.sharedcore.navigation.ChapterPick
import net.bible.sharedcore.navigation.DocRow
import net.bible.sharedcore.navigation.GridChoosePassageController
import net.bible.sharedcore.navigation.GridOptions
import net.bible.sharedcore.navigation.GridUi
import net.bible.sharedui.app.chooser.ChooserHostPlatform
import net.bible.sharedui.app.chooser.ChooserOverflowItem
import net.bible.sharedui.ios.IosPending
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.passage.Key

/** Pending (stream H): no keys, no rows, an inert grid controller. */
@kotlin.native.HiddenFromObjC
class IosChooserHostPlatform : ChooserHostPlatform {
    override fun backupModules() {
        IosPending.log("ChooserHostPlatform.backupModules")
    }

    override suspend fun generalBookKeys(page: CurrentGeneralBookPage): List<Key> {
        IosPending.log("ChooserHostPlatform.generalBookKeys")
        return emptyList()
    }

    override fun mapKeys(page: CurrentMapPage): List<Key> {
        IosPending.log("ChooserHostPlatform.mapKeys")
        return emptyList()
    }

    override fun gridChoosePassageController(
        scope: CoroutineScope,
        isScripture: Boolean,
        onResult: (PassageResult) -> Unit,
    ): GridChoosePassageController {
        IosPending.log("ChooserHostPlatform.gridChoosePassageController")
        return GridChoosePassageController(
            scope = scope,
            initialOptions = GridOptions(
                showScripture = isScripture, alphabetical = false, ltr = true,
                groupByCategory = false, longNames = false, showProgress = false,
            ),
            buildStep = { step, _ -> GridUi(step, "", 1, false, false, false, emptyList()) },
            onPersistOptions = { IosPending.log("ChooserHostPlatform.grid.onPersistOptions") },
            onPickBook = { IosPending.log("ChooserHostPlatform.grid.onPickBook"); BookPick.GoChapter },
            onPickChapter = { IosPending.log("ChooserHostPlatform.grid.onPickChapter"); ChapterPick.GoVerse },
            onPickVerse = { IosPending.log("ChooserHostPlatform.grid.onPickVerse"); "" },
            onFinish = { IosPending.log("ChooserHostPlatform.grid.onFinish") },
        )
    }

    override fun docRows(books: List<Book>): List<DocRow> {
        IosPending.log("ChooserHostPlatform.docRows")
        return emptyList()
    }

    override fun showError(e: Exception) {
        IosPending.log("ChooserHostPlatform.showError")
    }

    @Composable
    override fun OverflowIcon(item: ChooserOverflowItem) {
        IosPending.log("ChooserHostPlatform.OverflowIcon")
    }
}

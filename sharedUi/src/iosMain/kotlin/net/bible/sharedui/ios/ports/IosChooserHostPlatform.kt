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

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.bible.android.control.backup.BackupControl
import net.bible.android.control.backup.BackupHost
import net.bible.android.control.download.DownloadControl
import net.bible.android.control.navigation.NavigationControl
import net.bible.android.control.page.CurrentGeneralBookPage
import net.bible.android.control.page.CurrentMapPage
import net.bible.android.control.page.window.WindowControl
import net.bible.android.view.activity.navigation.DocRowMapper
import net.bible.android.view.activity.navigation.buildGridStep
import net.bible.android.view.activity.navigation.initialGridOptions
import net.bible.android.view.activity.navigation.persistGridOptions
import net.bible.android.view.activity.navigation.pickGridBook
import net.bible.android.view.activity.navigation.pickGridChapter
import net.bible.android.view.activity.navigation.genbookmap.keyChooserKeys
import net.bible.sharedcore.nav.PassageResult
import net.bible.sharedcore.navigation.DocRow
import net.bible.sharedcore.navigation.GridChoosePassageController
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.platform.UserNotifier
import net.bible.sharedui.app.chooser.ChooserHostPlatform
import net.bible.sharedui.assembly.HostWindowMetrics
import net.bible.sharedui.app.chooser.ChooserOverflowItem
import net.bible.sharedui.icons.AbIcons
import net.bible.sharedui.strings.keyed.StringTable
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.passage.Key
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.versification.BibleBook
import org.jetbrains.compose.resources.painterResource
import org.koin.core.Koin

/** Chooser adapters over the shared mapping, key and passage-grid contracts. */
@kotlin.native.HiddenFromObjC
class IosChooserHostPlatform(
    private val koin: Koin,
    private val hostScope: CoroutineScope,
    private val table: () -> StringTable,
    private val metrics: HostWindowMetrics,
) : ChooserHostPlatform {
    override fun backupModules() {
        hostScope.launch { koin.get<BackupControl>().backupModulesViaIntent(koin.get<BackupHost>()) }
    }

    override suspend fun generalBookKeys(page: CurrentGeneralBookPage): List<Key> =
        withContext(Dispatchers.IO) { page.keyChooserKeys() }

    override fun mapKeys(page: CurrentMapPage): List<Key> = page.keyChooserKeys()

    override fun gridChoosePassageController(
        scope: CoroutineScope,
        isScripture: Boolean,
        onResult: (PassageResult) -> Unit,
    ): GridChoosePassageController {
        val navigation = koin.get<NavigationControl>()
        val windows = koin.get<WindowControl>()
        val settings = koin.get<AppSettings>()
        val navigateToVerse = settings.getBoolean("navigate_to_verse_pref", false)
        var selectedBookNo = 0
        var selectedChapter = 1
        return GridChoosePassageController(
            scope = scope,
            initialOptions = initialGridOptions(navigation, isScripture, settings),
            buildStep = { step, options ->
                // Read the current table on each build, not when the platform/controller was created.
                buildGridStep(step, options, table().text("choosePassageBookName"), windows.windowRepository.name,
                    selectedBookNo, selectedChapter, navigation, windows,
                    metrics.isPortrait)
            },
            onPersistOptions = { persistGridOptions(it, navigation, settings) },
            onPickBook = { bookNo ->
                selectedBookNo = bookNo
                pickGridBook(bookNo, navigateToVerse, navigation) { selectedChapter = it }
            },
            onPickChapter = { chapter ->
                selectedChapter = chapter
                pickGridChapter(chapter, selectedBookNo, navigateToVerse, navigation, windows)
            },
            onPickVerse = { verse ->
                Verse(navigation.versification, BibleBook.values()[selectedBookNo], selectedChapter, verse).getOsisID()
            },
            onFinish = { onResult(PassageResult(verse = it)) },
        )
    }

    override fun docRows(books: List<Book>): List<DocRow> {
        val mapper = DocRowMapper(koin.get<DownloadControl>(), books)
        return books.map(mapper::toDocRow)
    }

    override fun showError(e: Exception) = koin.get<UserNotifier>().showError(table().text("error_occurred"), e)

    @Composable
    override fun OverflowIcon(item: ChooserOverflowItem) {
        val key = when (item) {
            ChooserOverflowItem.DOWNLOAD -> "ic_file_download_24dp"
            ChooserOverflowItem.BACKUP -> "ic_backup_black_24dp"
            ChooserOverflowItem.INSTALL_ZIP -> "ic_unarchive_white_24dp"
        }
        Icon(painterResource(AbIcons.all.getValue(key)), contentDescription = null)
    }
}

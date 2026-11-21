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

package net.bible.sharedui.host.ports

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
import net.bible.android.control.page.CurrentGeneralBookPage
import net.bible.android.control.page.CurrentMapPage
import net.bible.android.view.activity.navigation.DocRowMapper
import net.bible.android.view.activity.navigation.genbookmap.keyChooserKeys
import net.bible.sharedcore.navigation.DocRow
import net.bible.sharedcore.platform.UserNotifier
import net.bible.sharedui.app.chooser.ChooserHostPlatform
import net.bible.sharedui.app.chooser.ChooserOverflowItem
import net.bible.sharedui.icons.AbIcons
import net.bible.sharedui.strings.keyed.StringTable
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.passage.Key
import org.jetbrains.compose.resources.painterResource
import org.koin.core.Koin

/** Chooser adapters over the shared mapping and key contracts. */
@kotlin.native.HiddenFromObjC
class HostChooserHostPlatform(
    private val koin: Koin,
    private val hostScope: CoroutineScope,
    private val table: () -> StringTable,
) : ChooserHostPlatform {
    override fun backupModules() {
        hostScope.launch { koin.get<BackupControl>().backupModulesViaIntent(koin.get<BackupHost>()) }
    }

    override suspend fun generalBookKeys(page: CurrentGeneralBookPage): List<Key> =
        withContext(Dispatchers.IO) { page.keyChooserKeys() }

    override fun mapKeys(page: CurrentMapPage): List<Key> = page.keyChooserKeys()


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

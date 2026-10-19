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

import net.bible.sharedui.app.ports.PlatformText
import net.bible.sharedui.app.ports.PluralKey
import net.bible.sharedui.strings.keyed.StringTable
import platform.Foundation.NSByteCountFormatter
import platform.Foundation.NSByteCountFormatterCountStyleFile

/** Native file sizes and plural resources in the current UI language. */
@kotlin.native.HiddenFromObjC
class IosPlatformText(private val table: () -> StringTable) : PlatformText {
    override fun formatFileSize(bytes: Long): String =
        NSByteCountFormatter.stringFromByteCount(bytes, NSByteCountFormatterCountStyleFile)

    override fun plural(key: PluralKey, count: Int, vararg args: Any): String {
        val name = when (key) {
            PluralKey.CLOUD_DOC_SYNC_NOW_COUNT -> "cloud_doc_sync_now_count"
            PluralKey.CLOUD_DOC_SYNC_NOW_COUNT_SIZE -> "cloud_doc_sync_now_count_size"
            PluralKey.CLOUD_DOC_BULK_BLOCK_CONFIRM -> "cloud_doc_bulk_block_confirm"
            PluralKey.CLOUD_DOC_BULK_REMOVE_CONFIRM -> "cloud_doc_bulk_remove_all_confirm"
            PluralKey.CLOUD_DOC_BULK_REMOVE_CLOUD_CONFIRM -> "cloud_doc_bulk_remove_cloud_confirm"
            PluralKey.CLOUD_DOC_BULK_PURGE_CONFIRM -> "cloud_doc_bulk_purge_confirm"
        }
        return table().pluralFormat(name, count, *args)
    }
}

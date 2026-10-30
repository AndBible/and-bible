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
package net.bible.sharedui.desktop.assembly

import net.bible.sharedui.app.ports.PlatformText
import net.bible.sharedui.app.ports.PluralKey
import net.bible.sharedui.strings.keyed.StringTable
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** The desktop [PlatformText] (the `IosPlatformText` counterpart): decimal file sizes, plurals from the string table. */
class DesktopPlatformText(
    private val table: () -> StringTable,
    private val locale: () -> Locale = { Locale.getDefault() },
) : PlatformText {
    override fun formatFileSize(bytes: Long): String {
        // Decimal units, as NSByteCountFormatter's file style and Android's Formatter.formatFileSize do.
        val units = listOf("bytes", "KB", "MB", "GB", "TB")
        if (bytes < 1000) return "$bytes ${units[0]}"
        var value = bytes.toDouble()
        var unit = 0
        while (value >= 1000 && unit < units.lastIndex) { value /= 1000; unit++ }
        val format = DecimalFormat(if (value < 10) "0.##" else if (value < 100) "0.#" else "0", DecimalFormatSymbols.getInstance(locale()))
        return "${format.format(value)} ${units[unit]}"
    }

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

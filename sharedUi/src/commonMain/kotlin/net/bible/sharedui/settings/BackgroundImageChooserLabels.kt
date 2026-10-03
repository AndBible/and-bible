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

package net.bible.sharedui.settings

/** Host-resolved strings for [BackgroundImageChooserScreen] (translated text stays in strings.xml). */
data class BackgroundImageChooserLabels(
    val title: String, // R.string.background_image_title
    val none: String, // R.string.background_image_none
    val import: String, // R.string.background_image_import
    val empty: String, // R.string.background_image_empty
    val importing: String, // R.string.background_image_importing (new)
    val deleteTitle: String, // R.string.background_image_delete_title (new)
    val deleteConfirm: String, // R.string.background_image_delete_confirm (new)
    val delete: String, // R.string.delete
    val cancel: String, // R.string.cancel
) {
    companion object {
        fun forTest() = BackgroundImageChooserLabels(
            title = "Background image",
            none = "None",
            import = "Import",
            empty = "No background images",
            importing = "Importing…",
            deleteTitle = "Delete image?",
            deleteConfirm = "Delete this background image?",
            delete = "Delete",
            cancel = "Cancel",
        )
    }
}

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

package net.bible.service.addons.collections

import net.bible.android.database.IdType
import net.bible.android.database.bookmarks.withAddonIdTag
import okio.ByteString.Companion.encodeUtf8

/**
 * The id of an add-on collection row: a name-based id of `"$moduleInitials/$originalIdHex"` (SHA-256, first 16
 * bytes) with the add-on tag ([net.bible.android.database.bookmarks.ADDON_ID_TAG_HEX]) written over bytes 4..9.
 * Deterministic: the same module file gives the same ids on every device and across re-exports of the same source
 * label; two modules never share an id, and no derived id equals the author's own (untagged) source id.
 */
fun addonId(moduleInitials: String, original: IdType): IdType {
    val digest = "$moduleInitials/${original.toHex()}".encodeUtf8().sha256().toByteArray()
    return IdType.fromByteArray(withAddonIdTag(digest.copyOf(16)))!!
}

/*
 * Copyright (c) 2026 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.service.cloudsync.phos

import net.bible.android.database.IdType
import java.security.MessageDigest

/**
 * Ids as the Phos clients write them: UUIDs as 32 lowercase hex digits without dashes.
 *
 * Ids that are not UUIDs (older Phos bookmark ids such as "6A168692-F30E62E0") get a UUID derived
 * from the id, the same one every Phos client derives, so all of them name the item alike.
 */
object PhosIds {
    private val UUID_HEX = Regex("^[0-9a-fA-F]{32}$")

    /** md5(namespace + NUL + value) with the version 4 and variant bits set, as 32 hex digits. */
    fun derivedUuidHex(namespace: String, value: String): String {
        val d = MessageDigest.getInstance("MD5").digest((namespace + "\u0000" + value).toByteArray(Charsets.UTF_8))
        d[6] = ((d[6].toInt() and 0x0F) or 0x40).toByte()
        d[8] = ((d[8].toInt() and 0x3F) or 0x80).toByte()
        return d.toHex()
    }

    fun isUuidHex(s: String): Boolean = UUID_HEX.matches(s)

    fun hex(id: IdType): String = id.toHex()

    fun idOf(hex: String): IdType {
        val h = hex.lowercase()
        return IdType.fromString("${h.substring(0, 8)}-${h.substring(8, 12)}-${h.substring(12, 16)}-${h.substring(16, 20)}-${h.substring(20)}")
    }

    /** The local UUID for a bookmark id of the server (UUID itself, or derived from it). */
    fun bookmarkUuidHex(phosId: String): String =
        if (isUuidHex(phosId)) phosId.lowercase() else derivedUuidHex("phos-bookmark", phosId)

    fun labelUuidHex(name: String): String = derivedUuidHex("phos-label", name)

    fun windowUuidHex(workspaceHex: String, panelIndex: Int): String =
        derivedUuidHex("phos-ab-window", "$workspaceHex#$panelIndex")
}

/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with AndBible. If not, see <http://www.gnu.org/licenses/>.
 */

package net.bible.service.cloudsync.documents

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

const val DOCUMENT_META_FILENAME = "meta.json"

enum class DocumentType { SWORD, MYBIBLE, MYSWORD, ESWORD, EPUB }

@Serializable
data class DocumentSyncMeta(
    val initials: String,
    val name: String,
    val documentType: DocumentType,
    val version: String,
    val size: Long,
    val language: String,
    val category: String = "",
    val sourceDevice: String,
    val timestamp: Long,
    val cipherKey: String? = null,
    val deleted: Boolean = false,
) {
    fun toJson(): String = json.encodeToString(serializer(), this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        fun fromJson(text: String): DocumentSyncMeta = json.decodeFromString(serializer(), text)
    }
}

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

package net.bible.service.common

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import net.bible.sharedcore.navigation.RecentDocuments
import net.bible.sharedcore.platform.AppSettings

/**
 * Storage for round 15b's most-recently-used document list (spec §4.5).
 *
 * One JSON string key in the injected [AppSettings], which is a key/value Room store
 * (`AppSettings`) — so this adds no column and needs no migration. Ordering lives in the
 * pure [RecentDocuments]; this file is only the read/write half.
 */
class RecentDocumentsStore(private val settings: AppSettings) {
    private val json = Json { ignoreUnknownKeys = true }

    fun read(): List<String> {
        val raw = settings.getString(KEY, null) ?: return emptyList()
        // A malformed value must degrade to "no recents", never crash the reading view on launch.
        return runCatching { json.decodeFromString(ListSerializer(String.serializer()), raw) }
            .getOrDefault(emptyList())
    }

    fun record(initials: String?) {
        if (initials.isNullOrBlank()) return
        val next = RecentDocuments.touched(read(), initials)
        settings.setString(KEY, json.encodeToString(ListSerializer(String.serializer()), next))
    }

    companion object {
        const val KEY = "recent_documents"
    }
}

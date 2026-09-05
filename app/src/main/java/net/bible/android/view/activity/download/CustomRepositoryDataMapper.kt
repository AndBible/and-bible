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
package net.bible.android.view.activity.download

import kotlinx.serialization.Serializable
import net.bible.android.database.CustomRepository
import net.bible.service.common.CommonUtils
import net.bible.service.download.toData
import net.bible.service.download.toEntity
import net.bible.sharedcore.download.RepositoryResult

/**
 * The classic `"data"` Intent-JSON extra shape shared by [CustomRepositoryEditor],
 * [CustomRepositoryEditorComposeActivity] and [CustomRepositoriesComposeActivity]. Moved here
 * (out of classic [CustomRepositoryEditor], which used to host it) because this file's whole
 * purpose is already mapping between it and the portable [RepositoryResult] below.
 */
@Serializable
data class RepositoryData (
    var repository: CustomRepository? = null,
    var delete: Boolean = false,
    var cancel: Boolean = false,
) {
    fun toJSON(): String = CommonUtils.json.encodeToString(serializer(), this)

    companion object {
        fun fromJSON(str: String): RepositoryData = CommonUtils.json.decodeFromString(serializer(), str)
    }
}

/**
 * Maps between the classic `RepositoryData` Intent-JSON contract ([RepositoryData], declared
 * immediately above in this file and used verbatim by [CustomRepositoriesComposeActivity] for the
 * `"data"` extra it exchanges with [CustomRepositoryEditorComposeActivity]) and the portable
 * [RepositoryResult] the `:sharedCore` download controllers speak. Kept as a single pair of
 * functions so both Compose hosts (and `CustomRepositoryMapperTest`'s JSON-parity check) share one
 * mapping.
 */
fun RepositoryData.toRepositoryResult(): RepositoryResult = RepositoryResult(
    repository = repository?.toData(),
    delete = delete,
    cancel = cancel,
)

fun RepositoryResult.toRepositoryData(): RepositoryData = RepositoryData(
    repository = repository?.toEntity(),
    delete = delete,
    cancel = cancel,
)

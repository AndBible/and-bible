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
 * The classic `"data"` Intent-JSON extra shape, once shared by `CustomRepositoryEditor`,
 * `CustomRepositoryEditorComposeActivity` and `CustomRepositoriesComposeActivity`. Moved here (out
 * of classic `CustomRepositoryEditor`, which used to host it) because this file's whole purpose is
 * already mapping between it and the portable [RepositoryResult] below.
 *
 * **All three of those classes are gone** -- the classic editor with the classic
 * document-selection cluster, the two Compose hosts with nav-graph slice 4 Task 9 -- so the names
 * above are prose citations of what this shape used to serve, not KDoc links. Nothing in `:app`'s
 * main source set constructs a [RepositoryData] any more; see [toRepositoryResult] for why the type
 * is nevertheless kept.
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
 * immediately above in this file) and the portable [RepositoryResult] the `:sharedCore` download
 * controllers speak.
 *
 * **This file is now TEST-ONLY infrastructure, deliberately kept.** `RepositoryData` was the
 * `"data"` Intent extra `CustomRepositoriesComposeActivity` used to exchange with
 * `CustomRepositoryEditorComposeActivity`; nav-graph slice 4 deleted both -- the editor is an
 * in-graph destination now and its result travels as a typed [RepositoryResult] over a
 * `NavResultChannel`, with no Intent and no `fromJSON(...)!!` anywhere (plan D5). The last caller of
 * either function is `CustomRepositoryMapperTest`.
 *
 * **The queued collapse this is waiting for: unify the `RepositoryData`/`RepositoryResult` MIRROR
 * PAIR into the one `:sharedCore` type.** Design §6 puts that out of slice 4's scope, and plan D5
 * keeps this mapper and its test alive until it happens -- precisely because
 * `CustomRepositoryMapperTest`'s JSON-parity check (a [RepositoryResult] must serialise to
 * byte-identical JSON to the classic [RepositoryData] built from the same row) is what would catch
 * that collapse silently changing the on-disk/on-wire shape. Delete this file WITH the collapse,
 * not before it: removing it early would retire the only assertion that the two shapes still agree.
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

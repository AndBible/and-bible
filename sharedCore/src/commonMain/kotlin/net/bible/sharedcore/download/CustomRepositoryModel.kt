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

package net.bible.sharedcore.download

/**
 * iOS-clean mirror of the classic `net.bible.android.database.CustomRepository` entity. Plain data
 * class (no `@Serializable`) -- `:sharedCore` stays dependency-light; mapping to/from the classic
 * `RepositoryData` JSON Intent contract is an `:app`-layer concern (Task 3), not this one.
 */
data class CustomRepositoryData(
    val id: Long = 0,
    val name: String = "",
    val description: String = "",
    val type: String = "",
    val host: String = "",
    val catalogDirectory: String = "",
    val packageDirectory: String = "",
    val manifestUrl: String? = null,
)

/**
 * iOS-clean mirror of the classic `RepositoryData` editor round-trip payload (save/delete/cancel).
 * Plain data class -- see [CustomRepositoryData] for the no-`@Serializable` rationale.
 */
data class RepositoryResult(
    val repository: CustomRepositoryData? = null,
    val delete: Boolean = false,
    val cancel: Boolean = false,
)

/** Outcome of resolving a manifest URL (classic `CustomRepositoryEditor.validateManifestUrl`). */
sealed interface ManifestResult {
    data class Valid(val repository: CustomRepositoryData) : ManifestResult
    data object Invalid : ManifestResult
}

/**
 * Service seam the host implements (Room DAO + JSword `InstallManager` duplicate-name check +
 * network manifest fetch/parse live in the `:app`-layer implementation, Task 3). `:sharedCore`
 * only depends on this interface.
 */
interface CustomRepositoryService {
    suspend fun list(): List<CustomRepositoryData>

    /** Returns false if the upsert was rejected as a duplicate name (host does not throw). */
    suspend fun upsert(repo: CustomRepositoryData): Boolean
    suspend fun delete(repo: CustomRepositoryData)
    suspend fun validateManifest(url: String, existingId: Long): ManifestResult
}

/** One row of the custom-repositories list. */
data class RepoRow(val id: Long, val name: String, val description: String)

/** View-data for the custom-repositories list screen. */
data class CustomRepoListState(val rows: List<RepoRow> = emptyList())

/** Manifest-URL validation lifecycle for the editor screen. */
enum class Validation { Idle, Validating, Valid, Invalid }

/** View-data for the custom-repository editor screen. */
data class EditorState(
    val url: String = "",
    val validation: Validation = Validation.Idle,
    val resolved: CustomRepositoryData? = null,
    val packageDirectory: String = "",
    val isDirty: Boolean = false,
    val canSave: Boolean = false,
    val isExisting: Boolean = false,
)

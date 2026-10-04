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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Controller for the custom-repository editor screen (classic `CustomRepositoryEditor` activity).
 * Mirrors the manifest-URL-driven flow: typing a URL debounces (200ms, classic
 * `debounce(200, lifecycleScope){validateManifestUrl()}`) into [CustomRepositoryService.validateManifest],
 * which resolves either a `sword-https` manifest, or falls back to probing `manifest.json` /
 * `packages` / `mods.d.tar.gz` (host concern, behind the seam) -- represented here as just
 * [ManifestResult.Valid] / [ManifestResult.Invalid]. A successful validation seeds
 * [EditorState.packageDirectory] from the resolved repository; the user may override it via
 * [setPackageDir]. [EditorState.isDirty] mirrors classic's `initialDataJson` string-diff check, but
 * compares the would-be [RepositoryResult] by value equality against the [initial] one supplied at
 * construction, instead of a JSON round-trip.
 */
class CustomRepositoryEditorController(
    private val service: CustomRepositoryService,
    private val scope: CoroutineScope,
    private val initial: RepositoryResult,
) {
    /** Id of the repository being edited (0 for a brand-new repository), passed to every validate call. */
    private val existingId: Long = initial.repository?.id ?: 0L

    private val _state = MutableStateFlow(
        EditorState(isExisting = initial.repository?.let { it.id != 0L } ?: false)
    )
    val state: StateFlow<EditorState> = _state.asStateFlow()

    private var validationJob: Job? = null

    init {
        val manifestUrl = initial.repository?.manifestUrl
        if (manifestUrl != null && manifestUrl.isNotBlank()) {
            setUrl(manifestUrl)
        }
    }

    fun setUrl(url: String) {
        _state.value = _state.value.copy(url = url)
        validationJob?.cancel()
        validationJob = scope.launch {
            delay(200)
            runValidation(url)
        }
    }

    fun setPackageDir(dir: String) {
        _state.value = _state.value.copy(packageDirectory = dir)
        recomputeDirty()
    }

    fun buildSaveResult(): RepositoryResult =
        RepositoryResult(repository = currentRepository(), delete = false, cancel = false)

    fun buildDeleteResult(): RepositoryResult =
        RepositoryResult(repository = currentRepository(), delete = true, cancel = false)

    fun buildCancelResult(): RepositoryResult =
        RepositoryResult(repository = currentRepository(), delete = false, cancel = true)

    private suspend fun runValidation(url: String) {
        _state.value = _state.value.copy(validation = Validation.Validating)
        when (val result = service.validateManifest(url, existingId)) {
            is ManifestResult.Valid -> _state.value = _state.value.copy(
                validation = Validation.Valid,
                resolved = result.repository,
                packageDirectory = result.repository.packageDirectory,
                canSave = true,
            )
            ManifestResult.Invalid -> _state.value = _state.value.copy(
                validation = Validation.Invalid,
                resolved = null,
                canSave = false,
            )
        }
        recomputeDirty()
    }

    /** The resolved repository (once validated), or the original one being edited, with the
     *  currently-displayed [EditorState.packageDirectory] applied -- what a save/delete/cancel
     *  round-trip would carry back to the host. */
    private fun currentRepository(): CustomRepositoryData? {
        val base = _state.value.resolved ?: initial.repository ?: return null
        return base.copy(packageDirectory = _state.value.packageDirectory)
    }

    private fun recomputeDirty() {
        val current = RepositoryResult(repository = currentRepository(), delete = false, cancel = false)
        _state.value = _state.value.copy(isDirty = current != initial)
    }
}
